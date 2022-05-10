#!/usr/bin/groovy
package com.ellation.transifex.steps

import com.ellation.transifex.config.TransifexConfig
import groovy.json.JsonSlurperClassic
import groovy.transform.Field

@Field TransifexConfig projectConfig

/**
 * Pass Transifex project config from outside using this method at the beginning of your pipeline.
 * @param config contains some info required for TransifexPipelineSteps to work correctly
 */
void withConfig(TransifexConfig config) {
    projectConfig = config
}

/**
 * Get access to the gihub repository.
 *
 * @param gitHubRepo url to the github repository
 * @param branch git branch to checkout
 */
def checkoutSCM(gitHubRepo, branch) {
    git url: gitHubRepo, branch: branch, credentialsId: env.ETP_CREDENTIALS
}

def tx(String args) {
    sh("tx --debug --traceback $args")
}

/**
 * Init Transifex client and sets up the project remote.
 */
def initTransifexClient() {
    tx('--version')
    tx('init --force --no-interactive')
    tx("config mapping-remote ${projectConfig.txUrlPrefix}/${projectConfig.projectName}")
}

/**
 * Updates .tx/config file with required mappings for resources listed in .transifex/config.json.
 *
 * @param sourceFileProvider a lambda through which the method gets the Transifex source file for a specific resource.
 * @param expressionProvider a lambda through which the method gets the Transifex expression for a specific resource.
 */
def updateTransifexConfig(expressionProvider, sourceFileProvider = { "${it.repoFilePath}/${it.fileName}.${it.fileExt}" }) {
    projectConfig.resources.each {
        def resName = it.customResourceName ?: "${it.fileName}${it.fileExt}"
        def sourceFile = sourceFileProvider(it)
        def expression = expressionProvider(it)
        tx("config mapping --resource ${projectConfig.projectName}.${resName} --source-lang ${projectConfig.defaultLocale} -t ${it.i18nType} --source-file ${sourceFile} --expression '$expression' --execute")
    }
    tx('status')
}

/**
 * Upload files with translations keys from the repository to transifex.
 */
def uploadTranslationsKeysToTx() {
    tx("push --source --no-interactive --parallel")
}

/**
 * Download translated files from transifex.
 */
def downloadTranslatedFilesFromTx() {
    def txMode = (env.TX_ENV == projectConfig.txEnvProd) ? projectConfig.txModeProd : projectConfig.txModeStaging
    tx("pull --source --force --resource ${projectConfig.projectName}.* --mode ${txMode} --all --no-interactive")
}

/**
 * Upload downloaded files from transifex to S3 bucket.
 *
 * @param path path to the translations that should be uploaded to s3 bucket.
 */
def uploadTranslationsToBucket(path = './translations') {
    sh("aws --version")
    if (env.TX_ENV == projectConfig.txEnvProd) {
        sh("aws s3 sync ${path} ${projectConfig.prodS3BucketDest}")
    } else {
        sh("aws s3 sync ${path} ${projectConfig.proto0S3BucketDest}")
        sh("aws s3 sync ${path} ${projectConfig.stagingS3BucketDest}")
    }
}

/**
 * Get GitHub repository name from TX payload.
 *
 * @param githubProjectName github repository name
 * @return github repository url formed by concatenation of the github domain and githubProjectName
 */
def getRepoFromTXPayload(String githubProjectName) {
    return "${env.GITHUB_REPO_PREFIX}${githubProjectName}"
}

/**
 * Get GitHub repository name from GitHub payload.
 *
 * @param payload github webhook payload
 * @return github repository url extracted from the github webhook payload
 */
def getRepoFromGitHubPayload(String payload) {
    payloadJson = new JsonSlurperClassic().parseText(payload)
    return payloadJson.repository.html_url
}

/**
 * Get pushed ref from GitHub payload.
 *
 * @param payload github webhook payload
 * @return github ref in the example format "refs/heads/master"
 */
def getPushedRefGitHubPayload(String payload) {
    payloadJson = new JsonSlurperClassic().parseText(payload)
    return payloadJson.ref
}

/**
 * Attaches files to the build.
 *
 * @param artifact regexp which matches the files to be attached to the build.
 */
def attachTranslations(artifact) {
    archiveArtifacts artifacts: artifact, fingerprint: true
}

return this
