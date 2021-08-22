package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanWorkspace
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.test.mock
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [DefaultPostmanSettingsHelper]
 */
internal open class DefaultPostmanSettingsHelperTest : SettingsHelperTest() {

    @Inject
    protected lateinit var postmanSettingsHelper: PostmanSettingsHelper

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.mock(PostmanApiHelper::class)
    }

    class PrivateTokenTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                on(
                    messagesHelper.showInputDialog(
                        anyString(),
                        eq("Postman Private Token"),
                        any()
                    )
                ).thenReturn(null, "123")
            }
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
            assertNull(postmanSettingsHelper.getPrivateToken(false))
            assertEquals("123", postmanSettingsHelper.getPrivateToken(false))
            assertEquals("123", settings.postmanToken)
            assertEquals("123", postmanSettingsHelper.getPrivateToken())
        }
    }

    class GetWorkspaceFromDistinctTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val postmanApiHelper = mock<PostmanApiHelper>()
            postmanApiHelper.stub {
                on(postmanApiHelper.getAllWorkspaces())
                    .thenReturn(
                        listOf(
                            PostmanWorkspace("111", "aaa", "team"),
                            PostmanWorkspace("222", "bbb", "team"),
                            PostmanWorkspace("333", "ccc", "team"),
                        )
                    )
            }
            builder.bindInstance(PostmanApiHelper::class, postmanApiHelper)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Workspace For Current Project"),
                        Mockito.eq("Postman Workspace"),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> { arrayOf("aaa", "bbb", "ccc").contentEquals(it) },
                        Mockito.any()
                    )
                ).thenReturn(null, "bbb")
            }

            builder.bindInstance(MessagesHelper::class, messagesHelper)

        }

        @Test
        fun testGetWorkspace() {
            assertNull(postmanSettingsHelper.getWorkspace())
            assertNull(settings.postmanWorkspace)
            assertNull(postmanSettingsHelper.getWorkspace(false))
            assertEquals("222", postmanSettingsHelper.getWorkspace(false))
            assertEquals("222", settings.postmanWorkspace)
            assertEquals("222", postmanSettingsHelper.getWorkspace())
        }
    }

    class GetWorkspaceFromDuplicatedTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val postmanApiHelper = mock<PostmanApiHelper>()
            postmanApiHelper.stub {
                on(postmanApiHelper.getAllWorkspaces())
                    .thenReturn(
                        listOf(
                            PostmanWorkspace("111", "aaa", "team"),
                            PostmanWorkspace("222", "bbb", "team"),
                            PostmanWorkspace("223", "bbb", "team"),
                            PostmanWorkspace("333", "ccc", "team"),
                        )
                    )
            }
            builder.bindInstance(PostmanApiHelper::class, postmanApiHelper)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Workspace For Current Project"),
                        Mockito.eq("Postman Workspace"),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> {
                            arrayOf("1: aaa", "2: bbb", "3: bbb", "4: ccc").contentEquals(
                                it
                            )
                        },
                        Mockito.any()
                    )
                ).thenReturn(null, "3: bbb")
            }

            builder.bindInstance(MessagesHelper::class, messagesHelper)

        }

        @Test
        fun testGetWorkspace() {
            assertNull(postmanSettingsHelper.getWorkspace())
            assertNull(settings.postmanWorkspace)
            assertNull(postmanSettingsHelper.getWorkspace(false))
            assertEquals("223", postmanSettingsHelper.getWorkspace(false))
            assertEquals("223", settings.postmanWorkspace)
            assertEquals("223", postmanSettingsHelper.getWorkspace())
        }
    }

    class EditErrorWorkspaceTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val postmanApiHelper = mock<PostmanApiHelper>()
            postmanApiHelper.stub {
                on(postmanApiHelper.getAllWorkspaces())
                    .thenReturn(
                        listOf(
                            PostmanWorkspace("111", "aaa", "team"),
                            PostmanWorkspace("222", "bbb", "team"),
                            PostmanWorkspace("333", "ccc", "team"),
                        )
                    )
            }
            builder.bindInstance(PostmanApiHelper::class, postmanApiHelper)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Workspace For Current Project"),
                        Mockito.eq("Postman Workspace"),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> { arrayOf("aaa", "bbb", "ccc").contentEquals(it) },
                        Mockito.any()
                    )
                ).thenReturn("fff", "ccc")
            }

            builder.bindInstance(MessagesHelper::class, messagesHelper)

        }

        @Test
        fun testGetWorkspace() {
            assertNull(postmanSettingsHelper.getWorkspace())
            assertNull(settings.postmanWorkspace)
            assertNull(postmanSettingsHelper.getWorkspace(false))
            assertEquals("333", postmanSettingsHelper.getWorkspace(false))
            assertEquals("333", settings.postmanWorkspace)
            assertEquals("333", postmanSettingsHelper.getWorkspace())
        }
    }

    class SimplePostmanSettingsHelperTest : DefaultPostmanSettingsHelperTest() {

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
}