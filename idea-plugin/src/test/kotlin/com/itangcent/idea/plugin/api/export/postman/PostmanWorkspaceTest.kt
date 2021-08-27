package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.idea.plugin.api.export.postman.Emojis.PERSONAL
import com.itangcent.idea.plugin.api.export.postman.Emojis.TEAM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Test case of [PostmanWorkspace]
 */
internal class PostmanWorkspaceTest {

    @Test
    fun nameWithType() {
        val postmanWorkspace = PostmanWorkspace("001", null, null)
        assertNull(postmanWorkspace.nameWithType())
        postmanWorkspace.name = "My Workspace"
        assertEquals("My Workspace", postmanWorkspace.nameWithType())
        postmanWorkspace.type = "team"
        assertEquals("${TEAM}My Workspace", postmanWorkspace.nameWithType())
        postmanWorkspace.type = "personal"
        assertEquals("${PERSONAL}My Workspace", postmanWorkspace.nameWithType())
    }
}