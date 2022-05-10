@Library('ellation') _

def config    = etp.webConfig()
def pipeline  = etp.pipeline()

pipeline.withCredentials(config.credentials)

pipeline (config) {

  deploy(config.waitTime as int)

  if (config.testTarget != 'none') {
    test(config.testTarget)
  }
}

if (config.notifications) {
  //can not use a simpler form because of JENKINS-26481
  def parts = config.notifications.split(",")

  for (i = 0; i < parts.length; i++) {
    slackSend channel: parts[i], message: "${config.service} has finished on ${config.environment}"
  }
}
