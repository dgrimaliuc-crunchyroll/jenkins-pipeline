#!/usr/bin/groovy
package com.ellation.transifex

import groovy.json.JsonSlurperClassic

/**
 * Load config file from the project's Github repository for providing Transifex implementation.
 */
class TransifexConfigLoader implements Serializable {

    /**
     * The path to the config file must be the same in all repositories (/.transifex/config.json)!
     */
    private String repoConfigFilePath = '.transifex/config.json'

    /**
     * Load config json file from repository.
     * Required parameters: repoConfigFilePath
     */
    def loadConfigFile(script) {
        if (script.fileExists(repoConfigFilePath)) {
            def inputFile = script.readFile(repoConfigFilePath)
            def params = new JsonSlurperClassic().parseText(inputFile)
            return params
        }
        return null
    }
}
