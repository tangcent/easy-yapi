package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project

/** Stable ids for built-in feature capabilities. */
object CoreFeatureIds {
    val API_SCANNING = FeatureId("core.api-scanning")
    val AUTO_SCANNING = FeatureId("core.api-scanning.auto")
    val CONCURRENT_SCANNING = FeatureId("core.api-scanning.concurrent")
    val EDITOR_INTEGRATION = FeatureId("core.editor-integration")
    val COPY_API_URL = FeatureId("core.copy-api-url")
    val SEARCH_EVERYWHERE = FeatureId("core.search-everywhere")
}

/** Declares built-in scanning and editor capabilities. */
class CoreFeatureContributor : FeatureContributor {
    override val sourceId: String = SOURCE.id

    override fun contribute(project: Project): FeatureContribution = contribution()

    /** Returns the project-independent built-in contribution. */
    fun contribution(): FeatureContribution {
        val autoScanning = FeatureOptionDescriptor(
            id = CoreFeatureIds.AUTO_SCANNING,
            displayName = "Automatic API Scanning",
            defaultEnabled = true,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.AUTO_SCAN_ENABLED),
            source = SOURCE,
            description = "Automatically scan APIs when the project opens or source files change."
        )
        val concurrentScanning = FeatureOptionDescriptor(
            id = CoreFeatureIds.CONCURRENT_SCANNING,
            displayName = "Concurrent API Scanning",
            defaultEnabled = false,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.CONCURRENT_SCAN_ENABLED),
            source = SOURCE,
            description = "Scan APIs in parallel across modules for faster performance. Disable if you experience indexing slowdowns."
        )
        val apiScanning = FeatureDescriptor(
            id = CoreFeatureIds.API_SCANNING,
            displayName = "API Scanning",
            defaultEnabled = true,
            group = CORE_GROUP,
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED),
            nestedOptions = listOf(autoScanning, concurrentScanning),
            source = SOURCE,
            description = "Scan source code to discover and collect API endpoints."
        )
        val editorIntegration = FeatureDescriptor(
            id = CoreFeatureIds.EDITOR_INTEGRATION,
            displayName = "Editor Integration",
            defaultEnabled = true,
            group = CORE_GROUP,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.GUTTER_ICON_ENABLED),
            source = SOURCE,
            description = "Show gutter icons and line markers next to API methods in the editor."
        )
        // Declares no dependency on API_SCANNING: the action resolves the
        // selection through ApiScanner.scanSelection, which scans on demand
        // exactly like the export actions do. API_SCANNING only gates
        // background scanning, so tying this entry to it would hide a feature
        // that still works.
        val copyApiUrl = FeatureDescriptor(
            id = CoreFeatureIds.COPY_API_URL,
            displayName = "Copy API URL",
            defaultEnabled = true,
            group = CORE_GROUP,
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.COPY_API_URL_ENABLED),
            source = SOURCE,
            description = "Add the Copy API URL action to the EasyApi context menu, copying the address of every endpoint in the selection."
        )
        // Declares no dependency on API_SCANNING either, even though it reads the
        // same ApiIndex as editorIntegration. It only reads: fetchElements goes
        // straight to ApiIndex.endpoints() and never passes the scan admission
        // checks that reject gutter requests. The index is not cleared when
        // scanning stops (ApiIndex.invalidate has no production callers), and the
        // Dashboard's Refresh runs an isolated one-shot scan that refills it while
        // scanning is off - so the tab keeps returning endpoints for the rest of
        // the session. Tying it to API_SCANNING would grey out a surface that
        // still works.
        val searchEverywhere = FeatureDescriptor(
            id = CoreFeatureIds.SEARCH_EVERYWHERE,
            displayName = "Search Everywhere",
            defaultEnabled = true,
            group = CORE_GROUP,
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.SEARCH_EVERYWHERE_ENABLED),
            source = SOURCE,
            description = "List API endpoints in IntelliJ's Search Everywhere, under their own APIs tab."
        )
        return FeatureContribution(
            groups = listOf(CORE_GROUP),
            descriptors = listOf(apiScanning, editorIntegration, copyApiUrl, searchEverywhere)
        )
    }

    companion object {
        val CORE_GROUP = FeatureGroup("core-api", "API Features", 0)
        val SOURCE = FeatureSource("core")
    }
}
