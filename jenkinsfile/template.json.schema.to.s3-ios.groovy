@Library('ellation') _

import org.jenkinsci.plugins.workflow.libs.Library
import groovy.json.JsonSlurperClassic
import com.ellation.configdelta.JsonSchemaUploader
import com.ellation.ios.PlistParser

node("universal") {
    stage("clean the workspace") {
        step([$class: 'WsCleanup'])
    }

    stage("extract payload") {
        payloadJson = new JsonSlurperClassic().parseText(params.payload)
    }

    stage("checkout & init workspace") {
        if (env.BRANCH_OVERRIDE &&
                env.REPO_URL_OVERRIDE &&
                env.CONFIG_DELTA_SERVICE_NAME_OVERRIDE &&
                env.TARGET_NAME_OVERRIDE
        ) {
            // manual job execution, all required parameters should be provided
            env.CONFIG_DELTA_SERVICE_NAME = env.CONFIG_DELTA_SERVICE_NAME_OVERRIDE
            env.TARGET_NAME = env.TARGET_NAME_OVERRIDE
            git url: env.REPO_URL_OVERRIDE, branch: env.BRANCH_OVERRIDE, credentialsId: env.ETP_CREDENTIALS
        } else if (payloadJson.ref == "refs/heads/${env.DELTA_CONFIG_SCHEMA_BRANCH}") {
            git url: payloadJson.repository.clone_url, branch: env.DELTA_CONFIG_SCHEMA_BRANCH, credentialsId: env.ETP_CREDENTIALS
        } else {
            currentBuild.result = 'ABORTED'
            error("Aborting since the push was to other branch than ${env.DELTA_CONFIG_SCHEMA_BRANCH}.")
        }
    }

    stage("extract application version") {
        targetWorkspace = "${pwd()}/${env.TARGET_NAME}"
        infoPlist = PlistParser.parseXmlPlistText(readFile("${targetWorkspace}${env.INFO_PLIST_FILE_PATH}"))
        versionName = infoPlist.CFBundleShortVersionString
    }

    stage('upload Json schema') {
        if(versionName) {
            JsonSchemaUploader uploader = new JsonSchemaUploader(this, env.CONFIG_DELTA_SERVICE_NAME)
            def path = "${targetWorkspace}${env.JSON_SCHEMA_FILE_PATH}"
            uploader.uploadFromFile(path, "proto0", versionName, "ellationeng")
            uploader.uploadFromFile(path, "staging", versionName, "ellationeng")
            uploader.uploadFromFile(path, "prod", versionName, "ellation")

            uploader.uploadFromFile(path, "proto0", "latest", "ellationeng")
            uploader.uploadFromFile(path, "staging", "latest", "ellationeng")
            uploader.uploadFromFile(path, "prod", "latest", "ellation")
        } else {
            currentBuild.result = 'ABORTED'
            error("Aborting since required version name is null or empty")
        }
    }
}
