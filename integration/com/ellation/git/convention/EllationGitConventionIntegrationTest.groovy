package com.ellation.git.convention

import com.ellation.BaseJenkinsTest
import hudson.model.Result
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Test

class EllationGitConventionIntegrationTest extends BaseJenkinsTest {
    // Custom Exceptions' messages contain trailing whitespace after exception class in logs
    // This is required for better readability of aggregated exception thrown in Jenkins logs
    private static final String WHITESPACE = " "

    @Before
    void setUp() throws Exception {
        loadSharedLibraries()
    }

    /**
     * Checks the best case when all rules of Git convention pass for 1 commit.
     */
    @Test
    void verifiesCommitMessageConformsConvention() throws Exception {
        def ticketId = "CXAND-1234"
        def branchName = "CXAND-1234-test-branch"
        def authorEmail = "veaceslav.gaidarji@ellation.com"
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.convention.EllationGitConvention
                import com.ellation.git.GitInfo

                node {
                    sh "git init"
                    sh "git config user.name 'Veaceslav Gaidarji'"
                    sh "git config user.email $authorEmail"
                    sh "git checkout -b $branchName"
                    sh "touch test.txt"
                    sh "git add test.txt"

                    // COMMIT MESSAGE CONSTRUCTION
                    sh "echo 'Subject' >> commitMessage.txt"
                    sh "echo '' >> commitMessage.txt"
                    sh "echo 'Jira: $ticketId' >> commitMessage.txt"
                    sh "echo 'Reviewer:' >> commitMessage.txt"
                    sh "echo 'PR: GH-xx' >> commitMessage.txt"
                    sh "git commit --file=commitMessage.txt"

                    sh "git log --stat"

                    new EllationGitConvention().verify(GitInfo.from(this, "HEAD", "CXAND-1234"))
                }
                """)

        r.assertBuildStatusSuccess(jobDefinition.scheduleBuild2(0))
    }

    /**
     * Checks the worst case when all rules of Git convention fail for 1 commit.
     */
    @Test
    void verifiesCommitMessageDoesNotConformConvention() throws Exception {
        def wrongTicketId = "CXAND-1111"
        def branchName = "CXAND-1234-test-branch"
        def authorEmail = "test@wrong.domain.com"
        def subject73 = " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa."
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.convention.EllationGitConvention
                import com.ellation.git.GitInfo

                node {
                    sh "git init"
                    sh "git config user.name 'Veaceslav Gaidarji'"
                    sh "git config user.email $authorEmail"
                    sh "git checkout -b $branchName"
                    sh "touch test.txt"
                    sh "git add test.txt"

                    // COMMIT MESSAGE CONSTRUCTION
                    sh "echo '$subject73' >> commitMessage.txt"
                    sh "echo 'Jira: $wrongTicketId' >> commitMessage.txt"
                    sh "git commit --file=commitMessage.txt"

                    sh "git log --stat"

                    new EllationGitConvention().verify(GitInfo.from(this, "HEAD", "CXAND-1234"))
                }
                """)

