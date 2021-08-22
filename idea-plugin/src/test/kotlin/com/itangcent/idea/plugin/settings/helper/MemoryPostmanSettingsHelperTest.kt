package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.test.mock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [MemoryPostmanSettingsHelper]
 */
internal class MemoryPostmanSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    private val memoryPostmanSettingsHelper: MemoryPostmanSettingsHelper by lazy {
        postmanSettingsHelper as MemoryPostmanSettingsHelper
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(PostmanSettingsHelper::class) { it.with(MemoryPostmanSettingsHelper::class) }
        builder.mock(PostmanApiHelper::class)
    }

    @Test
    fun testSetPrivateToken() {
        settings.postmanToken = null
        assertNull(postmanSettingsHelper.getPrivateToken())

        settings.postmanToken = "PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-XXXXXXXX"
        assertEquals("PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-XXXXXXXX", postmanSettingsHelper.getPrivateToken())

        memoryPostmanSettingsHelper.setPrivateToken("PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-YYYYYYYY")
        assertEquals("PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-YYYYYYYY", postmanSettingsHelper.getPrivateToken())

        memoryPostmanSettingsHelper.setPrivateToken(null)
        assertEquals("PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-XXXXXXXX", postmanSettingsHelper.getPrivateToken())
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