package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FeatureSettingsTransactionTest {

    @Test
    fun testDraftIsIsolatedAndCommitReturnsOnlyRealDeltas() {
        val snapshot = FeatureRegistry.buildSnapshot(
            listOf(
                FeatureContributionEntry("core", CoreFeatureContributor().contribution())
            )
        )
        val settings = GeneralSettings()
        val before = settings.deepCopy()
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        transaction.setDesiredState(CoreFeatureIds.API_SCANNING, false)
        transaction.setDesiredState(CoreFeatureIds.CONCURRENT_SCANNING, true)

        assertEquals("Draft changes must not mutate persisted settings", before, settings)
        assertTrue("Transaction should report desired-state edits", transaction.isModified())
        assertTrue(
            "Nested desired state should remain enabled while its parent is disabled",
            transaction.resolvedStates().getValue(CoreFeatureIds.AUTO_SCANNING).desiredEnabled
        )
        assertFalse(
            "Nested effective state should follow its parent",
            transaction.resolvedStates().getValue(CoreFeatureIds.AUTO_SCANNING).effectiveEnabled
        )

        val change = transaction.commit(settings)

        assertNotNull("A changed transaction should return a typed change", change)
        assertEquals("Scanning desired state should be committed", false, settings.apiScanEnabled)
        assertEquals("Concurrent desired state should be committed", true, settings.concurrentScanEnabled)
        assertEquals("Unedited automatic scanning should be preserved", true, settings.autoScanEnabled)
        assertEquals("Unedited editor integration should be preserved", true, settings.gutterIconEnabled)
        assertEquals(
            "Desired and effective deltas should include every affected identity. " +
                "Copy API URL and Search Everywhere are independent of API scanning, so they stay out.",
            setOf(
                CoreFeatureIds.API_SCANNING,
                CoreFeatureIds.AUTO_SCANNING,
                CoreFeatureIds.CONCURRENT_SCANNING,
                CoreFeatureIds.EDITOR_INTEGRATION
            ),
            change!!.entries.mapTo(linkedSetOf()) { it.id }
        )
        assertEquals(
            "Commit should not mark the draft as persisted before the caller saves it",
            true,
            transaction.isModified()
        )
    }

    @Test
    fun testDiscardedDraftDoesNotMutateSettingsOrCreateChange() {
        val snapshot = FeatureRegistry.buildSnapshot(
            listOf(FeatureContributionEntry("core", CoreFeatureContributor().contribution()))
        )
        val settings = GeneralSettings()
        val before = settings.deepCopy()
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        transaction.setDesiredState(CoreFeatureIds.API_SCANNING, false)

        assertEquals("Discarding the transaction should leave settings unchanged", before, settings)
        assertTrue("The isolated draft should still report its edit", transaction.isModified())
    }

    @Test
    fun testUnmodifiedCommitIsANoOp() {
        val snapshot = FeatureRegistry.buildSnapshot(
            listOf(FeatureContributionEntry("core", CoreFeatureContributor().contribution()))
        )
        val settings = GeneralSettings()
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        val change = transaction.commit(settings)

        assertNull("An unmodified transaction should not return an event", change)
        assertFalse("An untouched transaction should not be modified", transaction.isModified())
    }

    @Test
    fun testBridgeFailureLeavesSettingsUntouched() {
        val direct = testFeatureDescriptor(
            id = "direct",
            bridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED)
        )
        val failingBridge = object : FeatureStateBridge {
            override fun readDesired(settings: GeneralSettings, defaultEnabled: Boolean): Boolean = false

            override fun stageWrite(
                desired: Boolean,
                descriptor: FeatureStateIdentity,
                batch: FeatureStateWriteBatch
            ) {
                throw IllegalStateException("Rejected staged value")
            }
        }
        val failing = testFeatureDescriptor(
            id = "failing",
            defaultEnabled = false,
            bridge = failingBridge
        )
        val snapshot = featureSnapshotOf(direct, failing)
        val settings = GeneralSettings(apiScanEnabled = true)
        val before = settings.deepCopy()
        val transaction = FeatureSettingsTransaction(snapshot, settings)
        transaction.setDesiredState(direct.id, false)
        transaction.setDesiredState(failing.id, true)

        try {
            transaction.commit(settings)
            fail("Commit should propagate a bridge validation failure")
        } catch (expected: IllegalStateException) {
            assertEquals("Failure should be the bridge rejection", "Rejected staged value", expected.message)
        }

        assertEquals("A failed staging phase must not mutate any setting", before, settings)
    }

    @Test
    fun testStateChangeExcludesReasonOnlyDifferences() {
        val id = FeatureId("feature")
        val before = ResolvedFeatureState(id, true, false, MissingDependency(FeatureId("missing")))
        val after = ResolvedFeatureState(id, true, false, DisabledByDependency(FeatureId("parent")))

        val change = FeatureStateChange.between(
            FeatureStateChangeSource.SETTINGS_APPLY,
            mapOf(id to before),
            mapOf(id to after)
        )

        assertTrue("Reason-only changes should not produce runtime deltas", change.entries.isEmpty())
    }
}
