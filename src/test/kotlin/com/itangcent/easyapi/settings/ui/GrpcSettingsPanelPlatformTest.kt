package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class GrpcSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: GrpcSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = GrpcSettingsPanel(project)
    }

    fun testResetFromAndApplyToDefaultSettings() {
        val settings = com.itangcent.easyapi.settings.Settings()
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        assertTrue(target.grpcEnable)
        assertFalse(target.grpcCallEnabled)
    }

    fun testResetFromCustomSettingsAndApplyTo() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            grpcEnable = false
            grpcCallEnabled = true
        }
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        assertFalse(target.grpcEnable)
        assertTrue(target.grpcCallEnabled)
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testResetFromWithArtifactConfigs() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            grpcCallEnabled = true
            grpcArtifactConfigs = arrayOf(
                "io.grpc:grpc-stub:latest:true",
                "io.grpc:grpc-protobuf:1.58.0:false"
            )
        }
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        assertTrue(target.grpcCallEnabled)
        assertTrue(target.grpcArtifactConfigs.isNotEmpty())
    }

    fun testResetFromWithAdditionalJars() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            grpcCallEnabled = true
            grpcAdditionalJars = arrayOf("/path/to/jar1.jar", "/path/to/jar2.jar")
        }
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        assertTrue(target.grpcAdditionalJars.isNotEmpty())
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromNullAndApplyTo() {
        panel.resetFrom(null)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)
        // Should not throw
        assertTrue(target.grpcEnable)
        assertFalse(target.grpcCallEnabled)
    }
}
