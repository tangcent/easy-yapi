package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class PostmanExportOptionsTest {

    @Test
    fun testPostmanExportOptionsCreation() {
        val options = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedWorkspaceName = "My Workspace",
            selectedCollectionId = "col-456",
            selectedCollectionName = "My Collection",
            useCustomCollection = true
        )
        assertEquals("ws-123", options.selectedWorkspaceId)
        assertEquals("My Workspace", options.selectedWorkspaceName)
        assertEquals("col-456", options.selectedCollectionId)
        assertEquals("My Collection", options.selectedCollectionName)
        assertTrue(options.useCustomCollection)
    }

    @Test
    fun testPostmanExportOptionsWithDefaults() {
        val options = PostmanExportOptions()
        assertNull(options.selectedWorkspaceId)
        assertNull(options.selectedWorkspaceName)
        assertNull(options.selectedCollectionId)
        assertNull(options.selectedCollectionName)
        assertFalse(options.useCustomCollection)
    }

    @Test
    fun testPostmanExportOptionsWithOnlyWorkspace() {
        val options = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedWorkspaceName = "My Workspace"
        )
        assertEquals("ws-123", options.selectedWorkspaceId)
        assertEquals("My Workspace", options.selectedWorkspaceName)
        assertNull(options.selectedCollectionId)
    }

    @Test
    fun testPostmanExportOptionsWithOnlyCollection() {
        val options = PostmanExportOptions(
            selectedCollectionId = "col-456",
            selectedCollectionName = "My Collection"
        )
        assertNull(options.selectedWorkspaceId)
        assertEquals("col-456", options.selectedCollectionId)
        assertEquals("My Collection", options.selectedCollectionName)
    }

    @Test
    fun testPostmanExportOptionsEquality() {
        val options1 = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedCollectionId = "col-456"
        )
        val options2 = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedCollectionId = "col-456"
        )
        assertEquals(options1, options2)
    }

    @Test
    fun testPostmanExportOptionsCopy() {
        val original = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedCollectionId = "col-456"
        )
        val copy = original.copy(selectedWorkspaceId = "ws-789")
        assertEquals("ws-789", copy.selectedWorkspaceId)
        assertEquals("ws-123", original.selectedWorkspaceId)
    }

    @Test
    fun testPostmanExportOptionsComponentFunctions() {
        val options = PostmanExportOptions(
            selectedWorkspaceId = "ws-123",
            selectedWorkspaceName = "Workspace",
            selectedCollectionId = "col-456",
            selectedCollectionName = "Collection",
            useCustomCollection = true
        )
        val (wsId, wsName, colId, colName, useCustom) = options
        assertEquals("ws-123", wsId)
        assertEquals("Workspace", wsName)
        assertEquals("col-456", colId)
        assertEquals("Collection", colName)
        assertTrue(useCustom)
    }
}
