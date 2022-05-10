import com.ellation.web.Config
import com.ellation.pipeline.EtpPipeline
import com.ellation.pipeline.ServicePipeline
import com.ellation.ServicePipeline as OldServicePipeline
import com.ellation.web.Config as PipelineConfig
import com.ellation.github.GithubClient
import com.ellation.github.RestClient

/**
 * This custom jenkins step acts as a Factory class for implementations needed in pipelines.
 */

/**
 * Old version that will be deprecated
 * @return ServicePipeline
 */
def pipeline() {
    return new OldServicePipeline()
}

/**
 * New version to use
 * @return ServicePipeline
 */
ServicePipeline servicePipeline(Script script, Config config) {
    EtpPipeline pipeline = new EtpPipeline(script, config)
    return (ServicePipeline) pipeline
}

/**
 * @return PipelineConfig
 */
def webConfig() {
    return new PipelineConfig(env, params)
}

/**
 * @return GithubClient
 */
def githubClient() {
    RestClient restClient

    withCredentials([usernamePassword(credentialsId: env.ETP_CREDENTIALS, passwordVariable: 'pass', usernameVariable: 'user')]) {
        restClient = new RestClient(baseUrl: 'https://api.github.com', authToken:pass)
    }

    return new GithubClient(restClient)
}

return this
