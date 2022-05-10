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

    stage ("get repo full URL") {
        gitHubRepoFullUrl = transifexPipelineSteps.getRepoFromGitHubPayload(params.payload)
    }

    stage ("scm") {
        transifexPipelineSteps.checkoutSCM(gitHubRepoFullUrl, "master")
    }

    stage("load project\'s config") {
        def loader = new TransifexConfigLoader()
        configs = loader.loadConfigFile(this)
    }

    stage ("store configs") {
        def config = new TransifexConfig(configs)
        transifexPipelineSteps.withConfig(config)
    }

    stage("set up transifex") {
        transifexPipelineSteps.initTransifexClient()
        transifexPipelineSteps.updateTransifexConfig { resource, fullFileName ->
            "translations/<lang>.${resource.fileExt}"
        }
    }

    stage("upload translations") {
        transifexPipelineSteps.uploadTranslationsKeysToTx()
    }
}
