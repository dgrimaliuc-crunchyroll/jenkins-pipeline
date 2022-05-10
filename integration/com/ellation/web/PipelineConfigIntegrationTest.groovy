package com.ellation.web

import com.ellation.BaseJenkinsTest
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Test
import org.jenkinsci.plugins.workflow.job.WorkflowJob

class PipelineConfigIntegrationTest extends BaseJenkinsTest {

    @Before
    void initJenkins() {
        loadSharedLibraries()
        setEnvironmentVariables()
    }

    @Test
    void integrationTest() {
        WorkflowJob p = createWorkflowJob("p", """
@Library('ellation@master') import com.ellation.web.Config

node {
    def conf = new Config(env)
    println conf.service
}
""")

        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0))
        r.assertLogContains('the_service', b)
    }

    void setEnvironmentVariables() {
        addEnvironmentVariable("ETP_SERVICE", "the_service")
        addEnvironmentVariable("ETP_ENVIRONMENT", "the_environment")
    }

}