        WorkflowRun build = r.assertBuildStatus(Result.FAILURE, jobDefinition.scheduleBuild2(0))
        r.assertLogContains(
                """com.ellation.git.convention.exception.GitConventionRuleFailedException:$WHITESPACE
                |Commit message:
                |' aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.
                |Jira: CXAND-1111'
                |
                |Git convention rules verification failed:
                |- Commit's author email must use one of the allowed domains [@ellation.com, @crunchyroll.com, @users.noreply.github.com]
                |- Commit message subject exceeded hard limit of 72 characters
                |- Commit message subject must start with capital letter
                |- Commit message subject must be separated with new line from commit body
                |- Commit message subject should not end with a period
                |- Commit message subject should not have preceding whitespaces
                |- Commit message must contain JIRA ticket ID which matches branch name prefix
                """.trim().stripMargin(),
                build
        )
    }

    /**
     * Verifies that range of commits conform git convention.
     * Range of commits calculated between feature branch and master branch.
     */
    @Test
    void verifiesCommitsRangeConformConvention() throws Exception {
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.convention.EllationGitConvention
                import com.ellation.git.convention.GitConventionVerifier

                node {
                    sh "git init"
                    sh "git config user.name 'Veaceslav Gaidarji'"
                    sh "git config user.email veaceslav.gaidarji@ellation.com"

                    // MASTER branch
                    sh "touch test1.txt"
                    sh "git add test1.txt"
                    sh "git commit --message=Subject1"
                    sh "touch test2.txt"
                    sh "git add test2.txt"
                    sh "git commit --message=Subject2"

                    // FEATURE branch
                    sh "git checkout -b CXAND-1234"

                    // OK COMMIT
                    sh "touch test3.txt"
                    sh "git add test3.txt"
                    sh "echo 'Subject3' >> commitMessage3.txt"
                    sh "echo '' >> commitMessage3.txt"
                    sh "echo 'Jira: CXAND-1234' >> commitMessage3.txt"
                    sh "git commit --file=commitMessage3.txt"

                    // OK COMMIT
                    sh "touch test4.txt"
                    sh "git add test4.txt"
                    sh "echo 'Subject4' >> commitMessage4.txt"
                    sh "echo '' >> commitMessage4.txt"
                    sh "echo 'Jira: CXAND-1234' >> commitMessage4.txt"
                    sh "git commit --file=commitMessage4.txt"

                    // print some info to logs to ease the debugging in case test fails
                    sh "git log --graph --oneline --decorate"
                    sh "git log --pretty=format:%H master..CXAND-1234"

                    def branchName = "CXAND-1234" // current branch name
                    def startCommit = "master" // target branch where current branch is merged (env.CHANGE_TARGET)
                    def endCommit = "HEAD"
                    new GitConventionVerifier(this, new EllationGitConvention())
                        .verifyCommitsRange(branchName, startCommit, endCommit)
                }
                """)

        r.assertBuildStatusSuccess(jobDefinition.scheduleBuild2(0))
    }

    /**
     * Verifies that range of commits does not conform git convention.
     * Range of commits calculated between feature branch and master branch.
     * Accumulated exception with rules failed per commit should be printed to log.
     */
    @Test
    void verifiesCommitsRangeDoesNotConformConvention() throws Exception {
        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _
                import com.ellation.git.convention.EllationGitConvention
                import com.ellation.git.convention.GitConventionVerifier

                node {
                    sh "git init"
                    sh "git config user.name 'Veaceslav Gaidarji'"
                    sh "git config user.email veaceslav.gaidarji@ellation.com"

                    // MASTER branch
                    sh "touch test1.txt"
                    sh "git add test1.txt"
                    sh "git commit --message=Subject1"
                    sh "touch test2.txt"
                    sh "git add test2.txt"
                    sh "git commit --message=Subject2"

                    // FEATURE branch
                    sh "git checkout -b CXAND-1234"

                    // WRONG COMMIT
                    sh "touch test3.txt"
                    sh "git add test3.txt"
                    sh "echo 'Subject3.' >> commitMessage3.txt"
                    sh "git commit --file=commitMessage3.txt"

                    // OK COMMIT
                    sh "touch test4.txt"
                    sh "git add test4.txt"
                    sh "echo 'Subject4' >> commitMessage4.txt"
                    sh "echo '' >> commitMessage4.txt"
                    sh "echo 'Jira: CXAND-1234' >> commitMessage4.txt"
                    sh "git commit --file=commitMessage4.txt"

                    // WRONG COMMIT
                    sh "touch test5.txt"
                    sh "git add test5.txt"
                    sh "echo 'subject5.' >> commitMessage5.txt"
                    sh "echo '' >> commitMessage5.txt"
                    sh "echo 'Jira: CXAND-1111' >> commitMessage5.txt"
                    sh "git commit --file=commitMessage5.txt"

                    // print some info to logs to ease the debugging in case test fails
                    sh "git log --graph --oneline --decorate"
                    sh "git log --pretty=format:%H master..CXAND-1234"

                    def branchName = "CXAND-1234" // current branch name
                    def startCommit = "master" // target branch where current branch is merged (env.CHANGE_TARGET)
                    def endCommit = "HEAD"
                    new GitConventionVerifier(this, new EllationGitConvention())
                        .verifyCommitsRange(branchName, startCommit, endCommit)
                }
                """)

        WorkflowRun build = r.assertBuildStatus(Result.FAILURE, jobDefinition.scheduleBuild2(0))
        r.assertLogContains(
                """com.ellation.git.convention.exception.MultipleGitConventionRulesFailedException:$WHITESPACE
                |
                |Commit message:
                |'subject5.
                |
                |Jira: CXAND-1111'
                |
                |Git convention rules verification failed:
                |- Commit message subject must start with capital letter
                |- Commit message subject should not end with a period
                |- Commit message must contain JIRA ticket ID which matches branch name prefix
                |
                |---------------
                |
                |Commit message:
                |'Subject3.'
                |
                |Git convention rules verification failed:
                |- Commit message subject should not end with a period
                |- Commit message must contain JIRA ticket ID which matches branch name prefix
                |
                |---------------
                """.trim().stripMargin(),
                build
        )
    }
}
