package com.ellation.configdelta

import groovy.mock.interceptor.MockFor
import org.junit.Test

import static org.junit.Assert.*

class JsonSchemaUploaderTest {
    def jenkinsScriptMock = new MockFor(MockPipelineScript)

    @Test
    void testUpload() {
        def uploadCmd = "echo '{}' | aws s3 cp - " +
                "s3://ellation-cx-proto0-config-schema-store/cr-android/3.0/schema.json " +
                "--acl bucket-owner-full-control"

        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadCmd, (String) cmd) }

        jenkinsScriptMock.use {
            def schemaUploader = new JsonSchemaUploader(new MockPipelineScript(), "cr-android")
            schemaUploader.upload("{}", "proto0", "3.0")
        }
    }

    @Test
    void testUploadWithSpecifiedAwsProfile() {
        def uploadCmd = "echo '{}' | aws s3 cp - " +
                "s3://ellation-cx-proto0-config-schema-store/cr-android/3.0/schema.json " +
                "--acl bucket-owner-full-control --profile ellation"

        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadCmd, (String) cmd) }

        jenkinsScriptMock.use {
            def schemaUploader = new JsonSchemaUploader(new MockPipelineScript(), "cr-android")
            schemaUploader.upload("{}", "proto0", "3.0", "ellation")
        }
    }

    @Test
    void testUploadFromFile() {
        def uploadCmd = "aws s3 cp ./schema.json " +
                "s3://ellation-cx-proto0-config-schema-store/cr-android/3.0/schema.json " +
                "--acl bucket-owner-full-control"

        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadCmd, (String) cmd) }

        jenkinsScriptMock.use {
            def schemaUploader = new JsonSchemaUploader(new MockPipelineScript(), "cr-android")
            schemaUploader.uploadFromFile("./schema.json", "proto0", "3.0")
        }
    }

    @Test
    void testUploadFromFileWithSpecifiedAwsProfile() {
        def uploadCmd = "aws s3 cp ./schema.json " +
                "s3://ellation-cx-proto0-config-schema-store/cr-android/3.0/schema.json " +
                "--acl bucket-owner-full-control --profile ellation"

        jenkinsScriptMock.demand.sh { cmd -> assertEquals(uploadCmd, (String) cmd) }

        jenkinsScriptMock.use {
            def schemaUploader = new JsonSchemaUploader(new MockPipelineScript(), "cr-android")
            schemaUploader.uploadFromFile("./schema.json", "proto0", "3.0", "ellation")
        }
    }

    private class MockPipelineScript extends Script {
        @Override
        Object run() {
            return null
        }
    }
}
