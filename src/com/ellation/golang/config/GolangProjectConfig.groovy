package com.ellation.golang.config

import com.cloudbees.groovy.cps.NonCPS

class GolangProjectConfig implements Serializable {
    public String slackFallbackChannelId

    /**
     * A base path for the main package (e.g. "github.com/author/repository")
     *
     * If not specified an attempt is made to derive it from scm URL
     */
    String mainPackage
    String goVersion
    String methodCoverageTargets
    String lineCoverageTargets
    String conditionalCoverageTargets
    private String gitUri

    GolangProjectConfig(Map params, String gitUri) {
        this.gitUri = gitUri
        mainPackage = getParameterOrDefault params, 'mainPackage', getPackagePathFromScm()
        goVersion = getParameterOrDefault params, 'goVersion', 'Go 1.11.5'
        slackFallbackChannelId = getParameterOrDefault(
                params,
                'slackFallbackChannel',
                '#admin-services-md'
        )
        methodCoverageTargets = getParameterOrDefault params, 'methodCoverageTargets', '80, 50, 0'
        lineCoverageTargets = getParameterOrDefault params, 'lineCoverageTargets', '80, 50, 0'
        conditionalCoverageTargets = getParameterOrDefault params, 'conditionalCoverageTargets', '70, 30, 0'
    }

    /**
     * @return Returns value for given parameterName or supplied default if parameter not found in given map.
     */
    @NonCPS
    private static String getParameterOrDefault(Map params, String parameterName, String defaultValue) {
        if (params?.containsKey(parameterName)) {
            return params.get(parameterName)
        }
        return defaultValue
    }

    @NonCPS
    private String getPackagePathFromScm() {
        def uri = gitUri
        if (uri.startsWith('git@')) {
            uri = 'ssh://' + uri.replace(':', '/')
        }
        def scmUri = new URI(uri)

        def mainDir = scmUri.getHost() + scmUri.getPath()
        if (mainDir.endsWith('.git')) {
            mainDir = mainDir[0..-5]
        }

        return mainDir
    }
}
