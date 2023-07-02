package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanWorkspace
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.any
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
                            PostmanWorkspace("222", "bbb", "personal"),
                            PostmanWorkspace("333", "ccc", "team")
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
                        Mockito.argThat<Array<PostmanWorkspace>?> {
                            arrayOf(
                                PostmanWorkspace("111", "aaa", "team"),
                                PostmanWorkspace("222", "bbb", "team"),
                                PostmanWorkspace("222", "bbb", "personal"),
                                PostmanWorkspace("333", "ccc", "team")
                            ).contentEquals(it)
                        },
                        any { it.nameWithType() ?: "" },
                        Mockito.any()
                    )
                ).thenReturn(null, PostmanWorkspace("222", "bbb", "team"))
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
                            PostmanWorkspace("333", "ccc", "team")
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
                        Mockito.argThat<Array<PostmanWorkspace>?> {
                            arrayOf(
                                PostmanWorkspace("111", "aaa", "team"),
                                PostmanWorkspace("222", "bbb", "team"),
                                PostmanWorkspace("223", "bbb", "team"),
                                PostmanWorkspace("333", "ccc", "team")
                            ).contentEquals(
                                it
                            )
                        },
                        any { it.name ?: "" },
                        Mockito.any()
                    )
                ).thenReturn(null, PostmanWorkspace("223", "bbb", "team"))
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

        @Test
        fun testPostmanExportMode() {
            for (postmanExportMode in PostmanExportMode.values()) {
                settings.postmanExportMode = postmanExportMode.name
                assertEquals(postmanExportMode, postmanSettingsHelper.postmanExportMode())
            }
        }
    }

    class GetCollectionFromDistinctTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val postmanApiHelper = mock<PostmanApiHelper>()
            postmanApiHelper.stub {
                on(postmanApiHelper.getAllCollection())
                    .thenReturn(
                        arrayListOf(
                            hashMapOf("name" to "aaa", "id" to "111"),
                            hashMapOf("name" to "bbb", "id" to "222"),
                            hashMapOf("name" to "ccc", "id" to "333")
                        )
                    )
            }
            builder.bindInstance(PostmanApiHelper::class, postmanApiHelper)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        eq("Select a collection to save apis in [module-a] to"),
                        eq("Postman Collection"),
                        Mockito.any(),
                        Mockito.argThat<Array<Map<String, Any?>>?> {
                            arrayOf(
                                mapOf("name" to "aaa", "id" to "111"),
                                mapOf("name" to "bbb", "id" to "222"),
                                mapOf("name" to "ccc", "id" to "333")
                            ).contentEquals(it)
                        },
                        any { it["name"].toString() },
                        Mockito.any()
                    )
                ).thenReturn(null, hashMapOf("name" to "bbb", "id" to "222"))
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        eq("Select a collection to save apis in [module-b] to"),
                        eq("Postman Collection"),
                        Mockito.any(),
                        Mockito.argThat<Array<Map<String, Any?>>?> {
                            arrayOf(
                                mapOf("name" to "aaa", "id" to "111"),
                                mapOf("name" to "bbb", "id" to "222"),
                                mapOf("name" to "ccc", "id" to "333")
                            ).contentEquals(it)
                        },
                        any { it["name"].toString() },
                        Mockito.any()
                    )
                ).thenReturn(hashMapOf("name" to "ccc", "id" to "333"))
            }

            builder.bindInstance(MessagesHelper::class, messagesHelper)
        }

        @Test
        fun testGetCollection() {
            assertNull(postmanSettingsHelper.getCollectionId("module-a"))
            assertNull(postmanSettingsHelper.getCollectionId("module-a", false))
            assertEquals("222", postmanSettingsHelper.getCollectionId("module-a", false))
            assertEquals("222", postmanSettingsHelper.getCollectionId("module-a"))
            assertNull(postmanSettingsHelper.getCollectionId("module-b"))
            assertEquals("333", postmanSettingsHelper.getCollectionId("module-b", false))
            assertEquals("333", postmanSettingsHelper.getCollectionId("module-b"))
        }
    }

    class GetCollectionFromDuplicatedTest : DefaultPostmanSettingsHelperTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            val postmanApiHelper = mock<PostmanApiHelper>()
            postmanApiHelper.stub {
                on(postmanApiHelper.getAllCollection())
                    .thenReturn(
                        arrayListOf(
                            hashMapOf("name" to "aaa", "id" to "111"),
                            hashMapOf("name" to "bbb", "id" to "222"),
                            hashMapOf("name" to "bbb", "id" to "555"),
                            hashMapOf("name" to "ccc", "id" to "333")
                        )
                    )
            }
            builder.bindInstance(PostmanApiHelper::class, postmanApiHelper)

            val messagesHelper = mock<MessagesHelper>()
            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        eq("Select a collection to save apis in [module-a] to"),
                        eq("Postman Collection"),
                        Mockito.any(),
                        Mockito.argThat<Array<Map<String, Any?>>?> {
                            arrayOf(
                                mapOf("name" to "aaa", "id" to "111"),
                                mapOf("name" to "bbb", "id" to "222"),
                                mapOf("name" to "bbb", "id" to "555"),
                                mapOf("name" to "ccc", "id" to "333")
                            ).contentEquals(it)
                        },
                        any { it["name"].toString() },
                        Mockito.any()
                    )
                ).thenReturn(null, hashMapOf("name" to "bbb", "id" to "222"))
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        eq("Select a collection to save apis in [module-b] to"),
                        eq("Postman Collection"),
                        Mockito.any(),
                        Mockito.argThat<Array<Map<String, Any?>>?> {
                            arrayOf(
                                mapOf("name" to "aaa", "id" to "111"),
                                mapOf("name" to "bbb", "id" to "222"),
                                mapOf("name" to "bbb", "id" to "555"),
                                mapOf("name" to "ccc", "id" to "333")
                            ).contentEquals(it)
                        },
                        any { it["name"].toString() },
                        Mockito.any()
                    )
                ).thenReturn(hashMapOf("name" to "bbb", "id" to "555"))
            }

            builder.bindInstance(MessagesHelper::class, messagesHelper)
        }

        @Test
        fun testGetCollection() {
            assertNull(postmanSettingsHelper.getCollectionId("module-a"))
            assertNull(postmanSettingsHelper.getCollectionId("module-a", false))
            assertEquals("222", postmanSettingsHelper.getCollectionId("module-a", false))
            assertEquals("222", postmanSettingsHelper.getCollectionId("module-a"))
            assertNull(postmanSettingsHelper.getCollectionId("module-b"))
            assertEquals("555", postmanSettingsHelper.getCollectionId("module-b", false))
            assertEquals("555", postmanSettingsHelper.getCollectionId("module-b"))
        }
    }

}