package com.ellation.golang.config

import org.junit.Test

import static org.junit.Assert.assertEquals

class GolangProjectConfigTest {

    @Test
    void initializesParameterWhenPassed() {
        def config = new GolangProjectConfig(
                'https://github.com/author/package',
                mainPackage: 'github/package',
                goVersion: '1.2.3',
                slackFallbackChannel: '#chanel',
                methodCoverageTargets: '80',
                lineCoverageTargets: '70',
                conditionalCoverageTargets: '60'
        )

        assertEquals('github/package', config.mainPackage)
        assertEquals('1.2.3', config.goVersion)
        assertEquals('#chanel', config.slackFallbackChannelId)
        assertEquals('80', config.methodCoverageTargets)
        assertEquals('70', config.lineCoverageTargets)
        assertEquals('60', config.conditionalCoverageTargets)
    }

    @Test
    void calculatePackageWhenNotPassed() {
        def config = new GolangProjectConfig(null, 'https://github.com/author/package.git')

        assertEquals('github.com/author/package', config.mainPackage)
    }

}
