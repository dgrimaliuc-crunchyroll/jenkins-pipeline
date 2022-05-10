package com.ellation.qe.config

import com.ellation.github.GithubClient

class QeConfig implements Serializable {
    // Jenkins
    public String jobName
    public String jobNumber
    public String jobUrl
    public String slave
    public Integer timeout
    public String awsRole
    public String branch
    public String repository
    public Boolean clearUntrackedFiles
    public String gitCredentialId
    public String awsCredentialId
    public String browserStackCredentialId
    public String lambdaTestCredentialId
    public Boolean archivePerformanceResults

    // Github
    public String githubCredentialId
    public GithubClient github

    // Service
    public String targetServiceSlug
    public String targetServiceBase
    public String targetServiceHead

    // TestRail
    public Boolean disableTestRailPublish
    public Boolean testRailNoFail
    public String testRailCredentialId
    public String testRailHost
    public String testRailUsername
    public String testRailPassword
    public String testRailNewRunName
    public String testRailProject
    public String testRailSuite
    public String testRailPlan
    public String testRailRun

    // JIRA
    public String jiraCredentialId
    public String jiraHost
    public String jiraUsername
    public String jiraPassword

    // General
    public String module
    public String profile
    public String environment
    public String tags
    public Integer retryCount
    public Integer parallelCount
    public String[] healthCheckServices
    public String country
    public String cloud
    public String os
    public String osVersion
    public String browser
    public String browserVersion
    public String device
    public String resolution
    public String additionalArgs
    public String runOnlyModifiedTestsFrom
    public String app
    public String app_type
    public String app_version
    public String app_build

    //PlayStation
    public String mainApp
    public String lastPatch

    QeConfig(env, params = [:]) {
        // Jenkins
        jobName = env.JOB_NAME
        jobNumber = env.BUILD_NUMBER
        jobUrl = env.BUILD_URL
        slave = env.SLAVE
        timeout = env.TIMEOUT?.isInteger() ? env.TIMEOUT?.toInteger() : null
        awsRole = env.AWS_ROLE ?: ''
        branch = env.BRANCH ?: 'master'
        repository = env.REPOSITORY?.trim() ?: ''
        clearUntrackedFiles = (env.CLEAR_BEFORE_CHECKOUT ?: "true").toBoolean()
        gitCredentialId = env.GIT_CREDENTIALS ?: 'e07c6884-0ed5-4b8c-a2c0-47e10d440545'
        awsCredentialId = env.AWS_CREDENTIAL_ID
        browserStackCredentialId = env.BROWSERSTACK_CREDENTIAL_ID ?: 'fba12c64-e12b-4b8a-b6aa-2da0a46a0a60'
        lambdaTestCredentialId = env.LAMBDATEST_CREDENTIAL_ID
        archivePerformanceResults = (env.ARCHIVE_PERFORMANCE_RESULTS ?: "false").toBoolean()

        // GitHub
        githubCredentialId = env.GITHUB_CREDENTIAL_ID

        // Service
        targetServiceSlug = env.TARGET_SERVICE_SLUG
        targetServiceBase = env.TARGET_SERVICE_BASE
        targetServiceHead = env.TARGET_SERVICE_HEAD

        // TestRail
        disableTestRailPublish = (env.DISABLE_TESTRAIL_PUBLISH ?: "false").toBoolean()
        testRailNoFail = (env.TESTRAIL_NO_FAIL ?: "false").toBoolean()
        testRailCredentialId = env.TESTRAIL_CREDENTIAL_ID ?: 'testrail'
        testRailHost = env.TESTRAIL_HOST ?: "https://ellation.testrail.net"
        testRailNewRunName = env.TESTRAIL_RUN_NAME ?: env.TESTRAIL_NEW_RUN_NAME
        testRailProject = env.TESTRAIL_PROJECT
        testRailSuite = env.TESTRAIL_SUITE
        testRailPlan = env.TESTRAIL_PLAN
        testRailRun = env.TESTRAIL_RUN

        // JIRA
        jiraCredentialId = env.JIRA_CREDENTIAL_ID ?: 'jira-qe'
        jiraHost = env.JIRA_HOST ?: "https://jira.tenkasu.net"

        // General
        runOnlyModifiedTestsFrom = env.RUN_ONLY_CHANGED_TESTS_FROM
        additionalArgs = env.ADDITIONAL_ARGS ?: ""
        module = env.MODULE
        profile = env.PROFILE
        environment = env.ENVIRONMENT
        tags = env.TAGS
        retryCount = env.RETRY_COUNT?.toInteger()
        parallelCount = env.PARALLEL_COUNT?.toInteger()
        healthCheckServices = env.HEALTH_CHECK?.split(',')*.trim() ?: []
        country = env.COUNTRY
        cloud = env.CLOUD
        os = env.OS
        osVersion = env.OS_VERSION
        device = env.DEVICE
        browser = env.BROWSER
        browserVersion = env.BROWSER_VERSION
        resolution = env.RESOLUTION
        app = env.APP
        app_type = env.APP_TYPE
        app_version = env.APP_VERSION
        app_build = env.APP_BUILD

        //PlayStation
        mainApp = env.MAIN_APP
        lastPatch = env.LAST_PATCH
    }

