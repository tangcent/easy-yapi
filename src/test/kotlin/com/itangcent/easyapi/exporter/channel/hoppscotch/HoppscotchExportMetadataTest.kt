package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import org.junit.Assert.*
import org.junit.Test

class HoppscotchExportMetadataTest {

    @Test
    fun testFormatDisplayWithWorkspaceAndCollectionNames() {
        val metadata = HoppscotchExportMetadata(
            workspaceName = "My Workspace",
            collectionName = "My Collection"
        )
        assertEquals("My Workspace/My Collection", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithWorkspaceNameOnly() {
        val metadata = HoppscotchExportMetadata(workspaceName = "My Workspace")
        assertEquals("My Workspace", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithCollectionNameOnly() {
        val metadata = HoppscotchExportMetadata(collectionName = "My Collection")
        assertEquals("My Collection", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithCollectionIdFallback() {
        val metadata = HoppscotchExportMetadata(collectionId = "col-456")
        assertEquals("col-456", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayWithNullData() {
        val metadata = HoppscotchExportMetadata()
        assertEquals("", metadata.formatDisplay())
    }

    @Test
    fun testFormatDisplayReturnsNullWhenCollectionDataPresent() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "Test",
            collectionData = HoppCollection(name = "Test")
        )
        assertNull(metadata.formatDisplay())
    }

    @Test
    fun testCopy() {
        val metadata = HoppscotchExportMetadata(workspaceName = "Test")
        val copy = metadata.copy(collectionName = "New Collection")
        assertEquals("Test", copy.workspaceName)
        assertEquals("New Collection", copy.collectionName)
    }
}
