package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.feature.CoreFeatureContributor
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureContribution
import com.itangcent.easyapi.core.feature.FeatureContributionEntry
import com.itangcent.easyapi.core.feature.FeatureId
import com.itangcent.easyapi.core.feature.FeatureOptionDescriptor
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.LegacyFeatureContributor
import com.itangcent.easyapi.core.feature.TEST_FEATURE_GROUP
import com.itangcent.easyapi.core.feature.TEST_FEATURE_SOURCE
import com.itangcent.easyapi.core.feature.featureSnapshotOf
import com.itangcent.easyapi.core.feature.testFeatureDescriptor
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FeaturesSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: FeatureSettingsPanel
    private lateinit var transaction: FeatureSettingsTransaction

    override fun setUp() {
        super.setUp()
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        transaction = FeatureSettingsTransaction(snapshot, GeneralSettings())
        runSettingsUiTestOnEdt {
            panel = FeatureSettingsPanel(snapshot)
            panel.bindTransaction(transaction)
        }
    }

    fun testRendersCoreFeatureAndNestedOptionsFromSnapshot() {
        runSettingsUiTestOnEdt {
            assertTrue(
                "Core feature group should be rendered",
                panel.renderedGroupTitlesForTest().contains(CoreFeatureContributor.CORE_GROUP.displayName)
            )
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.API_SCANNING))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.AUTO_SCANNING))
            assertEquals(false, panel.desiredStateForTest(CoreFeatureIds.CONCURRENT_SCANNING))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.EDITOR_INTEGRATION))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.COPY_API_URL))
            assertEquals(true, panel.isControlEnabledForTest(CoreFeatureIds.COPY_API_URL))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.SEARCH_EVERYWHERE))
            assertEquals(true, panel.isControlEnabledForTest(CoreFeatureIds.SEARCH_EVERYWHERE))
        }
    }

    fun testParentDisablePreservesDesiredStatesAndDisablesDependents() {
        runSettingsUiTestOnEdt {
            panel.setDesiredStateForTest(CoreFeatureIds.CONCURRENT_SCANNING, true)
            panel.setDesiredStateForTest(CoreFeatureIds.API_SCANNING, false)

            assertEquals("Automatic scanning Desired state should be retained", true,
                panel.desiredStateForTest(CoreFeatureIds.AUTO_SCANNING))
            assertEquals("Concurrent scanning Desired state should be retained", true,
                panel.desiredStateForTest(CoreFeatureIds.CONCURRENT_SCANNING))
            assertEquals("Editor Desired state should be retained", true,
                panel.desiredStateForTest(CoreFeatureIds.EDITOR_INTEGRATION))
            assertEquals(false, panel.isControlEnabledForTest(CoreFeatureIds.AUTO_SCANNING))
            assertEquals(false, panel.isControlEnabledForTest(CoreFeatureIds.CONCURRENT_SCANNING))
            assertEquals(false, panel.isControlEnabledForTest(CoreFeatureIds.EDITOR_INTEGRATION))
            assertEquals(
                "Search Everywhere reads the retained index only, so turning scanning off must not " +
                    "disable its control",
                true,
                panel.isControlEnabledForTest(CoreFeatureIds.SEARCH_EVERYWHERE)
            )
            assertEquals(
                "Requires API Scanning to be enabled.",
                panel.dependencyTextForTest(CoreFeatureIds.EDITOR_INTEGRATION)
            )
            assertTrue("The transaction should contain the feature edits", transaction.isModified())
        }
    }

    fun testParentReenableRestoresDependentControlsWithoutChangingDesiredStates() {
        runSettingsUiTestOnEdt {
            panel.setDesiredStateForTest(CoreFeatureIds.CONCURRENT_SCANNING, true)
            panel.setDesiredStateForTest(CoreFeatureIds.API_SCANNING, false)
            panel.setDesiredStateForTest(CoreFeatureIds.API_SCANNING, true)

            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.AUTO_SCANNING))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.CONCURRENT_SCANNING))
            assertEquals(true, panel.desiredStateForTest(CoreFeatureIds.EDITOR_INTEGRATION))
            assertEquals(true, panel.isControlEnabledForTest(CoreFeatureIds.AUTO_SCANNING))
            assertEquals(true, panel.isControlEnabledForTest(CoreFeatureIds.CONCURRENT_SCANNING))
            assertEquals(true, panel.isControlEnabledForTest(CoreFeatureIds.EDITOR_INTEGRATION))
            assertNull(panel.dependencyTextForTest(CoreFeatureIds.EDITOR_INTEGRATION))
        }
    }

    fun testEmptyDeclaredLegacyGroupsDoNotCreateSections() {
        val snapshot = FeatureRegistry.buildSnapshot(
            listOf(
                FeatureContributionEntry(
                    "empty",
                    FeatureContribution(
                        groups = listOf(
                            LegacyFeatureContributor.FRAMEWORKS_GROUP,
                            LegacyFeatureContributor.EXPORT_CHANNELS_GROUP,
                            LegacyFeatureContributor.FIELD_FORMAT_CHANNELS_GROUP
                        )
                    )
                )
            )
        )
        runSettingsUiTestOnEdt {
            val emptyPanel = FeatureSettingsPanel(snapshot)
            emptyPanel.bindTransaction(FeatureSettingsTransaction(snapshot, GeneralSettings()))
            assertTrue("Empty groups should not be rendered", emptyPanel.renderedGroupTitlesForTest().isEmpty())
        }
    }

    fun testCoreFeatureControlsExposeDescriptionsAsTooltips() {
        runSettingsUiTestOnEdt {
            assertEquals(
                "API Scanning tooltip should come from the descriptor description",
                "Scan source code to discover and collect API endpoints.",
                panel.toolTipTextForTest(CoreFeatureIds.API_SCANNING)
            )
            assertEquals(
                "Automatic API Scanning tooltip should come from the nested option description",
                "Automatically scan APIs when the project opens or source files change.",
                panel.toolTipTextForTest(CoreFeatureIds.AUTO_SCANNING)
            )
            assertEquals(
                "Concurrent API Scanning tooltip should come from the nested option description",
                "Scan APIs in parallel across modules for faster performance. Disable if you experience indexing slowdowns.",
                panel.toolTipTextForTest(CoreFeatureIds.CONCURRENT_SCANNING)
            )
            assertEquals(
                "Editor Integration tooltip should come from the descriptor description",
                "Show gutter icons and line markers next to API methods in the editor.",
                panel.toolTipTextForTest(CoreFeatureIds.EDITOR_INTEGRATION)
            )
            assertEquals(
                "Search Everywhere tooltip should come from the descriptor description",
                "List API endpoints in IntelliJ's Search Everywhere, under their own APIs tab.",
                panel.toolTipTextForTest(CoreFeatureIds.SEARCH_EVERYWHERE)
            )
        }
    }

    fun testDescriptorDescriptionIsRenderedAsCheckboxTooltip() {
        val nested = FeatureOptionDescriptor(
            id = FeatureId("test-parent/with-description-nested"),
            displayName = "Nested With Description",
            defaultEnabled = true,
            dependencyIds = emptyList(),
            stateBridge = com.itangcent.easyapi.core.feature.DirectBooleanStateBridge(
                com.itangcent.easyapi.core.feature.DirectBooleanSetting.API_SCAN_ENABLED
            ),
            source = TEST_FEATURE_SOURCE,
            description = "Nested tooltip text."
        )
        val snapshot = featureSnapshotOf(
            testFeatureDescriptor(
                id = "with-description",
                description = "Top-level tooltip text.",
                nestedOptions = listOf(nested)
            )
        )
        runSettingsUiTestOnEdt {
            val tooltipPanel = FeatureSettingsPanel(snapshot)
            tooltipPanel.bindTransaction(FeatureSettingsTransaction(snapshot, GeneralSettings()))
            assertEquals(
                "Top-level control tooltip should match descriptor.description",
                "Top-level tooltip text.",
                tooltipPanel.toolTipTextForTest(FeatureId("with-description"))
            )
            assertEquals(
                "Nested control tooltip should match option description",
                "Nested tooltip text.",
                tooltipPanel.toolTipTextForTest(FeatureId("test-parent/with-description-nested"))
            )
        }
    }

    fun testBlankDescriptionProducesNoTooltip() {
        val snapshot = featureSnapshotOf(
            testFeatureDescriptor(id = "no-description", description = "")
        )
        runSettingsUiTestOnEdt {
            val tooltipPanel = FeatureSettingsPanel(snapshot)
            tooltipPanel.bindTransaction(FeatureSettingsTransaction(snapshot, GeneralSettings()))
            assertNull(
                "Blank description should not produce a tooltip",
                tooltipPanel.toolTipTextForTest(FeatureId("no-description"))
            )
        }
    }
}
