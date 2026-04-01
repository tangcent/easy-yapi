package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class PostmanExportMetadataTest {

    @Test
    fun testConstruction() {
        val metadata = PostmanExportMetadata(
            workspaceName = "My Workspace",
            workspaceId = "ws-123",
            collectionName = "My Collection",
            collectionId = "col-456"
        )
        
        assertEquals("My Workspace", metadata.workspaceName)
        assertEquals("ws-123", metadata.workspaceId)
        assertEquals("My Collection", metadata.collectionName)
        assertEquals("col-456", metadata.collectionId)
        assertNull(metadata.collectionData)
    }

    @Test
    fun testConstructionWithDefaults() {
        val metadata = PostmanExportMetadata()
        
        assertNull(metadata.workspaceName)
        assertNull(metadata.workspaceId)
        assertNull(metadata.collectionName)
        assertNull(metadata.collectionId)
        assertNull(metadata.collectionData)
    }

    @Test
    fun testFormatDisplayWithWorkspaceAndCollectionNames() {
        val metadata = PostmanExportMetadata(
            workspaceName = "My Workspace",
            collectionName = "My Collection"
        )
        
        assertEquals("My Workspace/My Collection", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithWorkspaceNameOnly() {
        val metadata = PostmanExportMetadata(workspaceName = "My Workspace")
        assertEquals("My Workspace", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithCollectionNameOnly() {
        val metadata = PostmanExportMetadata(collectionName = "My Collection")
        assertEquals("My Collection", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithWorkspaceIdFallback() {
        val metadata = PostmanExportMetadata(workspaceId = "ws-123")
        assertEquals("ws-123", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithCollectionIdFallback() {
        val metadata = PostmanExportMetadata(collectionId = "col-456")
        assertEquals("col-456", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithNullData() {
        val metadata = PostmanExportMetadata()
        assertEquals("", metadata.formatDisplay())
    }

    @Test
    fun testCopy() {
        val metadata = PostmanExportMetadata(workspaceName = "Test")
        val copy = metadata.copy(collectionName = "New Collection")
        
        assertEquals("Test", copy.workspaceName)
        assertEquals("New Collection", copy.collectionName)
    }

    @Test
    fun testEquality() {
        val metadata1 = PostmanExportMetadata(
            workspaceName = "Workspace",
            collectionName = "Collection"
        )
        val metadata2 = PostmanExportMetadata(
            workspaceName = "Workspace",
            collectionName = "Collection"
        )
        
        assertEquals(metadata1, metadata2)
    }
}
