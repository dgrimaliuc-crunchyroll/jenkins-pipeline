@Library('ellation') _

import com.ellation.transifex.config.TransifexConfig
import com.ellation.transifex.steps.TransifexPipelineSteps
import com.ellation.transifex.TransifexConfigLoader
import org.jenkinsci.plugins.workflow.libs.Library

def configs
def gitHubRepoFullUrl
String downloadsFolder = 'translationsFromTransifex'

node("universal") {
    def transifexPipelineSteps = new TransifexPipelineSteps()

    stage("clean the workspace") {
        step([$class: 'WsCleanup'])
    }

    stage("get repo full URL") {
        gitHubRepoFullUrl = transifexPipelineSteps.getRepoFromTXPayload(params.GITHUB_PROJECT_NAME)
    }

    stage("scm") {
        transifexPipelineSteps.checkoutSCM(gitHubRepoFullUrl, params.SOURCE_BRANCH)
    }

    stage("load project\'s config") {
        def loader = new TransifexConfigLoader()
        configs = loader.loadConfigFile(this)
    }

    stage("store configs") {
        def config = new TransifexConfig(configs)
        transifexPipelineSteps.withConfig(config)
    }

    stage("set up transifex") {
        transifexPipelineSteps.initTransifexClient()
        transifexPipelineSteps.updateTransifexConfig { resource ->
            "./${downloadsFolder}/<lang>.lproj/${resource.fileName}.${resource.fileExt}"
        } { resource ->
            "${resource.repoFilePath}/en-US.lproj/${resource.fileName}.${resource.fileExt}"
        }
    }

    stage("download translated files") {
        transifexPipelineSteps.downloadTranslatedFilesFromTx()
    }

    stage("rename all localization folders to use dashes") {
        dir("${pwd()}/${downloadsFolder}"){
            List<String> foldersNames = sh(script: "ls", returnStdout: true).split("\n")
            foldersNames.each {
                sh "mv '$it' '${it.replace('_', '-')}'"
            }
        }
    }

    stage("upload to s3") {
        transifexPipelineSteps.uploadTranslationsToBucket(downloadsFolder)
    }
}
