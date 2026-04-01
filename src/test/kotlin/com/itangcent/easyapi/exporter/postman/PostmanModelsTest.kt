package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class PostmanCollectionInfoTest {

    @Test
    fun testConstruction() {
        val info = PostmanCollectionInfo(
            id = "col-123",
            name = "My Collection",
            uid = "user-col-123"
        )
        
        assertEquals("col-123", info.id)
        assertEquals("My Collection", info.name)
        assertEquals("user-col-123", info.uid)
    }

    @Test
    fun testConstructionWithDefaults() {
        val info = PostmanCollectionInfo(
            id = "col-456",
            name = "Test Collection"
        )
        
        assertEquals("col-456", info.id)
        assertEquals("Test Collection", info.name)
        assertNull(info.uid)
    }

    @Test
    fun testCopy() {
        val info = PostmanCollectionInfo(
            id = "col-789",
            name = "Original"
        )
        
        val copy = info.copy(name = "Updated")
        assertEquals("col-789", copy.id)
        assertEquals("Updated", copy.name)
    }

    @Test
    fun testEquality() {
        val info1 = PostmanCollectionInfo(
            id = "col-123",
            name = "Collection",
            uid = "uid-123"
        )
        val info2 = PostmanCollectionInfo(
            id = "col-123",
            name = "Collection",
            uid = "uid-123"
        )
        
        assertEquals(info1, info2)
    }
}

class WorkspaceTest {

    @Test
    fun testConstruction() {
        val workspace = Workspace(
            id = "ws-123",
            name = "My Workspace"
        )
        
        assertEquals("ws-123", workspace.id)
        assertEquals("My Workspace", workspace.name)
    }

    @Test
    fun testCopy() {
        val workspace = Workspace(id = "ws-456", name = "Original")
        val copy = workspace.copy(name = "Updated")
        
        assertEquals("ws-456", copy.id)
        assertEquals("Updated", copy.name)
    }

    @Test
    fun testEquality() {
        val workspace1 = Workspace(id = "ws-123", name = "Workspace")
        val workspace2 = Workspace(id = "ws-123", name = "Workspace")
        
        assertEquals(workspace1, workspace2)
    }
}
