package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [PostmanSettingsHelper]
 */
internal class PostmanSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        val messagesHelper = Mockito.mock(MessagesHelper::class.java)
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
                Mockito.eq("Postman Private Token"), Mockito.any()))
                .thenReturn(null, "123")
        builder.bindInstance(MessagesHelper::class, messagesHelper)

        val postmanWorkspaceChecker = object : PostmanWorkspaceChecker {
            override fun checkWorkspace(workspace: String): Boolean {
                return true
            }
        }
        builder.bindInstance(PostmanWorkspaceChecker::class, postmanWorkspaceChecker)
        builder.bindInstance(PostmanApiHelper::class, PostmanCachedApiHelper())
    }

    @Test
    fun testHasPrivateToken() {
        assertFalse(postmanSettingsHelper.hasPrivateToken())
        settings.postmanToken = "123"
        assertTrue(postmanSettingsHelper.hasPrivateToken())
    }

    @Test
    fun testGetPrivateToken() {
        assertNull(postmanSettingsHelper.getPrivateToken())
        assertNull(settings.postmanToken)
        assertNull(postmanSettingsHelper.getPrivateToken(false))
        assertEquals("123", postmanSettingsHelper.getPrivateToken(false))
        assertEquals("123", settings.postmanToken)
        assertEquals("123", postmanSettingsHelper.getPrivateToken())
    }

    @Test
    fun testWrapCollection() {
        settings.wrapCollection = false
        assertFalse(postmanSettingsHelper.wrapCollection())
        settings.wrapCollection = true
        assertTrue(postmanSettingsHelper.wrapCollection())
    }

    @Test
    fun testAutoMergeScript() {
        settings.autoMergeScript = false
        assertFalse(postmanSettingsHelper.autoMergeScript())
        settings.autoMergeScript = true
        assertTrue(postmanSettingsHelper.autoMergeScript())
    }

    @Test
    fun testPostmanJson5FormatType() {
        for (formatType in PostmanJson5FormatType.values()) {
            settings.postmanJson5FormatType = formatType.name
            assertEquals(formatType, postmanSettingsHelper.postmanJson5FormatType())
        }
    }

    @Test
    fun testGetWorkspace() {
        val properties = Properties()
        properties["demo"] = "123456789"
        properties["workspace-demo"] = "7788"

        settings.postmanWorkspaces = ByteArrayOutputStream().also { properties.store(it, "") }.toString()

        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo"))
        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo", false))
        assertEquals("7788", postmanSettingsHelper.getWorkspace("workspace-demo", false))
        assertEquals("7788", postmanSettingsHelper.getWorkspace("workspace-demo"))
    }

    @Test
    fun testSetWorkspace() {
        assertNull(postmanSettingsHelper.getWorkspace("demo"))
        postmanSettingsHelper.setWorkspace("demo", "123456789")
        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo"))
    }

    @Test
    fun testRemoveWorkspaceByModule() {
        assertNull(postmanSettingsHelper.getWorkspace("demo"))
        postmanSettingsHelper.setWorkspace("demo", "123456789")
        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo"))
        postmanSettingsHelper.removeWorkspaceByProject("demo")
        assertNull(postmanSettingsHelper.getWorkspace("demo"))
    }

    @Test
    fun testRemoveWorkspace() {
        assertNull(postmanSettingsHelper.getWorkspace("demo"))
        postmanSettingsHelper.setWorkspace("demo", "123456789")
        postmanSettingsHelper.setWorkspace("demo2", "123456789")
        postmanSettingsHelper.setWorkspace("demo3", "987654321")
        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo"))
        assertEquals("123456789", postmanSettingsHelper.getWorkspace("demo2"))
        assertEquals("987654321", postmanSettingsHelper.getWorkspace("demo3"))
        postmanSettingsHelper.removeWorkspace("123456789")
        assertNull(postmanSettingsHelper.getWorkspace("demo"))
        assertNull(postmanSettingsHelper.getWorkspace("demo2"))
        assertEquals("987654321", postmanSettingsHelper.getWorkspace("demo3"))
    }

    @Test
    fun testReadWorkspaces() {
        val properties = Properties()
        properties["demo"] = "123456789"
        properties["demo2"] = "123456789"
        properties["demo3"] = "987654321"

        settings.postmanWorkspaces = ByteArrayOutputStream().also { properties.store(it, "") }.toString()

        assertEquals("{\"demo3\":\"987654321\",\"demo\":\"123456789\",\"demo2\":\"123456789\"}", postmanSettingsHelper.readWorkspaces().toJson())
    }

}