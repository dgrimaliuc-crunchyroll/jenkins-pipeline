#!/usr/bin/groovy
import com.ellation.golang.config.GolangProjectConfig
import com.ellation.golang.steps.GolangPipelineSteps

def call(Map params) {
    node('universal') {
        def golangPipelineSteps = new GolangPipelineSteps()
        def config = new GolangProjectConfig(params, getScmUri())
        golangPipelineSteps.withConfig(config)

        def root = tool name: config.goVersion, type: 'go'
        def mainDir = config.mainPackage
        ansiColor('xterm') {
            withEnv(["GOROOT=${root}", "PATH+GO=${root}/bin:${pwd()}/bin", "GOPATH=${pwd()}/"]) {
                dir('src/' + mainDir) {
                    def tryCatchException
                    try {
                        checkout scm
                        timestamps {
                            stage('code-check') {
                                golangPipelineSteps.runCodeStyleChecks()
                            }
                            stage('coverage') {
                                golangPipelineSteps.unitTests()
                                golangPipelineSteps.publishCoverage()
                            }
                        }
                        currentBuild.result = 'SUCCESS'
                    } catch (Exception exception) {
                        currentBuild.result = 'FAILURE'
                        tryCatchException = exception
                    } finally {
                        try {
                            golangPipelineSteps.notify(currentBuild.result)
                        } catch (Exception exception) {
                            // throw exception from try-catch
                            // so that it's not swallowed by exception from finally
                            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-14.html#jls-14.20.2
                            if (tryCatchException != null) {
                                throw tryCatchException
                            } else {
                                throw exception
                            }
                        }
                    }
                }
            }
        }
    }
}

def getScmUri() {
    return scm.getUserRemoteConfigs()[0].getUrl()
}

return this
