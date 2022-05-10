package com.ellation.git

import com.ellation.BaseJenkinsTest
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Test

class GitCommitAuthorEmailProviderIntegrationTest extends BaseJenkinsTest {
    @Before
    void setUp() throws Exception {
        loadSharedLibraries()
    }

    @Test
    void identifiesCommitAuthorEmailFromNonMergeCommit() {
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.GitCommitAuthorEmailProvider

                node {
                    sh "git init"
                    sh "git config user.name test-name"
                    sh "git config user.email test.email@test.com"
                    sh "touch test.txt"
                    sh "git add test.txt"
                    sh "git commit --message=test"
                    sh "git log --stat"

                    def email = new GitCommitAuthorEmailProvider(this).email().trim()

                    echo "Email is \$email"
                }
                """)

        WorkflowRun build = r.assertBuildStatusSuccess(jobDefinition.scheduleBuild2(0))

        r.assertLogContains("Email is test.email@test.com", build)
    }

    @Test
    void identifiesCommitAuthorEmailFromMergeCommit() {
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.GitCommitAuthorEmailProvider

                node {
                    sh "git init"

                    // target branch = develop
                    sh "git config user.name test-name-develop"
                    sh "git config user.email test.email@develop.branch"
                    sh "git checkout -b develop"
                    sh "touch develop1.txt"
                    sh "git add develop1.txt"
                    sh "git commit --message=develop1message"

                    // source branch = CXAND-1234
                    sh "git config user.name test-name-CXAND-1234"
                    sh "git config user.email test.email@CXAND-1234.branch"
                    sh "git checkout -b CXAND-1234"
                    sh "touch CXAND-1234.txt"
                    sh "git add CXAND-1234.txt"
                    sh "git commit --message=CXAND-1234-message"

                    // target branch = develop
                    sh "git config user.name test-name-develop"
                    sh "git config user.email test.email@develop.branch"
                    sh "git checkout develop"
                    sh "touch develop2.txt"
                    sh "git add develop2.txt"
                    sh "git commit --message=develop2message"

                    // merge develop into CXAND-1234
                    // this imitates behaviour in Jenkins builds
                    // when target branch gets merged into source branch/PR.
                    // we want to take author of commit previous to merge commit from Jenkins
                    sh "git log"
                    sh "git checkout CXAND-1234"
                    sh "git merge develop"

                    def email = new GitCommitAuthorEmailProvider(this).email().trim()
                    echo "Email is \$email"
                }
                """)

        WorkflowRun build = r.assertBuildStatusSuccess(jobDefinition.scheduleBuild2(0))

        r.assertLogContains("Email is test.email@CXAND-1234.branch", build)
    }
}
