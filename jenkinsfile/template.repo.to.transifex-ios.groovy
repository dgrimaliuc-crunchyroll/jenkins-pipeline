@Library('ellation') _

import com.ellation.transifex.config.TransifexConfig
import com.ellation.transifex.steps.TransifexPipelineSteps
import com.ellation.transifex.TransifexConfigLoader
import org.jenkinsci.plugins.workflow.libs.Library

def configs
def gitHubRepoFullUrl

node("universal") {
    def transifexPipelineSteps = new TransifexPipelineSteps()

    stage("clean the workspace") {
        step([$class: 'WsCleanup'])
    }

    stage("get repo full URL") {
        gitHubRepoFullUrl = transifexPipelineSteps.getRepoFromGitHubPayload(params.payload)
    }

    stage("scm") {
        def pushedRef = transifexPipelineSteps.getPushedRefGitHubPayload(params.payload)
        if (pushedRef == "refs/heads/${env.TRANSLATIONS_BRANCH}") {
            transifexPipelineSteps.checkoutSCM(gitHubRepoFullUrl, env.TRANSLATIONS_BRANCH)
        } else {
            currentBuild.result = 'ABORTED'
            error("Aborting since the push was to other branch than ${env.TRANSLATIONS_BRANCH}.")
        }
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
            "${resource.repoFilePath}/<lang>.lproj/${resource.fileName}.${resource.fileExt}" 
        } { resource -> 
            "${resource.repoFilePath}/en-US.lproj/${resource.fileName}.${resource.fileExt}" 
        }
    }

    stage("upload translations") {
        transifexPipelineSteps.uploadTranslationsKeysToTx()
    }
}
