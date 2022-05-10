import com.ellation.deploy.DeployTicket
import com.ellation.web.Config

// TODO: to be worked on maybe
void runPostDeployTest(DeployTicket ticket, Config config, def params) {
    String testJob = "${config.service}-automated-test-prp"
    try {
        if (Jenkins.instance.getItemByFullName(testJob) != null) {
            build(job: testJob, parameters: params)
        } else {
            // If test does not exist, echo and let it pass
            echo("The ${testJob} job was not found")
        }
    } catch (Exception e) {
        throw e
    }
}
