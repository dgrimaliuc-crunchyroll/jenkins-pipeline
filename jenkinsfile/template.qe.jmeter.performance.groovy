library "ellation@qe"

qe {
    if (config.healthCheckServices)
        stage("Health Check") {
            parallel config.healthCheckServices.collectEntries { host ->
                return [(host): { healthCheck(host) }]
            }
        }

    stage("SCM") {
        git url: config.repository, branch: config.branch, credentialsId: config.gitCredentialId

        unstashFileParams()
    }

    stage("Run Performance") {
        def args = "-Djenkins=true"

        if (env.ENVIRONMENT)
            args += " -Denvironment=${env.ENVIRONMENT}"

        if (env.TENANT)
            args += " -Dtenant=${env.TENANT}"

        if (env.API)
            args += " -Dapi=${env.API}"

        if (env.LOCALE)
            args += " -locale=${env.LOCALE}"

        if (env.ENDPOINT)
            args += " -Dendpoint=${env.ENDPOINT}"

        if (env.ENDPOINT_TYPE)
            args += " -DendpointType=${env.ENDPOINT_TYPE}"

        if (env.TARGET_CONCURRENCY)
            args += " -DtargetConcurrency=${env.TARGET_CONCURRENCY}"

        if (env.RAMP_UP_TIME)
            args += " -DrampUpTime=${env.RAMP_UP_TIME}"

        if (env.RAMP_UP_STEPS_COUNT)
            args += " -DrampUpStepsCount=${env.RAMP_UP_STEPS_COUNT}"

        if (env.HOLD_TARGET_RATE_TIME)
            args += " -DholdTargetRateTime=${env.HOLD_TARGET_RATE_TIME}"

        if (env.TARGET_THROUGHPUT)
            args += " -DtargetThroughput=${env.TARGET_THROUGHPUT}"

        if (env.APDEX_SATISFIED)
            args += " -Dapdex_satisfied=${env.APDEX_SATISFIED}"

        if (env.APDEX_TOLERATED)
            args += " -Dapdex_tolerated=${env.APDEX_TOLERATED}"


        if (fileExists('performance.sh'))
            sh "JENKINS=true ./performance.sh"
        else
            sh "mvn clean initialize verify $args"
    }

    if (env.SKIP_PERFORMANCE_RESULTS_ARCHIVING != "true") {
        stage("Publish Results") {
            perfReport sourceDataFiles: "target/jmeter/results/*.csv", ignoreFailedBuilds: true, ignoreUnstableBuilds: true

            publishHTML([
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : "target/jmeter/reports/${env.API}",
                    reportFiles          : "index.html",
                    reportName           : "JMeter Dashboard",
                    reportTitles         : ""
            ])

            if (fileExists("target/jmeter/reports/${env.API}/report.html") && env.JOB_NAME.endsWith("-staging")) {
                currentBuild.description = readFile("target/jmeter/reports/${env.API}/report.html")
            }

            archiveArtifacts artifacts: "target/jmeter/reports/${env.API}/statistics.json"
        }
    }
}
