package com.ellation.transifex.config

import com.cloudbees.groovy.cps.NonCPS

/**
 * Provides config properties and helper methods.
 */
class TransifexConfig implements Serializable {

    /**
     * Project name. Transifex and GitHub projects MUST have the same name!
     */
    final String projectName

    /**
     * ArrayList with list of resources.
     * Each resource is an object and must have the following keys:
     * repoFilePath, fileName, fileExt, i18nType
     * Example:
     *{
     *  "repoFilePath": "config/locale",
     *  "fileName": "localizations",
     *  "fileExt": "json"
     *  "i18nType": "KEYVALUEJSON",
     *  "customResourceName": "etpandroidapp"
     *}
     */
    final ArrayList resources

    /**
     * Destination S3 production bucket where translations will be delivered.
     */
    final String prodS3BucketDest

    /**
     * Destination S3 staging bucket where translations will be delivered.
     */
    final String stagingS3BucketDest

    /**
     * Destination S3 proto0 bucket where translations will be delivered.
     */
    final String proto0S3BucketDest

    /**
     * Transifex production mode that will be used to download the translations.
     * For reference: https://docs.transifex.com/client/pull#getting-different-file-variants
     */
    final String txModeProd

    /**
     * Transifex staging mode that will be used to download the translations.
     * For reference: https://docs.transifex.com/client/pull#getting-different-file-variants
     */
    final String txModeStaging

    /**
     * The default locale of the source file.
     */
    final String defaultLocale = 'en'

    /**
     * Transifex URL prefix.
     */
    final String txUrlPrefix = 'https://www.transifex.com/ellation'

    /**
     * Transifex will have two webhooks(for staging and production environments).
     * They will trigger two different jenkins jobs.
     * Depends on this the system knows what s3 bucket should be used.
     */
    final String txEnvStaging = 'staging'
    final String txEnvProd = 'production'

    TransifexConfig(Map params) {
        resources = getParameterOrThrow(params, "resources")
        prodS3BucketDest = getParameterOrThrow(params, "prodS3BucketDest")
        stagingS3BucketDest = getParameterOrThrow(params, "stagingS3BucketDest")
        proto0S3BucketDest = getParameterOrThrow(params, "proto0S3BucketDest")
        txModeProd = getParameterOrThrow(params, "txModeProduction")
        txModeStaging = getParameterOrThrow(params, "txModeStaging")
        projectName = getParameterOrThrow(params, "projectName")
    }

    /**
     * @return Returns value for given parameterName or throws if parameter not found in given map.
     */
    @NonCPS
    protected static getParameterOrThrow(Map params, parameterName) {
        if (params.containsKey(parameterName)) {
            return params.get(parameterName)
        }
        throw new IllegalArgumentException("'$parameterName' is required.")
    }
}
