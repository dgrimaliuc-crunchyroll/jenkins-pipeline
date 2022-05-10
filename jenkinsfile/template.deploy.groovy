@Library('ellation') _

import com.ellation.configdelta.JsonSchemaUploader
import org.jenkinsci.plugins.workflow.libs.Library

def engAwsProfile = env.AWS_STAGING_PROFILE?.trim() ?: "ellationeng"
def account = env.ETP_PROD_ACCOUNT?.trim() ?: "978969509086"
def service = env.ETP_SERVICE
def subservices = env.ETP_SUBSERVICES
def notification = env.ETP_SLACK
def ellationFormationDir = env.ELLATION_FORMATION_DIR
def cfDeployPercent = env.CF_DEPLOY_PERCENT
def cfdSchemaStoreEnabled = env.ETP_CFD_SCHEMA_STORE_ENABLED

def services = [service]
for(subservice in subservices.split(',')) {
  if (subservice) {
    services << subservice
  }
}

node() {
    def serviceAmi = ellation_formation.getAmiId(service, 'staging')
    String commitHash = ellation_formation.history(service, 'staging').find(serviceAmi).commit_hash

    try {

        stage("Clean workspace") {
            cleanWs()
        }

        stage('promote') {
            for (deploy_service in services) {
                def ami = ellation_formation.getAmiId(deploy_service, 'staging')
                def ami_meta = ellation_formation.history(deploy_service, 'staging').find(ami)
                def flags = [stable: true, noprecheck: true, build: currentBuild.number]
                if (ami_meta) {
                    flags['commit_hash'] = ami_meta['commit_hash']
                }
                ellation_formation.promote(ami, account, engAwsProfile)
                ellation_formation.set(deploy_service, 'prod', ami, flags)
            }

            // the commit_hash metadata in ef-version will be of the format {repo}={hash} in case of multi-repository projects
            // skip if we find an '=' cause we do not support cfd integration for these projects
            if (cfdSchemaStoreEnabled) {
                if (commitHash.indexOf('=') == -1) {
                    JsonSchemaUploader uploader = new JsonSchemaUploader(this, service)
                    String source = uploader.getSchemaBucketPath('staging', commitHash)
                    uploader.uploadFromFile(source, 'prod', commitHash)
                    uploader.uploadFromFile(source, 'prod', 'latest')
                } else {
                    echo("multi-repository service detected, schema store integration not supported")
                }
            }
        }

        stage('deploy') {
            dir(ellationFormationDir) {
                // Mark deploy in NewRelic
                def version = commitHash ? commitHash : serviceAmi

                for (def deployService in services) {
                    newRelic.publishDeployToNewRelic("prod-${deployService}", version)
                }

                // Deploy service
                def deployPercent = cfDeployPercent ?: 10
                sh "ef-cf ./cloudformation/services/templates/${service}.json prod --commit --poll --percent ${deployPercent}"

            }
        }

    } catch (Exception error) {
        slackSend(channel: "#deploy-prod", message: "${service}-pipeline-prod failed")
        throw error
    }

    stage("Notification") {
        if (notification) {
            ArrayList<String> channelList = notification.split(',')
            for (channelName in channelList) {
                slackSend(channel: channelName.trim(), message: "${service} has finished on prod")
            }
        }
    }
}
