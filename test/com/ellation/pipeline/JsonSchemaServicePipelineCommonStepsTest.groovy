package com.ellation.pipeline

import com.ellation.web.Config
import groovy.mock.interceptor.MockFor
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class JsonSchemaServicePipelineCommonStepsTest {
    private def jenkinsScriptMock = new MockFor(MockPipelineScript)

    @Before
    void setUp() {
        jenkinsScriptMock.demand.getEnv { ["ETP_SSH_KEY_CREDENTIALS_ID": "ssh-credentials"] }
    }

    @Test
    void testUploadJsonSchemaAMI() {
        def checkoutCommand = [
                $class: 'GitSCM',
                branches: [[name: '5427a86b50fbbff1bb8974f715f98aa34891c02f']],
                userRemoteConfigs: [[credentialsId: 'ssh-credentials', url: 'https://github.com/crunchyroll/repository']]
        ]

        def buildSchemaCommand = [script: 'build schema', returnStdout: true]

        def uploadSchemaCommand = 'echo \'null\' | aws s3 cp - ' +
                's3://ellation-cx-proto0-config-schema-store/service/' +
                '5427a86b50fbbff1bb8974f715f98aa34891c02f/schema.json ' +
                '--acl bucket-owner-full-control'

        def uploadSchemaToLatestCommand = 'echo \'null\' | aws s3 cp - ' +
                's3://ellation-cx-proto0-config-schema-store/service/' +
                'latest/schema.json ' +
                '--acl bucket-owner-full-control'

        jenkinsScriptMock.demand.checkout { cmd -> assertEquals(checkoutCommand, cmd) }
        jenkinsScriptMock.demand.sh { cmd -> assertEquals(buildSchemaCommand, cmd) }
        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadSchemaCommand, cmd) }
        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadSchemaToLatestCommand, cmd) }

        jenkinsScriptMock.use {
            def schemaBuildCommand = "build schema"
            def config = createConfig(schemaBuildCommand)
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            def commitHash = "5427a86b50fbbff1bb8974f715f98aa34891c02f"
            commonSteps.uploadJsonSchemaAMI(commitHash)
        }
    }

    @Test
    void testUploadJsonSchemaDistHash() {
        def uploadSchemaCommand = 'aws s3 cp s3://ellation-cx-global-dist/service/' +
                '5427a86b50fbbff1bb8974f715f98aa34891c02f/proto0/ ' +
                's3://ellation-cx-proto0-config-schema-store/service/' +
                '5427a86b50fbbff1bb8974f715f98aa34891c02f/schema.json ' +
                '--acl bucket-owner-full-control'

        def uploadSchemaToLatestCommand = 'aws s3 cp s3://ellation-cx-global-dist/service/' +
                '5427a86b50fbbff1bb8974f715f98aa34891c02f/proto0/ ' +
                's3://ellation-cx-proto0-config-schema-store/service/' +
                'latest/schema.json ' +
                '--acl bucket-owner-full-control'

        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadSchemaCommand, (String) cmd) }
        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadSchemaToLatestCommand, (String) cmd) }

        jenkinsScriptMock.use {
            def config = createConfig()
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            def commitHash = "5427a86b50fbbff1bb8974f715f98aa34891c02f"
            commonSteps.uploadJsonSchemaDistHash(commitHash)
        }
    }

    @Test
    void testIsSchemaStoreIntegrationEnabledWithNullCommand() {
        def jsonSchemaBuildCommand = null
        def jsonSchemaPathDistHash = "5427a86b50fbbff1bb8974f715f98aa"
        def config = createConfig(jsonSchemaBuildCommand, jsonSchemaPathDistHash)

        jenkinsScriptMock.use {
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            assertTrue(commonSteps.isSchemaStoreIntegrationEnabled())
        }
    }

    @Test
    void testIsSchemaStoreIntegrationEnabledWithNullHash() {
        def jsonSchemaBuildCommand = "build schema"
        def jsonSchemaPathDistHash = null
        def config = createConfig(jsonSchemaBuildCommand, jsonSchemaPathDistHash)

        jenkinsScriptMock.use {
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            assertTrue(commonSteps.isSchemaStoreIntegrationEnabled())
        }
    }

    @Test
    void testIsSchemaStoreIntegrationEnabledWithNullCommandAndHash() {
        def jsonSchemaBuildCommand = null
        def jsonSchemaPathDistHash = null
        def config = createConfig(jsonSchemaBuildCommand, jsonSchemaPathDistHash)

        jenkinsScriptMock.use {
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            assertFalse(commonSteps.isSchemaStoreIntegrationEnabled())
        }
    }

    @Test
    void testIsSchemaStoreIntegrationEnabled() {
        def jsonSchemaBuildCommand = "build schema"
        def jsonSchemaPathDistHash = "5427a86b50fbbff1bb8974f715f98aa"
        def config = createConfig(jsonSchemaBuildCommand, jsonSchemaPathDistHash)

        jenkinsScriptMock.use {
            def commonSteps = new JsonSchemaServicePipelineCommonSteps(config, new MockPipelineScript())
            assertTrue(commonSteps.isSchemaStoreIntegrationEnabled())
        }
    }

    private Config createConfig(
            String jsonSchemaBuildCommand = null,
            String jsonSchemaPathDistHash = null
    ) {
        def env = [
                "ETP_SERVICE"                  : "service",
                "ETP_ENVIRONMENT"              : "proto0",
                "ETP_CREDENTIALS"              : "5427a86b50fbbff1bb8974f715f98aa3489",
                "ETP_REPOSITORY"               : "https://github.com/crunchyroll/repository",
                "ETP_CFD_SCHEMA_BUILD_COMMAND" : jsonSchemaBuildCommand,
                "ETP_CFD_SCHEMA_PATH_DIST_HASH": jsonSchemaPathDistHash,
        ]
        return new Config(env, [:])
    }

    private class MockPipelineScript extends Script {
        @Override
        Object run() {
            return null
        }
    }
}
