import groovy.transform.Field

import com.ellation.newrelic.NewRelic
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException

@Field NewRelic NR = null

/**
 * Post a version as deployed to NewRelic
 * The appName must be of the form {ENV}-{serviceName}
 * @param appName must be of the form {ENV}-{serviceName}
 * @param version can be anything alphanumerical
 */
public publishDeployToNewRelic(appName, version) {
  if (NR == null) {
    println "Initializing NewRelic client"
    try{
      withCredentials([string(credentialsId: 'NEWRELIC_API_KEY', variable: 'NEWRELIC_API_KEY')]) {
        NR = new NewRelic(apiKey: NEWRELIC_API_KEY)
      }
    } catch (CredentialNotFoundException e) {
      println "Could not find credentials for NewRelic"
      println "Error: $e"
      return
    }
  }

  println "Posting NewRelic deploy data about ${appName}"
  try {
    println "Publishing deploy for ${appName} with version ${version}"
    NR.publishVersion(appName, version)
  } catch (Exception e) {
    // do not fail the deploy on NR failure
    println "Failed to publish deploy for ${appName}; Error $e"
  }
}


public publishServiceDeployToNewRelic(pipeline, version) {
  def environment = pipeline.environment
  for (service in pipeline.getServiceList()) {
    this.publishDeployToNewRelic("${environment}-${service}", version)
  }
}

public void publishServiceDeployToNewRelicV2(String environment, String serviceName, String gitCommit) {
    publishDeployToNewRelic("${environment}-${serviceName}", gitCommit)
}

return this
