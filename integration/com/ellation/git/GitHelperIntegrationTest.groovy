package com.ellation.git

import com.ellation.BaseJenkinsTest
import org.junit.Test
import org.jenkinsci.plugins.workflow.job.WorkflowJob

class GitHelperIntegrationTest extends  BaseJenkinsTest {

    def pipelineCode = """
@Library('ellation@master') import com.ellation.git.GitHelper
def utils = new GitHelper()
"""

    @Test
    void itCanBeLoadedInAPipeline() {
        loadSharedLibraries()

        WorkflowJob p = createWorkflowJob("p", pipelineCode)

        r.assertBuildStatusSuccess(p.scheduleBuild2(0))
    }

}
