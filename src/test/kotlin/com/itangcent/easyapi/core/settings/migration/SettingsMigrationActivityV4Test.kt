package com.itangcent.easyapi.core.settings.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.state.ApplicationSettingsState
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests for the v4 branch of [SettingsMigrationActivity] — the translation
 * from legacy per-framework booleans (`feignEnable`, `jaxrsEnable`,
 * `actuatorEnable`, `grpcEnable`) on `ApplicationSettingsState.State` to the
 * unified `enabledFrameworks`/`disabledFrameworks` arrays on `GeneralSettings`.
 *
 * Coverage per PR7 (design-framework-enablement.md):
 * - (a) `feignEnable == true` → `enabledFrameworks += "Feign"`
 * - (b) `jaxrsEnable == false` → `disabledFrameworks += "JAX-RS"`
 * - (c) `actuatorEnable == true` → `enabledFrameworks += "SpringActuator"`
 * - (d) `grpcEnable == false` → `disabledFrameworks += "gRPC"`
 * - (e) All legacy defaults → both arrays empty (rely on `enabledByDefault`)
 * - (f) Mixed non-defaults → both arrays populated
 * - (g) Idempotency: running twice produces the same state
 * - (h) Missing legacy state → warns and falls back to defaults
 * - (i) Pre-existing entries in the arrays are preserved (no duplicates)
 */
@Suppress("DEPRECATION")  // ApplicationSettingsState is deprecated; retained for one-time migration
class SettingsMigrationActivityV4Test : EasyApiLightCodeInsightFixtureTestCase() {

    private val generalKey = GeneralSettings::class.qualifiedName!!
    private lateinit var migrationFlag: MigrationFlag
    private lateinit var appState: UnifiedAppSettingsState
    private lateinit var legacyApp: ApplicationSettingsState

    override fun setUp() {
        super.setUp()
        migrationFlag = MigrationFlag.getInstance()
        appState = UnifiedAppSettingsState.getInstance()
        legacyApp = ApplicationManager.getApplication()
            .getService(ApplicationSettingsState::class.java)!!
        // Reset to allow migration to run for each test.
        migrationFlag.loadState(MigrationFlag.State(migrated = false, version = 0))
        // Clear unified app state so prior tests' writes don't leak in.
        appState.loadState(UnifiedAppSettingsState.State())
        // Set default legacy state (all 4 framework booleans at their pre-patch defaults).
        legacyApp.loadState(ApplicationSettingsState.State())
    }

    override fun tearDown() {
        try {
            // Restore the original ApplicationSettingsState so a null-state instance
            // registered by testMissingLegacyState_warnsAndFallsBackToDefaults does
            // not leak into subsequent test classes. Mirrors the tearDown pattern in
            // ChannelRegistryIsEnabledTest / FieldFormatChannelRegistryIsEnabledTest.
            if (::legacyApp.isInitialized) {
                ApplicationManager.getApplication().registerServiceInstance(
                    ApplicationSettingsState::class.java,
                    legacyApp
                )
            }
        } finally {
            super.tearDown()
        }
    }

    private fun runMigration() {
        SettingsMigrationActivity().runActivity(project)
    }

    private fun enabledFrameworks(): List<String> {
        val json = appState.getValue(generalKey, "enabledFrameworks") ?: return emptyList()
        return GsonUtils.fromJson<Array<String>>(json).toList()
    }

    private fun disabledFrameworks(): List<String> {
        val json = appState.getValue(generalKey, "disabledFrameworks") ?: return emptyList()
        return GsonUtils.fromJson<Array<String>>(json).toList()
    }

    /**
     * Loads a legacy [ApplicationSettingsState.State] with the given framework
     * toggles. Other fields stay at their defaults.
     */
    private fun setLegacyState(
        feignEnable: Boolean = false,
        jaxrsEnable: Boolean = true,
        actuatorEnable: Boolean = false,
        grpcEnable: Boolean = true
    ) {
        legacyApp.loadState(
            ApplicationSettingsState.State(
                feignEnable = feignEnable,
                jaxrsEnable = jaxrsEnable,
                actuatorEnable = actuatorEnable,
                grpcEnable = grpcEnable
            )
        )
    }

