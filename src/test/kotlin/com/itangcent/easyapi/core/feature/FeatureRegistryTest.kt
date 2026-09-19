package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FeatureRegistryTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testProjectExtensionPointDiscoversContributorsInDeclaredOrder() {
        val extensionPoint = project.extensionArea
            .getExtensionPoint<FeatureContributor>("com.itangcent.idea.plugin.easy-yapi.featureContributor")
        val contributors = extensionPoint.extensionList

        assertEquals("Exactly two built-in contributors should be registered", 2, contributors.size)
        assertTrue("Core contributor should be first", contributors[0] is CoreFeatureContributor)
        assertTrue("Legacy contributor should be second", contributors[1] is LegacyFeatureContributor)

        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        assertEquals(
            "Core descriptors should precede legacy descriptors",
            listOf(CoreFeatureIds.API_SCANNING, CoreFeatureIds.EDITOR_INTEGRATION),
            snapshot.descriptors.take(2).map { it.id }
        )
    }

    fun testCopyApiUrlFeatureIsDeclaredWithoutDependencies() {
        val contribution = CoreFeatureContributor().contribution()
        val descriptor = contribution.descriptors.single { it.id == CoreFeatureIds.COPY_API_URL }

        assertEquals("Copy API URL", descriptor.displayName)
        assertTrue("Copy API URL should be enabled by default", descriptor.defaultEnabled)
        assertEquals(CoreFeatureContributor.CORE_GROUP, descriptor.group)
        assertTrue(
            "Copy API URL scans on demand like the export actions, so it must not depend on API scanning",
            descriptor.dependencyIds.isEmpty()
        )
        assertEquals(
            DirectBooleanSetting.COPY_API_URL_ENABLED,
            (descriptor.stateBridge as DirectBooleanStateBridge).setting
        )
    }

    fun testDiscoveredLegacyDescriptorsPreserveProductionMetadata() {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()

        ChannelRegistry.getInstance(project).allChannels().forEach { channel ->
            assertLegacyDescriptor(
                snapshot.descriptor(FeatureId("channel/${channel.id}")),
                channel.displayName,
                channel.enabledByDefault,
                LegacyOverrideArrayGroup.CHANNELS,
                channel.id
            )
        }
        FieldFormatChannelRegistry.getInstance(project).allChannels().forEach { format ->
            assertLegacyDescriptor(
                snapshot.descriptor(FeatureId("field-format/${format.id}")),
                format.displayName,
                format.enabledByDefault,
                LegacyOverrideArrayGroup.FIELD_FORMAT_CHANNELS,
                format.id
            )
        }
        CompositeApiClassRecognizer.getInstance(project).allRecognizers().forEach { recognizer ->
            assertLegacyDescriptor(
                snapshot.descriptor(FeatureId("framework/${recognizer.frameworkName}")),
                recognizer.frameworkName,
                recognizer.enabledByDefault,
                LegacyOverrideArrayGroup.FRAMEWORKS,
                recognizer.frameworkName
            )
        }
    }

    fun testSnapshotRetainsFirstDuplicateAndReportsBothSources() {
        val firstBridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED)
        val first = testFeatureDescriptor("duplicate", bridge = firstBridge)
        val ignored = testFeatureDescriptor(
            "duplicate",
            bridge = DirectBooleanStateBridge(DirectBooleanSetting.AUTO_SCAN_ENABLED),
            group = FeatureGroup("empty", "Empty", 1),
            source = FeatureSource("second")
        )
        val diagnostics = mutableListOf<Triple<FeatureId, String, String>>()

        val snapshot = FeatureRegistry.buildSnapshot(
            entries = listOf(
                FeatureContributionEntry("first", FeatureContribution(descriptors = listOf(first))),
                FeatureContributionEntry("second", FeatureContribution(descriptors = listOf(ignored)))
            ),
            onDuplicate = { id, retained, discarded ->
                diagnostics += Triple(id, retained, discarded)
            }
        )

        assertEquals(listOf(first.id), snapshot.descriptors.map { it.id })
        assertSame("The first bridge should be retained", firstBridge, snapshot.descriptors.single().stateBridge)
        assertEquals(
            "Duplicate diagnostics should identify retained and ignored sources",
            listOf(Triple(first.id, "first", "second")),
            diagnostics
        )
        assertFalse("The ignored descriptor's empty group should not remain", snapshot.groups.contains(ignored.group))
    }

    fun testMissingDependencyIsReportedAndResolvedAsDisabled() {
        val missingId = FeatureId("missing")
        val descriptor = testFeatureDescriptor("dependent", dependencies = listOf(missingId))
        val diagnostics = mutableListOf<Pair<FeatureId, FeatureId>>()
        val snapshot = FeatureRegistry.buildSnapshot(
            entries = listOf(
                FeatureContributionEntry("source", FeatureContribution(descriptors = listOf(descriptor)))
            ),
            onMissingDependency = { id, dependencyId -> diagnostics += id to dependencyId }
        )

        val state = FeatureStateResolver().resolve(snapshot, mapOf(descriptor.id to true)).getValue(descriptor.id)
        assertEquals(listOf(descriptor.id to missingId), diagnostics)
        assertTrue("Desired state should be preserved", state.desiredEnabled)
        assertFalse("A missing dependency should disable Effective state", state.effectiveEnabled)
        assertEquals(MissingDependency(missingId), state.reason)
    }

    fun testLegacyContributorKeepsRawIdsWhenCategoriesShareAnId() {
        val contribution = LegacyFeatureContributor().createContribution(
            channels = listOf(StubChannel("shared", "Export Shared", false)),
            fieldFormatChannels = listOf(StubFormat("shared", "Format Shared", true)),
            recognizers = listOf(StubRecognizer("shared", false))
        )
        val descriptors = contribution.descriptors.associateBy { it.id }

        assertEquals(3, descriptors.size)
        assertLegacyDescriptor(
            descriptors[FeatureId("channel/shared")],
            "Export Shared",
            false,
            LegacyOverrideArrayGroup.CHANNELS,
            "shared"
        )
        assertLegacyDescriptor(
            descriptors[FeatureId("field-format/shared")],
            "Format Shared",
            true,
            LegacyOverrideArrayGroup.FIELD_FORMAT_CHANNELS,
            "shared"
        )
        assertLegacyDescriptor(
            descriptors[FeatureId("framework/shared")],
            "shared",
            false,
            LegacyOverrideArrayGroup.FRAMEWORKS,
            "shared"
        )
    }

    private fun assertLegacyDescriptor(
        descriptor: FeatureDescriptor?,
        displayName: String,
        defaultEnabled: Boolean,
        group: LegacyOverrideArrayGroup,
        rawId: String
    ) {
        assertNotNull("Expected legacy descriptor for '$rawId'", descriptor)
        val retained = descriptor!!
        val bridge = retained.stateBridge as LegacyOverrideArrayStateBridge
        assertEquals(displayName, retained.displayName)
        assertEquals(defaultEnabled, retained.defaultEnabled)
        assertEquals(group, bridge.group)
        assertEquals(rawId, bridge.rawLegacyId)
    }

    private class StubChannel(
        override val id: String,
        override val displayName: String,
        override val enabledByDefault: Boolean
    ) : Channel {
        override suspend fun export(context: ExportContext): ExportResult = ExportResult.Success(0, "")
    }

    private class StubFormat(
        override val id: String,
        override val displayName: String,
        override val enabledByDefault: Boolean
    ) : FieldFormatChannel {
        override val actionText: String = "To$displayName"
        override suspend fun format(project: Project, psiClass: PsiClass): String = ""
    }

    private class StubRecognizer(
        override val frameworkName: String,
        override val enabledByDefault: Boolean
    ) : ApiClassRecognizer {
        override val targetAnnotations: Set<String> = emptySet()
        override suspend fun isApiClass(psiClass: PsiClass): Boolean = false
    }
}
