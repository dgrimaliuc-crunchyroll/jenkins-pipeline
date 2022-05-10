package com.ellation

import hudson.EnvVars
import hudson.model.Result
import hudson.slaves.EnvironmentVariablesNodeProperty
import jenkins.plugins.git.GitSCMSource
import jenkins.plugins.git.GitSampleRepoRule
import org.apache.commons.io.FileUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule

class BaseJenkinsTest {
    @Rule
    public JenkinsRule r = new JenkinsRule()
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule()

    protected WorkflowJob createWorkflowJob(String projectName, String jobContent) {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob, projectName)

        p.setDefinition(new CpsFlowDefinition(jobContent))
        p
    }

    protected void addEnvironmentVariable(String envKey, String envValue) {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty()
        EnvVars envVars = prop.getEnvVars()

        envVars.put(envKey, envValue)
        r.jenkins.getGlobalNodeProperties().add(prop)
    }

    protected void loadSharedLibraries() {
        initSharedLibraryRepo()
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("ellation", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.getRoot().getAbsolutePath(), "", "*", "", true)))))
    }

    /**
     * @see #buildAndFindExpectedLogMessage(String, String, String)
     */
    protected void buildAndFindExpectedLogMessage(String pipeline, String expectedLogMessage) {
        buildAndFindExpectedLogMessage("projectUnderTest", pipeline, expectedLogMessage)
    }

    /**
     * Builds the pipeline and verifies if log contains given message.
     * It's a common practice to print some message in pipeline code after code-under-test was executed.
     * This way we can make sure code was executed and does what we expect.
     *
     * @param projectName Pipeline project name under test
     * @param pipeline Pipeline code to execute
     * @param expectedLogMessage Expected log message. Use <code>echo "$MESSAGE"</code> to print message in pipeline.
     */
    protected void buildAndFindExpectedLogMessage(String projectName,
                                                  String pipeline,
                                                  String expectedLogMessage) {
        WorkflowRun build = r.assertBuildStatus(
                Result.SUCCESS,
                createWorkflowJob(projectName, pipeline).scheduleBuild2(0)
        )
        r.assertLogContains(expectedLogMessage, build)
    }

    private void initSharedLibraryRepo() {
        sampleRepo.init()
        FileUtils.copyDirectory(new File("src"), new File(sampleRepo.getRoot(), "src"))
        FileUtils.copyDirectory(new File("vars"), new File(sampleRepo.getRoot(), "vars"))
        sampleRepo.git("add", "src")
        sampleRepo.git("add", "vars")
        sampleRepo.git("commit", "--message=init")
    }
}
