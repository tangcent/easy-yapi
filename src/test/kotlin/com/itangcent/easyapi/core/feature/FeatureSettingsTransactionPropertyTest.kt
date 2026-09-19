package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class FeatureSettingsTransactionPropertyTest {

    @Test
    fun propertyDraftIsolationAndExactCommit(): Unit = runBlocking {
        checkAll(iterations = 150, Arb.int()) { seed ->
            val random = Random(seed)
            val snapshot = FeatureRegistry.buildSnapshot(
                listOf(FeatureContributionEntry("core", CoreFeatureContributor().contribution()))
            )
            val settings = GeneralSettings(
                apiScanEnabled = random.nextBoolean(),
                autoScanEnabled = random.nextBoolean(),
                concurrentScanEnabled = random.nextBoolean(),
                gutterIconEnabled = random.nextBoolean(),
                copyApiUrlEnabled = random.nextBoolean(),
                switchNotice = random.nextBoolean(),
                logLevel = random.nextInt(),
                outputCharset = "charset-$seed",
                enabledChannels = arrayOf("unknown-enabled-$seed"),
                disabledChannels = arrayOf("unknown-disabled-$seed")
            )
            val before = settings.deepCopy()
            val transaction = FeatureSettingsTransaction(snapshot, settings)
            val expected = transaction.initialDesiredStates.toMutableMap()
            // One bit per identity, so every core feature stays reachable by the
            // edit mask as new identities are added.
            val editMask = random.nextInt(0, 1 shl snapshot.stateIdentities.size)

            snapshot.stateIdentities.forEachIndexed { index, identity ->
                if (editMask and (1 shl index) != 0) {
                    val draft = random.nextBoolean()
                    transaction.setDesiredState(identity.id, draft)
                    expected[identity.id] = draft
                }
            }

            assertEquals("Draft must not mutate settings for seed=$seed", before, settings)
            val observedEvents = mutableListOf<FeatureStateChange>()
            assertEquals("Draft editing must not publish events for seed=$seed", emptyList<FeatureStateChange>(), observedEvents)

            val change = transaction.commit(settings)

            snapshot.stateIdentities.forEach { identity ->
                assertEquals(
                    "Committed desired state should match the draft for ${identity.id} seed=$seed",
                    expected.getValue(identity.id),
                    identity.stateBridge.readDesired(settings, identity.defaultEnabled)
                )
            }
            assertEquals("Unrelated switch setting should remain unchanged for seed=$seed", before.switchNotice, settings.switchNotice)
            assertEquals("Unrelated log setting should remain unchanged for seed=$seed", before.logLevel, settings.logLevel)
            assertEquals("Unrelated charset should remain unchanged for seed=$seed", before.outputCharset, settings.outputCharset)
            assertEquals("Unrelated enabled array should remain unchanged for seed=$seed", before.enabledChannels.toList(), settings.enabledChannels.toList())
            assertEquals("Unrelated disabled array should remain unchanged for seed=$seed", before.disabledChannels.toList(), settings.disabledChannels.toList())
            assertEquals("Commit must not publish its returned event for seed=$seed", emptyList<FeatureStateChange>(), observedEvents)
            if (!transaction.isModified()) {
                assertNull("No-op drafts should not produce a change for seed=$seed", change)
            }
        }
    }
}
