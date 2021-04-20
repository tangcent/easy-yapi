package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
                .thenReturn("123")
        builder.bindInstance(MessagesHelper::class, messagesHelper)
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
}