    private fun assertMigrationCompleted() {
        assertTrue(
            "Migration should have completed (migrated=true)",
            migrationFlag.state.migrated
        )
        assertEquals(4, migrationFlag.state.version)
    }

    // --- (a) feignEnable == true → enabledFrameworks += "Feign" ---

    fun testFeignEnableTrue_addsFeignToEnabled() {
        setLegacyState(feignEnable = true)
        runMigration()
        assertMigrationCompleted()
        assertTrue("Feign should be in enabledFrameworks", "Feign" in enabledFrameworks())
        assertFalse("JAX-RS should not be in disabledFrameworks", "JAX-RS" in disabledFrameworks())
        assertFalse("SpringActuator should not be in enabledFrameworks", "SpringActuator" in enabledFrameworks())
        assertFalse("gRPC should not be in disabledFrameworks", "gRPC" in disabledFrameworks())
    }

    // --- (b) jaxrsEnable == false → disabledFrameworks += "JAX-RS" ---

    fun testJaxrsEnableFalse_addsJaxrsToDisabled() {
        setLegacyState(jaxrsEnable = false)
        runMigration()
        assertMigrationCompleted()
        assertTrue("JAX-RS should be in disabledFrameworks", "JAX-RS" in disabledFrameworks())
        assertFalse("Feign should not be in enabledFrameworks", "Feign" in enabledFrameworks())
        assertFalse("SpringActuator should not be in enabledFrameworks", "SpringActuator" in enabledFrameworks())
        assertFalse("gRPC should not be in disabledFrameworks", "gRPC" in disabledFrameworks())
    }

    // --- (c) actuatorEnable == true → enabledFrameworks += "SpringActuator" ---

    fun testActuatorEnableTrue_addsSpringActuatorToEnabled() {
        setLegacyState(actuatorEnable = true)
        runMigration()
        assertMigrationCompleted()
        assertTrue("SpringActuator should be in enabledFrameworks", "SpringActuator" in enabledFrameworks())
        assertFalse("Feign should not be in enabledFrameworks", "Feign" in enabledFrameworks())
        assertFalse("JAX-RS should not be in disabledFrameworks", "JAX-RS" in disabledFrameworks())
        assertFalse("gRPC should not be in disabledFrameworks", "gRPC" in disabledFrameworks())
    }

    // --- (d) grpcEnable == false → disabledFrameworks += "gRPC" ---

    fun testGrpcEnableFalse_addsGrpcToDisabled() {
        setLegacyState(grpcEnable = false)
        runMigration()
        assertMigrationCompleted()
        assertTrue("gRPC should be in disabledFrameworks", "gRPC" in disabledFrameworks())
        assertFalse("Feign should not be in enabledFrameworks", "Feign" in enabledFrameworks())
        assertFalse("JAX-RS should not be in disabledFrameworks", "JAX-RS" in disabledFrameworks())
        assertFalse("SpringActuator should not be in enabledFrameworks", "SpringActuator" in enabledFrameworks())
    }

    // --- (e) All legacy defaults → both arrays empty ---

    fun testAllDefaults_noEntriesAdded() {
        setLegacyState() // all 4 booleans at pre-patch defaults
        runMigration()
        assertMigrationCompleted()
        assertTrue(
            "enabledFrameworks should be empty when all legacy values match defaults",
            enabledFrameworks().isEmpty()
        )
        assertTrue(
            "disabledFrameworks should be empty when all legacy values match defaults",
            disabledFrameworks().isEmpty()
        )
    }

    // --- (f) Mixed non-defaults → both arrays populated ---

