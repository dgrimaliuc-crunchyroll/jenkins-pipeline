@Library('ellation') _

@Grab('org.yaml:snakeyaml:1.24')
import org.yaml.snakeyaml.Yaml

import com.ellation.registry.ServiceRegistryEntry
import com.ellation.registry.ServiceRegistryEntryJson
import com.ellation.registry.ServiceRegistryEntryYaml

String service = env.ETP_SERVICE
String environment = env.ETP_ENVIRONMENT
String region = "us-west-2"

node("docker") {

    stage("Clean workspace") {
        cleanWs()
    }

    stage("Build") {
        gitWrapper.checkoutRepository(branch: env.BUILDBRANCH, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ETP_REPOSITORY)
        commitHash = sh(returnStdout: true, script:"git rev-parse HEAD")
        commitHash = commitHash.trim()

        // Create Build Container
        buildImage = "build-${service}"
        withCredentials([string(credentialsId: env.ETP_SSH_SECRET_TEXT, variable: 'sshKey')]) {
        sh(returnStdout: true, script: "docker build --rm --build-arg SSH_KEY=\"${sshKey}\" --tag=${buildImage} " +
                "--quiet --file=./jenkins.Dockerfile .")
        }

        // Compile the binary, copy it + config + build info to /release
        sh(returnStdout: true, script: "mkdir -p release")
        String releaseDirectory = pwd() + "/release"
        String makeTarget = "package-${service}"
        String script = "docker run --rm --volume=${releaseDirectory}:/release build-${service} ${makeTarget}"
        sh(returnStdout: true, script: script)

        // Build the app deployment image
        sh(returnStdout: true, script: "docker build -t ${service}:${commitHash} .")
        sh(returnStdout: true, script: "docker tag ${service}:${commitHash} ${service}:latest")
        imageId = sh(returnStdout: true, script:"docker images ${service}:${commitHash} -q | head -n1")
        echo imageId
    }

    stage("Push to ECR") {
        withAWS {
            def identity = awsIdentity()
            mgmtAccountNumber = identity.account
        }

        docker.withRegistry("https://${mgmtAccountNumber}.dkr.ecr.us-west-2.amazonaws.com/${service}", "ecr:us-west-2:el_mgmt_keys") {
            docker.image(service).push(commitHash)
            imageName = docker.image("${service}").imageName()
        }
    }

    stage("Update ECS") {
        gitWrapper.checkoutRepository(branch: "master", credentialsId: "jenkins-github-ssh",
                url: "git@github.com:crunchyroll/ellation-infrastructure-live.git")
        ServiceRegistryEntry entry
        entry = serviceRegistry.parseServiceRegistryYaml("./service_registry.yaml", service)
        serviceTeam = entry.getTeam()

        accountRootDir = "${serviceTeam}/el-${serviceTeam}-${environment}"
        accountNumber = sh(returnStdout: true, script: "cat ${accountRootDir}/terragrunt.hcl | grep aws_account_id | cut -d '=' -f 2 | tr -d '\"'")
        accountNumber = accountNumber.trim()

        // Execute Terrarunt under an assumed role.
        withAWS(role:'global-role-deploy', roleAccount:accountNumber, roleSessionName: 'jenkins-session') {
            sshagent (credentials: [env.ETP_SSH_KEY_CREDENTIALS_ID]) {
                dir("${accountRootDir}/${region}/${environment}/${service}") {
                    sh "terragrunt apply -var \"image_name=${imageName}\" -var \"image_tag=${commitHash}\" -auto-approve"
                }
            }
        }
    }
}
