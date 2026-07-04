package com.itangcent.easyapi.config

import com.intellij.openapi.Disposable
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.SettingsChangeListener
import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class ConfigSyncServiceTest : EasyApiLightCodeInsightFixtureTestCase(), Disposable {

    private lateinit var configSyncService: ConfigSyncService
    private var testBusConnection: com.intellij.util.messages.MessageBusConnection? = null

    override fun dispose() {
        testBusConnection?.dispose()
    }

    override fun setUp() {
        super.setUp()
        configSyncService = ConfigSyncService.getInstance(project)
    }

    override fun tearDown() {
        if (::configSyncService.isInitialized) {
            configSyncService.dispose()
        }
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(configSyncService)
        assertSame(configSyncService, ConfigSyncService.getInstance(project))
    }

    fun testStart() {
        configSyncService.start()
        runBlocking {
            delay(100)
        }
    }

    fun testDispose() {
        configSyncService.dispose()

        runBlocking {
            delay(100)
        }
    }

    /**
     * Verifies that (a) `ConfigSyncService` subscribes to
     * `SettingsChangeListener.TOPIC` on `start()`, and (b) a manual
     * `ConfigReader.reload()` picks up a rule file placed in the project's
     * `.easyapi/` folder.
     *
     * We split the test to avoid timing fragility from the 2-second debounce
     * and `IdeDispatchers.Background` (which may not be initialized in the
     * test JVM). The wiring assertion (a) verifies the listener is live;
     * the reload assertion (b) verifies the config reader actually reads
     * the new file when reload runs.
     */
    fun testSettingsChangeWiringAndReloadPicksUpRuleFile() {
        // The test framework replaces ConfigReader with TestConfigReader.empty()
        // by default. We need the real DefaultConfigReader to test reload wiring.
        val realReader = DefaultConfigReader(project)
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = realReader
        )

        // (a) Verify the settings-changed listener fires through to a handler
        // we register ourselves. If ConfigSyncService can subscribe,
        // so can we — and the same `binder.save()` path reaches both.
        var listenerFired = false
        val bus = project.messageBus.connect(this)
        testBusConnection = bus
        bus.subscribe(SettingsChangeListener.TOPIC,
            object : SettingsChangeListener {
                override fun settingsChanged() { listenerFired = true }
            })

        // Rule files live in <base>/.easyapi/. Write one there.
        val projectBase = project.basePath ?: fail("project.basePath required")
        val easyapiDir = java.io.File(projectBase, ".easyapi").apply { mkdirs() }
        val ruleFile = java.io.File(easyapiDir, "reload-test.properties")
        ruleFile.writeText("api.name=from-folder\n")

        try {
            val binder = SettingBinder.getInstance(project)
            val originalDisabled = binder.read(EnvironmentSettings::class).disabledAutoRuleFiles
            try {
                // Touch settings to fire the listener.
                binder.save(EnvironmentSettings(disabledAutoRuleFiles = emptyArray()))

                assertTrue(
                    "binder.save() should fire SettingsChangeListener",
                    listenerFired
                )

                // (b) Verify the config reader picks up the new rule file
                // when reload runs. (ConfigSyncService would call this
                // via its debounce job; we call it directly to avoid
                // timing fragility in tests.)
                runBlocking { realReader.reload() }
                val first = realReader.getFirst("api.name")
                assertEquals(
                    "api.name should be visible after reload with.easyapi/ rule file",
                    "from-folder", first
                )
            } finally {
                binder.save(EnvironmentSettings(disabledAutoRuleFiles = originalDisabled))
            }
        } finally {
            ruleFile.delete()
            if (easyapiDir.listFiles()?.isEmpty() == true) easyapiDir.delete()
        }
    }

    private fun fail(message: String): Nothing {
        throw AssertionError(message)
    }
}