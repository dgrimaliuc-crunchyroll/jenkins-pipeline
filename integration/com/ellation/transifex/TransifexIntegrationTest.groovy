package com.ellation.transifex

import com.ellation.BaseJenkinsTest
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals

class TransifexIntegrationTest extends BaseJenkinsTest {
    @Before
    void setUp() throws Exception {
        loadSharedLibraries()
    }

    @Test
    void verifiesLoadConfigFile() throws Exception {
        def testConfig = """{
                            "stagingS3BucketDest": "s3://ellation-staging",
                            "prodS3BucketDest": "s3://ellation-production",
                            "txModeStaging": "onlytranslated",
                            "txModeProduction": "onlyreviewed",
                            "projectName": "cr-project-name",
                            "resources": [
                                {
                                    "repoFilePath": "config/locale",
                                    "fileName": "localizations",
                                    "fileExt": "json",
                                    "i18nType": "KEYVALUEJSON"
                                },
                                {
                                    "repoFilePath": "config/locale",
                                    "fileName": "homePage",
                                    "fileExt": "json",
                                    "i18nType": "KEYVALUEJSON"
                                }
                            ]
                        }"""

        def jobDefinition = createWorkflowJob("projectUnderTest",
                """
                @Library('ellation@master') _

                import com.ellation.transifex.TransifexConfigLoader
                import groovy.json.JsonSlurperClassic
                import static org.junit.Assert.assertEquals

                node {
                    def testConfig = '''$testConfig'''
                    def testConfigObject = new JsonSlurperClassic().parseText(testConfig)

                    sh "mkdir .transifex"
                    sh "echo '\$testConfig' > ./.transifex/config.json"

                    def loader = new TransifexConfigLoader()
                    def configFile = loader.loadConfigFile(this)

                    assertEquals(configFile, testConfigObject);
                }
                """)

        r.assertBuildStatusSuccess(jobDefinition.scheduleBuild2(0))
    }
}