    def getArgs() {
        def args = '-Djenkins=true -Dlocal=false'

        /*
         * General parameters
         */

        if (module)
            args += " -pl \"$module\""

        if (profile)
            args += " -P \"$profile\""

        if (environment)
            args += " -Denvironment=\"$environment\""

        if (parallelCount != null)
            args += " -DparallelCount=\"$parallelCount\""

        if (retryCount != null) {
            args += " -DretryCount=\"$retryCount\""

            // TODO: Remove these parameters when all project will accept `retryCount` parameter
            args += " -Dsurefire.rerunFailingTestsCount=$retryCount"
            args += " -Dfailsafe.rerunFailingTestsCount=$retryCount"
        }

        if (runOnlyModifiedTestsFrom)
            args += " -DrunOnlyModifiedTestsFrom=\"$runOnlyModifiedTestsFrom\""

        if (tags) {
            args += " -Dtags=\"$tags\""
        }


        /*
         * TestRail
         */

        if (!disableTestRailPublish && testRailHost && testRailUsername && testRailPassword) {
            args += " -DTestRail.enabled=true"
            args += " -DTestRail.description=\"Job: [$jobName#$jobNumber]($jobUrl)\""

            if (testRailHost)
                args += " -DTestRail.host=\"$testRailHost\""

            if (testRailUsername)
                args += " -DTestRail.username=\"$testRailUsername\""

            if (testRailPassword)
                args += " -DTestRail.password=\"$testRailPassword\""

            if (testRailNewRunName) {
                args += " -DrunName=\"$testRailNewRunName\""
                args += " -DTestRail.name=\"$testRailNewRunName\""
            }

            if (testRailProject)
                args += " -DTestRail.project=\"$testRailProject\""

            if (testRailSuite)
                args += " -DTestRail.suite=\"$testRailSuite\""

            if (testRailPlan)
                args += " -DTestRail.plan=\"$testRailPlan\""

            if (testRailRun)
                args += " -DTestRail.run=\"$testRailRun\""
        }


        /*
         * JIRA
         */

        if (jiraHost && jiraUsername && jiraPassword) {
            args += " -Djira.host=$jiraHost"
            args += " -Djira.username=$jiraUsername"
            args += " -Djira.password=$jiraPassword"
        }


        /*
         * UI/Cloud Specific
         */

        if (country)
            args += " -Dcountry=\"$country\""

        if (cloud)
            args += " -Dcloud=\"$cloud\""

        if (os)
            args += " -Dos=\"$os\""

        if (osVersion) {
            args += " -Dos_version=\"$osVersion\"" // deprecated
            args += " -DosVersion=\"$osVersion\""
        }

        if (device)
            args += " -Ddevice=\"$device\""

        if (browser)
            args += " -Dbrowser=\"$browser\""

        if (browserVersion) {
            def version = (browserVersion == "latest") ? "" : browserVersion
            args += " -Dbrowser_version=\"$version\"" // deprecated
            args += " -DbrowserVersion=\"$version\""
        }

        if (resolution)
            args += " -Dresolution=\"$resolution\""

        if (app)
            args += " -Dapp=\"$app\""

        if (app_type)
            args += " -DappType=\"$app_type\""

        if (app_version)
            args += " -DappVersion=\"$app_version\""

        if (app_build)
            args += " -DappBuild=\"$app_build\""

        /*
         * PlayStation specific
         */

        if (mainApp)
            args += " -DmainApp=\"$mainApp\""

        if (lastPatch)
            args += " -DlastPatch=\"$lastPatch\""

        return args
    }
}