    fun testMixedNonDefaults_allEntriesAdded() {
        setLegacyState(
            feignEnable = true,
            jaxrsEnable = false,
            actuatorEnable = true,
            grpcEnable = false
        )
        runMigration()
        assertMigrationCompleted()
        val enabled = enabledFrameworks()
        val disabled = disabledFrameworks()
        assertTrue("Feign should be in enabledFrameworks", "Feign" in enabled)
        assertTrue("SpringActuator should be in enabledFrameworks", "SpringActuator" in enabled)
        assertTrue("JAX-RS should be in disabledFrameworks", "JAX-RS" in disabled)
        assertTrue("gRPC should be in disabledFrameworks", "gRPC" in disabled)
        // SpringMVC has no legacy boolean — must not appear in either array
        assertFalse("SpringMVC should not be in enabledFrameworks", "SpringMVC" in enabled)
        assertFalse("SpringMVC should not be in disabledFrameworks", "SpringMVC" in disabled)
    }

    // --- (g) Idempotency: running twice produces the same state ---

    fun testIdempotency_runningTwiceProducesSameState() {
        setLegacyState(feignEnable = true, jaxrsEnable = false)
        runMigration()
        val firstRunEnabled = enabledFrameworks()
        val firstRunDisabled = disabledFrameworks()

        // Reset the flag so the migration runs again on the same state.
        migrationFlag.loadState(MigrationFlag.State(migrated = false, version = 0))
        runMigration()
        assertMigrationCompleted()

        assertEquals(
            "enabledFrameworks must not change on second migration run",
            firstRunEnabled,
            enabledFrameworks()
        )
        assertEquals(
            "disabledFrameworks must not change on second migration run",
            firstRunDisabled,
            disabledFrameworks()
        )
    }

    // --- (h) Missing legacy state → warns and falls back to defaults ---

    fun testMissingLegacyState_warnsAndFallsBackToDefaults() {
        // Replace ApplicationSettingsState with an instance whose internal state
        // is nulled out via reflection, simulating "legacy state unavailable".
        // The migration's `?.state` safe call then resolves to null, triggering
        // the warn-and-fallback path. tearDown restores the original service so
        // the null-state instance does not leak into subsequent test classes.
        //
        // ApplicationSettingsState is final (not `open`), so it cannot be
        // subclassed with an `object : ApplicationSettingsState()` override.
        // Reflection on the private `state` field is the only way to make
        // `getState()` return null at runtime (the Kotlin signature is
        // non-null, but Kotlin-to-Kotlin calls don't insert null checks,
        // so the null propagates to the migration's `?.state` safe call).
        @Suppress("DEPRECATION")
        val nullStateApp = ApplicationSettingsState()
        val stateField = ApplicationSettingsState::class.java.getDeclaredField("state")
        stateField.isAccessible = true
        stateField.set(nullStateApp, null)

        ApplicationManager.getApplication().registerServiceInstance(
            ApplicationSettingsState::class.java,
            nullStateApp
        )
        runMigration()
        // Migration still completes (migrateApplicationSettings returns early,
        // but runActivity proceeds to migrateProjectSettings and sets the flag).
        assertMigrationCompleted()
        assertTrue(
            "enabledFrameworks should be empty when legacy state is missing",
            enabledFrameworks().isEmpty()
        )
        assertTrue(
            "disabledFrameworks should be empty when legacy state is missing",
            disabledFrameworks().isEmpty()
        )
    }

    // --- (i) Pre-existing entries in the arrays are preserved ---

    fun testPreExistingEntriesPreserved() {
        // Simulate a prior partial migration that already wrote "SpringMVC" to
        // enabledFrameworks. The v4 branch must not lose it.
        val existingModules = mutableMapOf<String, MutableMap<String, String>>()
        existingModules[generalKey] = mutableMapOf(
            "enabledFrameworks" to GsonUtils.toJson(arrayOf("SpringMVC"))
        )
        appState.loadState(UnifiedAppSettingsState.State(modules = existingModules))

        setLegacyState(feignEnable = true)
        runMigration()
        assertMigrationCompleted()

        val enabled = enabledFrameworks()
        assertTrue(
            "Pre-existing SpringMVC entry must be preserved",
            "SpringMVC" in enabled
        )
        assertTrue(
            "New Feign entry must be added alongside the pre-existing entry",
            "Feign" in enabled
        )
    }
}
