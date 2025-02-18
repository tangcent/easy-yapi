package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.settings.helper.SettingsHelperTest
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.asJsonElement
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.test.HttpClientProviderMockBuilder
import com.itangcent.test.response404
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [PostmanCachedApiHelper]
 */
abstract class PostmanCachedApiHelperTest : SettingsHelperTest() {

    @Inject
    protected lateinit var postmanApiHelper: PostmanCachedApiHelper

    @Inject
    private lateinit var localFileRepository: LocalFileRepository

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }
    }

    override fun afterBind(actionContext: ActionContext) {
        super.afterBind(actionContext)
        settings.postmanToken = "PMAK-XXXXXXXXXXXXXXXXXXXXXXXX-XXXXXXXX"
    }

    class SuccessDefaultPostmanApiHelperTest : PostmanCachedApiHelperTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(
                    HttpClientProviderMockBuilder.builder()
                        .url(DefaultPostmanApiHelper.COLLECTION)
                        .method("POST")
                        .response(
                            content = CREATE_OR_UPDATE_COLLECTION_SUCCESS_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/8283378b-5df6-488b-237c-f6b9ed0d7883")
                        .method("PUT")
                        .response(
                            content = CREATE_OR_UPDATE_COLLECTION_SUCCESS_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/31415926-67ce-2d4a-13c1-535897932384")
                        .method("PUT")
                        .response(
                            content = CREATE_OR_UPDATE_COLLECTION_FAILED_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/31415926-67ce-12cf-13c1-535897932384")
                        .method("PUT")
                        .response(
                            responseCode = 404
                        )
                        .url(DefaultPostmanApiHelper.COLLECTION)
                        .method("GET")
                        .response(
                            content = ALL_COLLECTIONS_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.WORKSPACE}/e508269b-72ef-4c67-92c4-55777ba33434")
                        .method("GET")
                        .response(
                            content = COLLECTIONS_IN_WORKSPACE,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/42dc9386-34ab-2d4a-83c1-535897932384")
                        .method("GET")
                        .response(
                            content = COLLECTION_INFO_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url(DefaultPostmanApiHelper.WORKSPACE)
                        .method("GET")
                        .response(
                            content = ALL_WORKSPACES_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.WORKSPACE}/0db55d8f-8e03-4568-2871-250a378ab87c")
                        .method("GET")
                        .response(
                            content = WORKSPACE_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/42dc9386-34ab-2d4a-83c1-535897932384")
                        .method("DELETE")
                        .response(
                            content = DELETE_COLLECTION_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .url("${DefaultPostmanApiHelper.COLLECTION}/378bacc3-77cf-5331-82c1-ed273450ae35")
                        .method("DELETE")
                        .response(
                            content = DELETE_COLLECTION_FAILED_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .notFound().response404()
                        .build()
                )
            }
        }

        @Test
        fun testCreateCollection() {
            val collection = postmanApiHelper.createCollection(KV.create(), null)
            assertEquals(
                "{\"id\":\"8283378b-5df6-488b-237c-f6b9ed0d7883\",\"name\":\"springboot-demo-20210812224241\",\"uid\":\"4253095-91292dfd-5de6-380a-d28c-e6b6ef0c1207\"}",
                collection.toJson()
            )
        }

        @Test
        fun testUpdateCollection() {
            assertTrue(postmanApiHelper.updateCollection("8283378b-5df6-488b-237c-f6b9ed0d7883", KV.create()))
            assertFalse(
                postmanApiHelper.updateCollection(
                    "31415926-67ce-2d4a-13c1-535897932384",
                    COLLECTION_INFO_RESULT.asJsonElement()?.sub("collection")!!.asMap()
                )
            )
            assertFalse(postmanApiHelper.updateCollection("31415926-67ce-12cf-13c1-535897932384", KV.create()))
        }

        @Test
        fun testGetAllCollection() {
            val allCollection = postmanApiHelper.getAllCollection()
            assertEquals(
                "[{\"owner\":\"4151095\",\"createdAt\":\"2018-07-03T07:18:17.000Z\",\"uid\":\"4251195-1172c991-4b94-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring apis\",\"isPublic\":false,\"id\":\"1171d990-4b01-e8b4-21a0-9e1edc7bbb49\",\"updatedAt\":\"2018-07-04T09:27:29.000Z\"},{\"owner\":\"4151095\",\"createdAt\":\"2018-05-18T02:15:14.000Z\",\"uid\":\"4251195-2272c991-4b74-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring-demo\",\"isPublic\":false,\"id\":\"1225b9ed-9ebe-4d83-94a1-fcd17c692910\",\"updatedAt\":\"2018-06-20T06:47:28.000Z\"}]",
                allCollection.toJson()
            )
            assertEquals(
                "[{\"owner\":\"4151095\",\"createdAt\":\"2018-07-03T07:18:17.000Z\",\"uid\":\"4251195-1172c991-4b94-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring apis\",\"isPublic\":false,\"id\":\"1171d990-4b01-e8b4-21a0-9e1edc7bbb49\",\"updatedAt\":\"2018-07-04T09:27:29.000Z\"},{\"owner\":\"4151095\",\"createdAt\":\"2018-05-18T02:15:14.000Z\",\"uid\":\"4251195-2272c991-4b74-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring-demo\",\"isPublic\":false,\"id\":\"1225b9ed-9ebe-4d83-94a1-fcd17c692910\",\"updatedAt\":\"2018-06-20T06:47:28.000Z\"}]",
                postmanApiHelper.getAllCollection(true).toJson()
            )
            assertEquals(
                "[{\"owner\":\"4151095\",\"createdAt\":\"2018-07-03T07:18:17.000Z\",\"uid\":\"4251195-1172c991-4b94-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring apis\",\"isPublic\":false,\"id\":\"1171d990-4b01-e8b4-21a0-9e1edc7bbb49\",\"updatedAt\":\"2018-07-04T09:27:29.000Z\"},{\"owner\":\"4151095\",\"createdAt\":\"2018-05-18T02:15:14.000Z\",\"uid\":\"4251195-2272c991-4b74-b8d1-91c0-0e1bb07eaf49\",\"name\":\"spring-demo\",\"isPublic\":false,\"id\":\"1225b9ed-9ebe-4d83-94a1-fcd17c692910\",\"updatedAt\":\"2018-06-20T06:47:28.000Z\"}]",
                postmanApiHelper.getAllCollection(false).toJson()
            )
        }

        @Test
        fun testGetCollectionByWorkspace() {
            val allCollection = postmanApiHelper.getCollectionByWorkspace("e508269b-72ef-4c67-92c4-55777ba33434")
            assertEquals(
                "[{\"id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"uid\":\"4250095-42dc9386-34ab-2d4a-83c1-535897932384\"}]",
                allCollection.toJson()
            )
            assertEquals(
                "[{\"id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"uid\":\"4250095-42dc9386-34ab-2d4a-83c1-535897932384\"}]",
                postmanApiHelper.getCollectionByWorkspace("e508269b-72ef-4c67-92c4-55777ba33434", true).toJson()
            )
            assertEquals(
                "[{\"id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"uid\":\"4250095-42dc9386-34ab-2d4a-83c1-535897932384\"}]",
                postmanApiHelper.getCollectionByWorkspace("e508269b-72ef-4c67-92c4-55777ba33434", false).toJson()
            )
        }

        @Test
        fun testGetCollectionInfo() {
            val collectionInfo = postmanApiHelper.getCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384")
            assertEquals(
                "{\"info\":{\"_postman_id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"description\":\"exported at 2021-08-13 09:31:47\",\"schema\":\"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"},\"item\":[{\"name\":\"simple test-\",\"item\":[{\"name\":\"test-RequestHeader\",\"id\":\"4a51ae4d-9ece-4bf4-9593-e0bc4dc4bc68\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"response\":[{\"id\":\"4a088045-3f62-4127-a089-6761177afd44\",\"name\":\"test-RequestHeader-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]},{\"name\":\"test-array params\",\"id\":\"c76f829d-8804-46ab-b7d5-dad6ff1791fa\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"response\":[{\"id\":\"19435d48-c88e-4197-96f1-3511edf1977d\",\"name\":\"test-array params-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]}],\"id\":\"8b081ba5-7909-45b8-b7c0-233ed4fcb234\",\"description\":\"simple test-\"}],\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"//collection\",\"var aa = 1;\",\"var bb = 2;\",\"//folder\",\"var aa = 3;\",\"var bb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"//collection\",\"var aaa = 1;\",\"var bbb = 2;\",\"//folder\",\"var aaa = 3;\",\"var bbb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}}]}",
                collectionInfo.toJson()
            )

            assertEquals(
                "{\"info\":{\"_postman_id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"description\":\"exported at 2021-08-13 09:31:47\",\"schema\":\"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"},\"item\":[{\"name\":\"simple test-\",\"item\":[{\"name\":\"test-RequestHeader\",\"id\":\"4a51ae4d-9ece-4bf4-9593-e0bc4dc4bc68\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"response\":[{\"id\":\"4a088045-3f62-4127-a089-6761177afd44\",\"name\":\"test-RequestHeader-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]},{\"name\":\"test-array params\",\"id\":\"c76f829d-8804-46ab-b7d5-dad6ff1791fa\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"response\":[{\"id\":\"19435d48-c88e-4197-96f1-3511edf1977d\",\"name\":\"test-array params-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]}],\"id\":\"8b081ba5-7909-45b8-b7c0-233ed4fcb234\",\"description\":\"simple test-\"}],\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"//collection\",\"var aa = 1;\",\"var bb = 2;\",\"//folder\",\"var aa = 3;\",\"var bb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"//collection\",\"var aaa = 1;\",\"var bbb = 2;\",\"//folder\",\"var aaa = 3;\",\"var bbb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}}]}",
                postmanApiHelper.getCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384", true).toJson()
            )

            assertEquals(
                "{\"info\":{\"_postman_id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"name\":\"springboot-demo-20210813093147\",\"description\":\"exported at 2021-08-13 09:31:47\",\"schema\":\"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"},\"item\":[{\"name\":\"simple test-\",\"item\":[{\"name\":\"test-RequestHeader\",\"id\":\"4a51ae4d-9ece-4bf4-9593-e0bc4dc4bc68\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"response\":[{\"id\":\"4a088045-3f62-4127-a089-6761177afd44\",\"name\":\"test-RequestHeader-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/header\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"header\"]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]},{\"name\":\"test-array params\",\"id\":\"c76f829d-8804-46ab-b7d5-dad6ff1791fa\",\"request\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"response\":[{\"id\":\"19435d48-c88e-4197-96f1-3511edf1977d\",\"name\":\"test-array params-Example\",\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth-token\"}],\"url\":{\"raw\":\"{{springboot-demo}}/test/arrays?string=&int=0\",\"host\":[\"{{springboot-demo}}\"],\"path\":[\"test\",\"arrays\"],\"query\":[{\"key\":\"string\",\"value\":\"\",\"description\":\"type:string[]\"},{\"key\":\"int\",\"value\":\"0\",\"description\":\"type:int[]\"}]}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"Fri, 13 Aug 202109:31:47 GMT\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"cookie\":[],\"body\":\"\\\"\\\"\"}]}],\"id\":\"8b081ba5-7909-45b8-b7c0-233ed4fcb234\",\"description\":\"simple test-\"}],\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"//collection\",\"var aa = 1;\",\"var bb = 2;\",\"//folder\",\"var aa = 3;\",\"var bb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"//collection\",\"var aaa = 1;\",\"var bbb = 2;\",\"//folder\",\"var aaa = 3;\",\"var bbb = 4;\",\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"],\"type\":\"text/javascript\"}}]}",
                postmanApiHelper.getCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384", false).toJson()
            )
        }

        @Test
        fun testGetAllWorkspaces() {
            assertEquals(
                "[{\"id\":\"0db55d8f-8e03-4568-2871-250a378ab87c\",\"name\":\"Team Workspace\",\"type\":\"team\"},{\"id\":\"1e2e3d32-91fe-4208-a4cf-714c372b5885\",\"name\":\"easy-yapi-Dev\",\"type\":\"team\"},{\"id\":\"58fa9260-917e-4ee7-9359-ba2f3ac02993\",\"name\":\"tangcent\",\"type\":\"personal\"},{\"id\":\"6792df46-04c3-832f-88f1-c723d1716ca4\",\"name\":\"My Workspace\",\"type\":\"personal\"},{\"id\":\"e60ffc9b-3ca5-4222-91ea-0a30f22ea4d3\",\"name\":\"easy-yapi-demo\",\"type\":\"personal\"},{\"id\":\"e508269b-72ef-4c67-92c4-55777ba33434\",\"name\":\"tang\",\"type\":\"team\"}]",
                postmanApiHelper.getAllWorkspaces().toJson()
            )

            assertEquals(
                "[{\"id\":\"0db55d8f-8e03-4568-2871-250a378ab87c\",\"name\":\"Team Workspace\",\"type\":\"team\"},{\"id\":\"1e2e3d32-91fe-4208-a4cf-714c372b5885\",\"name\":\"easy-yapi-Dev\",\"type\":\"team\"},{\"id\":\"58fa9260-917e-4ee7-9359-ba2f3ac02993\",\"name\":\"tangcent\",\"type\":\"personal\"},{\"id\":\"6792df46-04c3-832f-88f1-c723d1716ca4\",\"name\":\"My Workspace\",\"type\":\"personal\"},{\"id\":\"e60ffc9b-3ca5-4222-91ea-0a30f22ea4d3\",\"name\":\"easy-yapi-demo\",\"type\":\"personal\"},{\"id\":\"e508269b-72ef-4c67-92c4-55777ba33434\",\"name\":\"tang\",\"type\":\"team\"}]",
                postmanApiHelper.getAllWorkspaces().toJson()
            )

            settings.postmanToken = null
            assertNull(postmanApiHelper.getAllWorkspaces())

        }

        @Test
        fun testGetWorkspaceInfo() {
            val workspaceInfo = postmanApiHelper.getWorkspaceInfo("0db55d8f-8e03-4568-2871-250a378ab87c")
            assertEquals(
                "{\"id\":\"0db55d8f-8e03-4568-2871-250a378ab87c\",\"name\":\"Team Workspace\",\"type\":\"team\"}",
                workspaceInfo.toJson()
            )
        }

        @Test
        fun testDeleteCollectionInfo() {
            val deletedCollection = postmanApiHelper.deleteCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384")
            assertEquals(
                "{\"id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"uid\":\"42dc9386-34ab-2d4a-83c1-535897932384\"}",
                deletedCollection.toJson()
            )

            assertNull(postmanApiHelper.deleteCollectionInfo("378bacc3-77cf-5331-82c1-ed273450ae35"))
        }

    }

    abstract class FailedDefaultPostmanApiHelperTest : PostmanCachedApiHelperTest() {

        @Test
        fun testCreateCollection() {
            assertNull(postmanApiHelper.createCollection(KV.create(), null))
        }

        @Test
        fun testUpdateCollection() {
            assertFalse(postmanApiHelper.updateCollection("8283378b-5df6-488b-237c-f6b9ed0d7883", KV.create()))
            assertFalse(postmanApiHelper.updateCollection("31415926-67ce-2d4a-13c1-535897932384", KV.create()))
            assertFalse(postmanApiHelper.updateCollection("31415926-67ce-12cf-13c1-535897932384", KV.create()))
        }

        @Test
        fun testGetAllCollection() {
            assertNull(postmanApiHelper.getAllCollection())
        }

        @Test
        fun testGetCollectionByWorkspace() {
            assertNull(postmanApiHelper.getCollectionByWorkspace("e508269b-72ef-4c67-92c4-55777ba33434"))
        }

        @Test
        fun testGetCollectionInfo() {
            assertNull(postmanApiHelper.getCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384"))
        }

        @Test
        fun testGetAllWorkspaces() {
            assertNull(postmanApiHelper.getAllWorkspaces())
        }

        @Test
        fun testGetWorkspaceInfo() {
            assertNull(postmanApiHelper.getWorkspaceInfo("0db55d8f-8e03-4568-2871-250a378ab87c"))
        }

        @Test
        fun testDeleteCollectionInfo() {
            assertNull(postmanApiHelper.deleteCollectionInfo("42dc9386-34ab-2d4a-83c1-535897932384"))
            assertNull(postmanApiHelper.deleteCollectionInfo("378bacc3-77cf-5331-82c1-ed273450ae35"))
        }
    }

    class AuthenticationFailedDefaultPostmanApiHelperTest : FailedDefaultPostmanApiHelperTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(
                    HttpClientProviderMockBuilder.builder()
                        .call()
                        .response(
                            content = AUTHENTICATION_FAILED_RESULT,
                            contentType = ContentType.APPLICATION_JSON
                        )
                        .notFound().response404()
                        .build()
                )
            }
        }

    }

    class ServiceLimitExhaustedFailedDefaultPostmanApiHelperTest : FailedDefaultPostmanApiHelperTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(
                    HttpClientProviderMockBuilder.builder()
                        .call()
                        .response(
                            content = SERVICE_LIMIT_EXHAUSTED_FAILED_RESULT,
                            contentType = ContentType.APPLICATION_JSON,
                            responseCode = 429
                        )
                        .notFound().response404()
                        .build()
                )
            }
        }
    }

    class UnreachableFailedDefaultPostmanApiHelperTest : FailedDefaultPostmanApiHelperTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(
                    HttpClientProviderMockBuilder.builder()
                        .call()
                        .response404()
                        .build()
                )
            }
        }
    }

    companion object {

        const val CREATE_OR_UPDATE_COLLECTION_SUCCESS_RESULT =
            "{\"collection\":{\"id\":\"8283378b-5df6-488b-237c-f6b9ed0d7883\",\"name\":\"springboot-demo-20210812224241\",\"uid\":\"4253095-91292dfd-5de6-380a-d28c-e6b6ef0c1207\"}}"

        const val CREATE_OR_UPDATE_COLLECTION_FAILED_RESULT =
            "{\"error\":{\"name\":\"instanceNotFoundError\",\"message\":\"The specified item does not exist.\",\"details\":{\"item\":\"collection\",\"id\":\"378bacc3-77cf-5331-82c1-ed273450ae35\"}}}"

        const val ALL_COLLECTIONS_RESULT = "{\n" +
                "    \"collections\": [\n" +
                "        {\n" +
                "            \"owner\": \"4151095\",\n" +
                "            \"createdAt\": \"2018-07-03T07:18:17.000Z\",\n" +
                "            \"uid\": \"4251195-1172c991-4b94-b8d1-91c0-0e1bb07eaf49\",\n" +
                "            \"name\": \"spring apis\",\n" +
                "            \"isPublic\": false,\n" +
                "            \"id\": \"1171d990-4b01-e8b4-21a0-9e1edc7bbb49\",\n" +
                "            \"updatedAt\": \"2018-07-04T09:27:29.000Z\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"owner\": \"4151095\",\n" +
                "            \"createdAt\": \"2018-05-18T02:15:14.000Z\",\n" +
                "            \"uid\": \"4251195-2272c991-4b74-b8d1-91c0-0e1bb07eaf49\",\n" +
                "            \"name\": \"spring-demo\",\n" +
                "            \"isPublic\": false,\n" +
                "            \"id\": \"1225b9ed-9ebe-4d83-94a1-fcd17c692910\",\n" +
                "            \"updatedAt\": \"2018-06-20T06:47:28.000Z\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"

        const val COLLECTIONS_IN_WORKSPACE =
            "{\n" +
                    "    \"workspace\": {\n" +
                    "        \"id\": \"e508269b-72ef-4c67-92c4-55777ba33434\",\n" +
                    "        \"name\": \"tang\",\n" +
                    "        \"type\": \"team\",\n" +
                    "        \"description\": null,\n" +
                    "        \"collections\": [\n" +
                    "            {\n" +
                    "                \"id\": \"42dc9386-34ab-2d4a-83c1-535897932384\",\n" +
                    "                \"name\": \"springboot-demo-20210813093147\",\n" +
                    "                \"uid\": \"4250095-42dc9386-34ab-2d4a-83c1-535897932384\"\n" +
                    "            }\n" +
                    "        ]\n" +
                    "    }\n" +
                    "}"

        const val COLLECTION_INFO_RESULT = "{\n" +
                "    \"collection\": {\n" +
                "        \"info\": {\n" +
                "            \"_postman_id\": \"42dc9386-34ab-2d4a-83c1-535897932384\",\n" +
                "            \"name\": \"springboot-demo-20210813093147\",\n" +
                "            \"description\": \"exported at 2021-08-13 09:31:47\",\n" +
                "            \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n" +
                "        },\n" +
                "        \"item\": [\n" +
                "            {\n" +
                "                \"name\": \"simple test-\",\n" +
                "                \"item\": [\n" +
                "                    {\n" +
                "                        \"name\": \"test-RequestHeader\",\n" +
                "                        \"id\": \"4a51ae4d-9ece-4bf4-9593-e0bc4dc4bc68\",\n" +
                "                        \"request\": {\n" +
                "                            \"method\": \"GET\",\n" +
                "                            \"header\": [\n" +
                "                                {\n" +
                "                                    \"key\": \"token\",\n" +
                "                                    \"value\": \"\",\n" +
                "                                    \"type\": \"text\",\n" +
                "                                    \"description\": \"auth-token\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"url\": {\n" +
                "                                \"raw\": \"{{springboot-demo}}/test/header\",\n" +
                "                                \"host\": [\n" +
                "                                    \"{{springboot-demo}}\"\n" +
                "                                ],\n" +
                "                                \"path\": [\n" +
                "                                    \"test\",\n" +
                "                                    \"header\"\n" +
                "                                ]\n" +
                "                            }\n" +
                "                        },\n" +
                "                        \"response\": [\n" +
                "                            {\n" +
                "                                \"id\": \"4a088045-3f62-4127-a089-6761177afd44\",\n" +
                "                                \"name\": \"test-RequestHeader-Example\",\n" +
                "                                \"originalRequest\": {\n" +
                "                                    \"method\": \"GET\",\n" +
                "                                    \"header\": [\n" +
                "                                        {\n" +
                "                                            \"key\": \"token\",\n" +
                "                                            \"value\": \"\",\n" +
                "                                            \"type\": \"text\",\n" +
                "                                            \"description\": \"auth-token\"\n" +
                "                                        }\n" +
                "                                    ],\n" +
                "                                    \"url\": {\n" +
                "                                        \"raw\": \"{{springboot-demo}}/test/header\",\n" +
                "                                        \"host\": [\n" +
                "                                            \"{{springboot-demo}}\"\n" +
                "                                        ],\n" +
                "                                        \"path\": [\n" +
                "                                            \"test\",\n" +
                "                                            \"header\"\n" +
                "                                        ]\n" +
                "                                    }\n" +
                "                                },\n" +
                "                                \"code\": 200,\n" +
                "                                \"_postman_previewlanguage\": \"json\",\n" +
                "                                \"header\": [\n" +
                "                                    {\n" +
                "                                        \"name\": \"date\",\n" +
                "                                        \"key\": \"date\",\n" +
                "                                        \"value\": \"Fri, 13 Aug 202109:31:47 GMT\",\n" +
                "                                        \"description\": \"The date and time that the message was sent\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"server\",\n" +
                "                                        \"key\": \"server\",\n" +
                "                                        \"value\": \"Apache-Coyote/1.1\",\n" +
                "                                        \"description\": \"A name for the server\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"transfer-encoding\",\n" +
                "                                        \"key\": \"transfer-encoding\",\n" +
                "                                        \"value\": \"chunked\",\n" +
                "                                        \"description\": \"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"content-type\",\n" +
                "                                        \"key\": \"content-type\",\n" +
                "                                        \"value\": \"application/json;charset=UTF-8\"\n" +
                "                                    }\n" +
                "                                ],\n" +
                "                                \"cookie\": [],\n" +
                "                                \"body\": \"\\\"\\\"\"\n" +
                "                            }\n" +
                "                        ]\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"name\": \"test-array params\",\n" +
                "                        \"id\": \"c76f829d-8804-46ab-b7d5-dad6ff1791fa\",\n" +
                "                        \"request\": {\n" +
                "                            \"method\": \"GET\",\n" +
                "                            \"header\": [\n" +
                "                                {\n" +
                "                                    \"key\": \"token\",\n" +
                "                                    \"value\": \"\",\n" +
                "                                    \"type\": \"text\",\n" +
                "                                    \"description\": \"auth-token\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"url\": {\n" +
                "                                \"raw\": \"{{springboot-demo}}/test/arrays?string=&int=0\",\n" +
                "                                \"host\": [\n" +
                "                                    \"{{springboot-demo}}\"\n" +
                "                                ],\n" +
                "                                \"path\": [\n" +
                "                                    \"test\",\n" +
                "                                    \"arrays\"\n" +
                "                                ],\n" +
                "                                \"query\": [\n" +
                "                                    {\n" +
                "                                        \"key\": \"string\",\n" +
                "                                        \"value\": \"\",\n" +
                "                                        \"description\": \"type:string[]\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"key\": \"int\",\n" +
                "                                        \"value\": \"0\",\n" +
                "                                        \"description\": \"type:int[]\"\n" +
                "                                    }\n" +
                "                                ]\n" +
                "                            }\n" +
                "                        },\n" +
                "                        \"response\": [\n" +
                "                            {\n" +
                "                                \"id\": \"19435d48-c88e-4197-96f1-3511edf1977d\",\n" +
                "                                \"name\": \"test-array params-Example\",\n" +
                "                                \"originalRequest\": {\n" +
                "                                    \"method\": \"GET\",\n" +
                "                                    \"header\": [\n" +
                "                                        {\n" +
                "                                            \"key\": \"token\",\n" +
                "                                            \"value\": \"\",\n" +
                "                                            \"type\": \"text\",\n" +
                "                                            \"description\": \"auth-token\"\n" +
                "                                        }\n" +
                "                                    ],\n" +
                "                                    \"url\": {\n" +
                "                                        \"raw\": \"{{springboot-demo}}/test/arrays?string=&int=0\",\n" +
                "                                        \"host\": [\n" +
                "                                            \"{{springboot-demo}}\"\n" +
                "                                        ],\n" +
                "                                        \"path\": [\n" +
                "                                            \"test\",\n" +
                "                                            \"arrays\"\n" +
                "                                        ],\n" +
                "                                        \"query\": [\n" +
                "                                            {\n" +
                "                                                \"key\": \"string\",\n" +
                "                                                \"value\": \"\",\n" +
                "                                                \"description\": \"type:string[]\"\n" +
                "                                            },\n" +
                "                                            {\n" +
                "                                                \"key\": \"int\",\n" +
                "                                                \"value\": \"0\",\n" +
                "                                                \"description\": \"type:int[]\"\n" +
                "                                            }\n" +
                "                                        ]\n" +
                "                                    }\n" +
                "                                },\n" +
                "                                \"code\": 200,\n" +
                "                                \"_postman_previewlanguage\": \"json\",\n" +
                "                                \"header\": [\n" +
                "                                    {\n" +
                "                                        \"name\": \"date\",\n" +
                "                                        \"key\": \"date\",\n" +
                "                                        \"value\": \"Fri, 13 Aug 202109:31:47 GMT\",\n" +
                "                                        \"description\": \"The date and time that the message was sent\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"server\",\n" +
                "                                        \"key\": \"server\",\n" +
                "                                        \"value\": \"Apache-Coyote/1.1\",\n" +
                "                                        \"description\": \"A name for the server\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"transfer-encoding\",\n" +
                "                                        \"key\": \"transfer-encoding\",\n" +
                "                                        \"value\": \"chunked\",\n" +
                "                                        \"description\": \"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"\n" +
                "                                    },\n" +
                "                                    {\n" +
                "                                        \"name\": \"content-type\",\n" +
                "                                        \"key\": \"content-type\",\n" +
                "                                        \"value\": \"application/json;charset=UTF-8\"\n" +
                "                                    }\n" +
                "                                ],\n" +
                "                                \"cookie\": [],\n" +
                "                                \"body\": \"\\\"\\\"\"\n" +
                "                            }\n" +
                "                        ]\n" +
                "                    }\n" +
                "                ],\n" +
                "                \"id\": \"8b081ba5-7909-45b8-b7c0-233ed4fcb234\",\n" +
                "                \"description\": \"simple test-\"\n" +
                "            }\n" +
                "        ],\n" +
                "        \"event\": [\n" +
                "            {\n" +
                "                \"listen\": \"prerequest\",\n" +
                "                \"script\": {\n" +
                "                    \"exec\": [\n" +
                "                        \"//collection\",\n" +
                "                        \"var aa = 1;\",\n" +
                "                        \"var bb = 2;\",\n" +
                "                        \"//folder\",\n" +
                "                        \"var aa = 3;\",\n" +
                "                        \"var bb = 4;\",\n" +
                "                        \"var a = \\\"1\\\";\",\n" +
                "                        \"var b = \\\"1\\\";\"\n" +
                "                    ],\n" +
                "                    \"type\": \"text/javascript\"\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"listen\": \"test\",\n" +
                "                \"script\": {\n" +
                "                    \"exec\": [\n" +
                "                        \"//collection\",\n" +
                "                        \"var aaa = 1;\",\n" +
                "                        \"var bbb = 2;\",\n" +
                "                        \"//folder\",\n" +
                "                        \"var aaa = 3;\",\n" +
                "                        \"var bbb = 4;\",\n" +
                "                        \"var a = \\\"1\\\";\",\n" +
                "                        \"var b = \\\"1\\\";\"\n" +
                "                    ],\n" +
                "                    \"type\": \"text/javascript\"\n" +
                "                }\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}"

        const val ALL_WORKSPACES_RESULT =
            "{\n" +
                    "    \"workspaces\": [\n" +
                    "        {\n" +
                    "            \"id\": \"0db55d8f-8e03-4568-2871-250a378ab87c\",\n" +
                    "            \"name\": \"Team Workspace\",\n" +
                    "            \"type\": \"team\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": \"1e2e3d32-91fe-4208-a4cf-714c372b5885\",\n" +
                    "            \"name\": \"easy-yapi-Dev\",\n" +
                    "            \"type\": \"team\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": \"58fa9260-917e-4ee7-9359-ba2f3ac02993\",\n" +
                    "            \"name\": \"tangcent\",\n" +
                    "            \"type\": \"personal\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": \"6792df46-04c3-832f-88f1-c723d1716ca4\",\n" +
                    "            \"name\": \"My Workspace\",\n" +
                    "            \"type\": \"personal\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": \"e60ffc9b-3ca5-4222-91ea-0a30f22ea4d3\",\n" +
                    "            \"name\": \"easy-yapi-demo\",\n" +
                    "            \"type\": \"personal\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": \"e508269b-72ef-4c67-92c4-55777ba33434\",\n" +
                    "            \"name\": \"tang\",\n" +
                    "            \"type\": \"team\"\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}"

        const val WORKSPACE_RESULT =
            "{\"workspace\":{\"id\":\"0db55d8f-8e03-4568-2871-250a378ab87c\",\"name\":\"Team Workspace\",\"type\":\"team\",\"description\":\"This workspace contains all your team's collections and environments that were previously in the Team Library, as well as any monitors, mock servers or integrations that were shared with the team.\"}}"

        const val DELETE_COLLECTION_RESULT =
            "{\"collection\":{\"id\":\"42dc9386-34ab-2d4a-83c1-535897932384\",\"uid\":\"42dc9386-34ab-2d4a-83c1-535897932384\"}}"

        const val DELETE_COLLECTION_FAILED_RESULT =
            "{\"error\":{\"name\":\"instanceNotFoundError\",\"message\":\"The specified item does not exist.\",\"details\":{\"item\":\"collection\",\"id\":\"378bacc3-77cf-5331-82c1-ed273450ae35\"}}}"

        const val AUTHENTICATION_FAILED_RESULT =
            "{\"error\":{\"name\":\"AuthenticationError\",\"message\":\"Invalid API Key. Every request requires a valid API Key to be sent.\"}}"

        const val SERVICE_LIMIT_EXHAUSTED_FAILED_RESULT =
            "{\"error\":{\"name\":\"serviceLimitExhausted\",\"message\":\"Service limit exhausted. Please contact your team admin.\"}}"
    }
}