package com.ellation.ios

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull

class PlistParserTest {
    @Test
    void testParseXmlPlistTextAndExtractVersion() {
        def expectedVersion  = "4.0.0"
        def plistText = """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleShortVersionString</key>
                <string>${expectedVersion}</string>
            </dict>
            </plist>
        """

        def plist = PlistParser.parseXmlPlistText(plistText)
        String version = plist.CFBundleShortVersionString

        assertEquals(expectedVersion, version)
    }

    @Test
    void testParseXmlPlistTextAndExtractVersionWhenVersionNotPresent() {
        def plistText = """<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                </dict>
                </plist>
            """

        def plist = PlistParser.parseXmlPlistText(plistText)

        assertNull(plist.CFBundleShortVersionString)
    }
}
