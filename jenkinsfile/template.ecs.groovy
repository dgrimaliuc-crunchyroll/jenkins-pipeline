@Library('ellation') _

import com.ellation.registry.ServiceRegistryEntry

String service = env.ETP_SERVICE
String environment = env.ETP_ENVIRONMENT
String region = "us-west-2"
String deployBranch = env.DEPLOYBRANCH
String awsAccountId = env.AWS_ACCOUNT_ID

node("docker") {
    cleanWs()
    // Fixes issue with ef-open tools for Jenkins Docker
    env.JENKINS_DOCKER = "TRUE"

    stage("Build") {
        gitWrapper.checkoutRepository(branch: env.BUILDBRANCH, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ETP_REPOSITORY)
        commitHash = sh(returnStdout: true, script: "git rev-parse HEAD")
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
        String script = "docker run --rm --volume=${releaseDirectory}:/release ${buildImage} ${makeTarget}"
        sh(returnStdout: true, script: script)

        // Build the app deployment image
        sh(returnStdout: true, script: "docker build -t ${service}:${commitHash} .")
        sh(returnStdout: true, script: "docker tag ${service}:${commitHash} ${service}:latest")
        imageId = sh(returnStdout: true, script: "docker images ${service}:${commitHash} -q | head -n1")
        imageId = imageId.trim()
        echo("Docker image: ${service}:${commitHash} ${imageId}")
    }

    stage("Push to ECR") {
        // Authenticate docker client to aws ecr
        sh(returnStdout: true, script: "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${awsAccountId}.dkr.ecr.${region}.amazonaws.com")

        // Tag the docker image so it can be pushed ot the AWS ecr
        sh(returnStdout: true, script: "docker tag ${imageId} ${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${service}")

        int result = sh(returnStatus: true, script: "aws ecr describe-repositories --region us-west-2 --repository-names ${service}")
        if (result != 0) {
            echo("AWS ECR repository doesn't exist, creating repository")
            sh(returnStdout: true, script: "aws ecr create-repository --region us-west-2 --repository-name ${service}")
        }

        sh(returnStdout: true, script: "docker push ${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${service}")
    }

    stage("Deploy") {
        efCf.deployService(service, environment, deployBranch)
    }

    cleanWs()
}
