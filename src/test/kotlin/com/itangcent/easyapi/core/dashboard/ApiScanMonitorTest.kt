package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.read
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.io.File

/**
 * Tests for [ApiScanMonitor] — the scan-health observer introduced by the
 * scan-performance work (see `.spec/api-scan-performance.md` §6).
 *
 * Focus is on the two externally visible contracts that are easy to get wrong:
 *
 * 1. `abortScan` actually cancels the active scan's indicator.
 * 2. `disableAutoScan` turns AUTO_SCANNING off **and persists it**.
 *
 * On (2): `FeatureSettingsTransaction.commit()` only mutates the settings object
 * handed to it — persistence requires a following `SettingBinder.save()`. This is
 * easy to drop and *looks* fine at runtime, because `SettingBinder.read()` is
 * backed by a TTL cache: within the TTL, re-reading returns the very instance
 * `commit()` just mutated, so a plain behavioral assertion passes **even with
 * `save()` missing**. This test therefore pairs the behavioral check with a
 * source-level tripwire asserting the `commit -> save -> publish` order
 * (same technique as `Jsr223ScriptParserBindingTest`).
 */
class ApiScanMonitorTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var monitor: ApiScanMonitor

    private val monitorSource =
        File("src/main/kotlin/com/itangcent/easyapi/core/dashboard/ApiScanMonitor.kt")

    override fun setUp() {
        super.setUp()
        monitor = ApiScanMonitor.getInstance(project)
    }

    fun testAbortScanCancelsActiveIndicator() {
        val indicator = EmptyProgressIndicator()
        monitor.onScanStart(indicator)
        try {
            assertFalse("Scan should not be cancelled before abort", indicator.isCanceled)

            monitor.abortScan()

            assertTrue("abortScan must cancel the active scan's indicator", indicator.isCanceled)
        } finally {
            monitor.onScanEnd()
        }
    }

    fun testDisableAutoScanTurnsOffAutoScanning() {
        monitor.disableAutoScan()

        val settings = settingBinder.read<GeneralSettings>()
        val states = FeatureSettingsTransaction(
            FeatureRegistry.getInstance(project).snapshot(),
            settings
        ).resolvedStates()

        assertFalse(
            "disableAutoScan must turn AUTO_SCANNING off",
            states.getValue(CoreFeatureIds.AUTO_SCANNING).desiredEnabled
        )
        assertFalse(
            "disableAutoScan must also clear the persisted autoScanEnabled flag",
            settings.autoScanEnabled
        )
    }

    fun testDisableAutoScanPersistsBeforePublishing() {
        assertTrue(
            "Test must run from project root; could not find ${monitorSource.path}",
            monitorSource.exists()
        )
        val body = monitorSource.readText()
            .substringAfter("fun disableAutoScan()")
            .substringBefore("console.warn(\"Automatic API scanning disabled")

        assertTrue(
            "disableAutoScan must commit a FeatureSettingsTransaction",
            body.contains("transaction.commit(settings)")
        )
        assertTrue(
            "disableAutoScan must persist via binder.save(settings) — commit() alone is in-memory only",
            body.contains("binder.save(settings)")
        )
        assertTrue(
            "disableAutoScan must publish the change so ApiScanLifecycleController reacts",
            body.contains("publishFeatureStateChange")
        )
        assertTrue(
            "save must run before publish: listeners read the persisted state",
            body.indexOf("binder.save(settings)") < body.indexOf("publishFeatureStateChange")
        )
    }

    fun testClassTokensAreUniqueAndEndIsIdempotent() {
        monitor.onScanStart(null)
        try {
            val first = monitor.onClassStart("com.example.FirstCtrl")
            val second = monitor.onClassStart("com.example.SecondCtrl")
            assertTrue("Each in-flight class must get its own token", first != second)

            monitor.onClassEnd(first)
            // Ending the same token twice (e.g. a nested finally) must not throw
            // nor double-count the class.
            monitor.onClassEnd(first)
            monitor.onClassEnd(second)
        } finally {
            monitor.onScanEnd()
        }
    }

    fun testScanEndWithoutStartDoesNotThrow() {
        monitor.onScanEnd()
    }

    fun testTimedOutClassesAreAggregatedIntoTheSummary() {
        monitor.onScanStart(null)
        try {
            monitor.onClassTimeout("com.example.SlowCtrl")
            // A class can time out once per exporter — count, do not dedupe.
            monitor.onClassTimeout("com.example.SlowCtrl")
            monitor.onClassTimeout("com.example.OtherCtrl")

            val summary = monitor.scanSummary(1_000L)

            assertTrue(
                "Timed-out classes must be aggregated into the scan summary, got: $summary",
                summary.contains("Timed out:")
            )
            assertTrue("summary must list SlowCtrl, got: $summary", summary.contains("com.example.SlowCtrl"))
            assertTrue("summary must list OtherCtrl, got: $summary", summary.contains("com.example.OtherCtrl"))
            assertTrue(
                "repeat timeouts for one class must be counted, got: $summary",
                summary.contains("com.example.SlowCtrlx2")
            )
        } finally {
            monitor.onScanEnd()
        }
    }

    fun testTimedOutClassesAreClearedBetweenScans() {
        monitor.onScanStart(null)
        monitor.onClassTimeout("com.example.StaleCtrl")
        monitor.onScanEnd()

        monitor.onScanStart(null)
        try {
            assertFalse(
                "A new scan must not inherit the previous scan's timeouts",
                monitor.scanSummary(0L).contains("com.example.StaleCtrl")
            )
        } finally {
            monitor.onScanEnd()
        }
    }

    fun testScanSummaryIsWrittenToIdeaLogNotOnlyTheConsole() {
        // The console defaults to logLevel=SILENT, so a console-only summary
        // makes the whole monitor invisible at default settings.
        val source = monitorSource.readText()
            .substringAfter("fun onScanEnd()")
            .substringBefore("fun onClassStart")

        assertTrue("onScanEnd must log the summary to idea.log", source.contains("LOG.info(summary)"))
        assertTrue("onScanEnd must still report to the console", source.contains("console.info(summary)"))
    }
}
