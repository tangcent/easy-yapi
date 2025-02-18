package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.DataContext
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.asDate
import com.itangcent.common.utils.formatDate
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit.STANDARD_TIME
import com.itangcent.test.mock
import org.junit.jupiter.api.condition.OS

/**
 * Test case of [PostmanFormatter]
 */
internal class PostmanFormatterTest : PostmanSpringClassExporterBaseTest() {

    @Inject
    private lateinit var postmanFormatter: PostmanFormatter

    private lateinit var settings: Settings

    private val date = STANDARD_TIME.asDate().formatDate("EEE, dd MMM yyyyHH:mm:ss 'GMT'")

    override fun beforeBind() {
        super.beforeBind()
        settings = Settings()
        settings.inferEnable = true
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(STANDARD_TIME))
        }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
        builder.bindInstance(PostmanApiHelper::class, PostmanCachedApiHelper())
        builder.mock(DataContext::class)
    }

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    /**
     * use json-schema by default
     */
    fun testRequest2Item() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.NONE.name
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"ctrl\",\"name\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/ctrl/name\"}},\"response\":[{\"name\":\"current ctrl name-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"ctrl\",\"name\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/ctrl/name\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"\"}],\"name\":\"current ctrl name\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[0]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/greeting\"}},\"response\":[{\"name\":\"say hello-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/greeting\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"\"}],\"name\":\"say hello\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[1]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"\",\"equals\":true,\"description\":\"user id\"}],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/get/{id}\"}},\"response\":[{\"name\":\"get user info-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"\",\"equals\":true,\"description\":\"user id\"}],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/get/{id}\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"{\\n  \\\"code\\\": 0,\\n  \\\"msg\\\": \\\"success\\\",\\n  \\\"data\\\": {\\n    \\\"id\\\": 0,\\n    \\\"type\\\": 0,\\n    \\\"name\\\": \\\"Tony Stark\\\",\\n    \\\"age\\\": 45,\\n    \\\"sex\\\": 0,\\n    \\\"birthDay\\\": \\\"\\\",\\n    \\\"regtime\\\": \\\"\\\"\\n  }\\n}\"}],\"name\":\"get user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[2]).toJson()!!.toUnixString()
        )
    }

    /**
     * use json5
     */
    fun testRequest2ItemWithJson5() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.ALL.name
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"ctrl\",\"name\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/ctrl/name\"}},\"response\":[{\"name\":\"current ctrl name-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"ctrl\",\"name\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/ctrl/name\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"\\\"\\\"\"}],\"name\":\"current ctrl name\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[0]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/greeting\"}},\"response\":[{\"name\":\"say hello-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/greeting\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"\\\"\\\"\"}],\"name\":\"say hello\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[1]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"\",\"equals\":true,\"description\":\"user id\"}],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/get/{id}\"}},\"response\":[{\"name\":\"get user info-Example\",\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"\",\"equals\":true,\"description\":\"user id\"}],\"host\":[\"{{test_default}}\"],\"raw\":\"{{test_default}}/user/get/{id}\"}},\"code\":200,\"_postman_previewlanguage\":\"json\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"success\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        /**\\n         * user type\\n         * 1 :administration\\n         * 2 :a person, an animal or a plant\\n         * 3 :Anonymous visitor\\n         */\\n        \\\"type\\\": 0,\\n        \\\"name\\\": \\\"Tony Stark\\\", //user name\\n        \\\"age\\\": 45, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\"}],\"name\":\"get user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[2]).toJson()!!.toUnixString()
        )
    }

    fun testItem2Request() {
        val api = GsonUtils.fromJson<HashMap<String, Any?>>(
            "{\"request\":{\"method\":\"GET\",\"header\":[{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"url\":{\"path\":[\"user\",\"get\"],\"query\":[{\"description\":\"user id type:long\",\"value\":\"0\",\"key\":\"id\"}],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/user/get?id=0\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"header\":[{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"url\":{\"path\":[\"user\",\"get\"],\"query\":[{\"description\":\"user id type:long\",\"value\":\"0\",\"key\":\"id\"}],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/user/get?id=0\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"cookie\":[],\"responseTime\":\"38\",\"name\":\"get user info by user ID-Example\",\"header\":[{\"name\":\"date\",\"description\":\"The date and time that the message was sent\",\"value\":\"Wed, 25 Aug 202113:06:00 GMT\",\"key\":\"date\"},{\"name\":\"server\",\"description\":\"A name for the server\",\"value\":\"Apache-Coyote/1.1\",\"key\":\"server\"},{\"name\":\"transfer-encoding\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\",\"value\":\"chunked\",\"key\":\"transfer-encoding\"},{\"name\":\"content-type\",\"value\":\"application/json;charset=UTF-8\",\"key\":\"content-type\"}],\"id\":\"88cc98be-e4a6-4fcd-a189-6d1a307cf334\",\"body\":\"{}\"}],\"name\":\"get user info by user ID\",\"id\":\"1e310fdd-e944-4a5e-83e2-d4b8778e1763\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}},{\"listen\":\"test\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}}]}"
        )!!
        val request = postmanFormatter.item2Request(api)!!
        assertEquals("get user info by user ID", request.name)
        assertEquals("GET", request.method)
        assertEquals(URL.of("user/get"), request.path)
        assertEquals(
            "[{\"name\":\"token\",\"value\":\"\",\"desc\":\"The authentication Token\"}]",
            request.headers.toJson()
        )
        assertEquals("[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id type:long\"}]", request.querys.toJson())

        val api2 = GsonUtils.fromJson<HashMap<String, Any?>>(
            "{\"request\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"application/json\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"raw\",\"options\":{\"raw\":{\"language\":\"json\"}},\"raw\":\"{\\n  \\\"id\\\": 0,\\n  \\\"type\\\": 0,\\n  \\\"name\\\": \\\"\\\",\\n  \\\"age\\\": 0,\\n  \\\"sex\\\": 0,\\n  \\\"birthDay\\\": \\\"\\\",\\n  \\\"regtime\\\": \\\"\\\"\\n}\"},\"url\":{\"path\":[\"user\",\"add\"],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/user/add\"}},\"response\":[{\"originalRequest\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"application/json\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"raw\",\"options\":{\"raw\":{\"language\":\"json\"}},\"raw\":\"{\\n  \\\"id\\\": 0,\\n  \\\"type\\\": 0,\\n  \\\"name\\\": \\\"\\\",\\n  \\\"age\\\": 0,\\n  \\\"sex\\\": 0,\\n  \\\"birthDay\\\": \\\"\\\",\\n  \\\"regtime\\\": \\\"\\\"\\n}\"},\"url\":{\"host\":[\"{{user}}\"],\"raw\":\"{{user}}\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"cookie\":[],\"responseTime\":\"63\",\"name\":\"create an user-Example\",\"header\":[{\"name\":\"date\",\"description\":\"The date and time that the message was sent\",\"value\":\"Wed, 25 Aug 202113:06:00 GMT\",\"key\":\"date\"},{\"name\":\"server\",\"description\":\"A name for the server\",\"value\":\"Apache-Coyote/1.1\",\"key\":\"server\"},{\"name\":\"transfer-encoding\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\",\"value\":\"chunked\",\"key\":\"transfer-encoding\"},{\"name\":\"content-type\",\"value\":\"application/json;charset=UTF-8\",\"key\":\"content-type\"}],\"id\":\"b5f59298-e871-491f-b082-35bd2589c7b2\",\"body\":\"{\\n  \\\"code\\\": 0,\\n  \\\"msg\\\": \\\"\\\",\\n  \\\"data\\\": {\\n    \\\"id\\\": 0,\\n    \\\"type\\\": 0,\\n    \\\"name\\\": \\\"\\\",\\n    \\\"age\\\": 0,\\n    \\\"sex\\\": 0,\\n    \\\"birthDay\\\": \\\"\\\",\\n    \\\"regtime\\\": \\\"\\\"\\n  }\\n}\"}],\"name\":\"create an user\",\"id\":\"d0142157-7f1f-49d8-9b73-a9e6c745deb4\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}},{\"listen\":\"test\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}}]}"
        )!!
        val request2 = postmanFormatter.item2Request(api2)!!
        assertEquals("create an user", request2.name)
        assertEquals("POST", request2.method)
        assertEquals(URL.of("user/add"), request2.path)
        assertEquals(
            "[{\"name\":\"Content-Type\",\"value\":\"application/json\"},{\"name\":\"token\",\"value\":\"\",\"desc\":\"The authentication Token\"}]",
            request2.headers.toJson()
        )
        assertNull(request2.querys)
        assertEquals(
            "{\"id\":0,\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}",
            request2.body.toJson()
        )
        assertEquals(
            "json",
            request2.bodyType
        )

        val api3 = GsonUtils.fromJson<HashMap<String, Any?>>(
            "{\"request\":{\"method\":\"PUT\",\"header\":[{\"type\":\"text\",\"value\":\"application/x-www-form-urlencoded\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"url\":{\"path\":[\"user\",\"update\"],\"query\":[{\"description\":\"user id\",\"value\":\"0\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"value\":\"\",\"key\":\"name\"},{\"description\":\"age of user\",\"value\":\"0\",\"key\":\"age\"},{\"description\":\"「deprecated」It's a secret\",\"value\":\"0\",\"key\":\"sex\"},{\"description\":\"birthDay of user\",\"value\":\"\",\"key\":\"birthDay\"},{\"description\":\"注册时间\",\"value\":\"\",\"key\":\"regtime\"}],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/user/update?id=0&type=0&name=&age=0&sex=0&birthDay=&regtime=\"}},\"response\":[{\"originalRequest\":{\"method\":\"PUT\",\"header\":[{\"type\":\"text\",\"value\":\"application/x-www-form-urlencoded\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"url\":{\"path\":[\"user\",\"update\"],\"query\":[{\"description\":\"user id\",\"value\":\"0\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"value\":\"\",\"key\":\"name\"},{\"description\":\"age of user\",\"value\":\"0\",\"key\":\"age\"},{\"description\":\"「deprecated」It's a secret\",\"value\":\"0\",\"key\":\"sex\"},{\"description\":\"birthDay of user\",\"value\":\"\",\"key\":\"birthDay\"},{\"description\":\"注册时间\",\"value\":\"\",\"key\":\"regtime\"}],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/user/update?id=0&type=0&name=&age=0&sex=0&birthDay=&regtime=\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"cookie\":[],\"responseTime\":\"66\",\"name\":\"update user info-Example\",\"header\":[{\"name\":\"date\",\"description\":\"The date and time that the message was sent\",\"value\":\"Wed, 25 Aug 202113:06:00 GMT\",\"key\":\"date\"},{\"name\":\"server\",\"description\":\"A name for the server\",\"value\":\"Apache-Coyote/1.1\",\"key\":\"server\"},{\"name\":\"transfer-encoding\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\",\"value\":\"chunked\",\"key\":\"transfer-encoding\"},{\"name\":\"content-type\",\"value\":\"application/json;charset=UTF-8\",\"key\":\"content-type\"}],\"id\":\"d638003a-a82a-44d9-ade2-d35d3461f314\",\"body\":\"{}\"}],\"name\":\"update user info\",\"id\":\"7235169a-6610-436e-bb1b-b79daf4f5ffb\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}},{\"listen\":\"test\",\"script\":{\"type\":\"text/javascript\",\"exec\":[\"var a = \\\"1\\\";\",\"var b = \\\"1\\\";\"]}}]}"
        )!!
        val request3 = postmanFormatter.item2Request(api3)!!
        assertEquals("update user info", request3.name)
        assertEquals("PUT", request3.method)
        assertEquals(URL.of("user/update"), request3.path)
        assertEquals(
            "[{\"name\":\"Content-Type\",\"value\":\"application/x-www-form-urlencoded\"},{\"name\":\"token\",\"value\":\"\",\"desc\":\"The authentication Token\"}]",
            request3.headers.toJson()
        )
        assertEquals(
            "[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\"},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\"},{\"name\":\"name\",\"value\":\"\",\"desc\":\"name of user\"},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"age of user\"},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"「deprecated」It's a secret\"},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"birthDay of user\"},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"注册时间\"}]",
            request3.querys.toJson()
        )

        val api4 = GsonUtils.fromJson<HashMap<String, Any?>>(
            "{\"protocolProfileBehavior\":{\"disableBodyPruning\":true},\"request\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"multipart/form-data\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"formdata\",\"formdata\":[{\"description\":\"profile img of user\",\"type\":\"file\",\"key\":\"profileImg\"},{\"description\":\"user id\",\"type\":\"text\",\"value\":\"0\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"name\"},{\"description\":\"age of user\",\"type\":\"text\",\"value\":\"0\",\"key\":\"age\"},{\"description\":\"「deprecated」It's a secret\",\"type\":\"text\",\"value\":\"0\",\"key\":\"sex\"},{\"description\":\"birthDay of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"birthDay\"},{\"description\":\"注册时间\",\"type\":\"text\",\"value\":\"\",\"key\":\"regtime\"}]},\"url\":{\"path\":[\"file\",\"add\"],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/file/add\"}},\"response\":[{\"originalRequest\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"multipart/form-data\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"formdata\",\"formdata\":[{\"description\":\"profile img of user\",\"type\":\"file\",\"key\":\"profileImg\"},{\"description\":\"user id\",\"type\":\"text\",\"value\":\"0\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"name\"},{\"description\":\"age of user\",\"type\":\"text\",\"value\":\"0\",\"key\":\"age\"},{\"description\":\"「deprecated」It's a secret\",\"type\":\"text\",\"value\":\"0\",\"key\":\"sex\"},{\"description\":\"birthDay of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"birthDay\"},{\"description\":\"注册时间\",\"type\":\"text\",\"value\":\"\",\"key\":\"regtime\"}]},\"url\":{\"path\":[\"file\",\"add\"],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/file/add\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"cookie\":[],\"responseTime\":\"99\",\"name\":\"create an user(上传profile img of user)-Example\",\"header\":[{\"name\":\"date\",\"description\":\"The date and time that the message was sent\",\"value\":\"Wed, 25 Aug 202120:46:39 GMT\",\"key\":\"date\"},{\"name\":\"server\",\"description\":\"A name for the server\",\"value\":\"Apache-Coyote/1.1\",\"key\":\"server\"},{\"name\":\"transfer-encoding\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\",\"value\":\"chunked\",\"key\":\"transfer-encoding\"},{\"name\":\"content-type\",\"value\":\"application/json;charset=UTF-8\",\"key\":\"content-type\"}],\"id\":\"a7ae6bbf-80e5-4a74-9491-49a314bdc9d7\",\"body\":\"{}\"}],\"name\":\"create an user\",\"id\":\"5b843baf-f9c7-4b25-b206-bf6aea45d4d3\"}"
        )!!
        val request4 = postmanFormatter.item2Request(api4)!!
        assertEquals("create an user", request4.name)
        assertEquals("POST", request4.method)
        assertEquals(URL.of("file/add"), request4.path)
        assertEquals(
            "[{\"name\":\"Content-Type\",\"value\":\"multipart/form-data\"},{\"name\":\"token\",\"value\":\"\",\"desc\":\"The authentication Token\"}]",
            request4.headers.toJson()
        )
        assertNull(request4.querys.toJson())
        assertEquals(
            "[{\"name\":\"profileImg\",\"desc\":\"profile img of user\",\"type\":\"file\"},{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\",\"type\":\"text\"},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\"},{\"name\":\"name\",\"value\":\"\",\"desc\":\"name of user\",\"type\":\"text\"},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"age of user\",\"type\":\"text\"},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"「deprecated」It's a secret\",\"type\":\"text\"},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"birthDay of user\",\"type\":\"text\"},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"注册时间\",\"type\":\"text\"}]",
            request4.formParams.toJson()
        )

        val api5 = GsonUtils.fromJson<HashMap<String, Any?>>(
            "{\"protocolProfileBehavior\":{\"disableBodyPruning\":true},\"request\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"multipart/form-data\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"urlencoded\",\"urlencoded\":[{\"description\":\"user id\",\"type\":\"text\",\"value\":\"\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"name\"}]},\"url\":{\"path\":[\"file\",\"add\"],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/file/add\"}},\"response\":[{\"originalRequest\":{\"method\":\"POST\",\"header\":[{\"type\":\"text\",\"value\":\"multipart/form-data\",\"key\":\"Content-Type\"},{\"description\":\"The authentication Token\",\"type\":\"text\",\"value\":\"\",\"key\":\"token\"}],\"body\":{\"mode\":\"formdata\",\"formdata\":[{\"src\":[],\"description\":\"profile img of user\",\"type\":\"file\",\"key\":\"profileImg\"},{\"description\":\"user id\",\"type\":\"text\",\"value\":\"0\",\"key\":\"id\"},{\"description\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\",\"value\":\"0\",\"key\":\"type\"},{\"description\":\"name of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"name\"},{\"description\":\"age of user\",\"type\":\"text\",\"value\":\"0\",\"key\":\"age\"},{\"description\":\"「deprecated」It's a secret\",\"type\":\"text\",\"value\":\"0\",\"key\":\"sex\"},{\"description\":\"birthDay of user\",\"type\":\"text\",\"value\":\"\",\"key\":\"birthDay\"},{\"description\":\"注册时间\",\"type\":\"text\",\"value\":\"\",\"key\":\"regtime\"}]},\"url\":{\"path\":[\"file\",\"add\"],\"host\":[\"{{user}}\"],\"raw\":\"{{user}}/file/add\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"cookie\":[],\"name\":\"create an user(上传profile img of user)-Example\",\"header\":[{\"name\":\"date\",\"description\":\"The date and time that the message was sent\",\"value\":\"Wed, 25 Aug 202120:46:39 GMT\",\"key\":\"date\"},{\"name\":\"server\",\"description\":\"A name for the server\",\"value\":\"Apache-Coyote/1.1\",\"key\":\"server\"},{\"name\":\"transfer-encoding\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\",\"value\":\"chunked\",\"key\":\"transfer-encoding\"},{\"name\":\"content-type\",\"value\":\"application/json;charset=UTF-8\",\"key\":\"content-type\"}],\"id\":\"28324d5d-94b9-468d-afee-35abd1f800f5\",\"body\":\"{}\"}],\"name\":\"create an user\",\"id\":\"2289716e-d401-4952-9208-7e343aa4b79d\"}"
        )!!
        val request5 = postmanFormatter.item2Request(api5)!!
        assertEquals("create an user", request5.name)
        assertEquals("POST", request5.method)
        assertEquals(URL.of("file/add"), request5.path)
        assertEquals(
            "[{\"name\":\"Content-Type\",\"value\":\"multipart/form-data\"},{\"name\":\"token\",\"value\":\"\",\"desc\":\"The authentication Token\"}]",
            request5.headers.toJson()
        )
        assertNull(request5.querys.toJson())
        assertEquals(
            "[{\"name\":\"id\",\"value\":\"\",\"desc\":\"user id\",\"type\":\"text\"},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :ADMIN\\n2 :MEMBER\\n3 :GUEST\",\"type\":\"text\"},{\"name\":\"name\",\"value\":\"\",\"desc\":\"name of user\",\"type\":\"text\"}]",
            request5.formParams.toJson()
        )

    }

    /**
     * use json-schema by default
     */
    fun testParseRequests() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.NONE.name
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        assertEquals(
            ResultLoader.load("testParseRequests"),
            GsonUtils.prettyJson(postmanFormatter.parseRequests(requests))
        )
    }

    /**
     * use json-schema by default
     */
    fun testParseRequestsWithWrapCollection() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.ALL.name
        settings.wrapCollection = true
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        assertEquals(
            ResultLoader.load("testParseRequestsWithWrapCollection"),
            GsonUtils.prettyJson(postmanFormatter.parseRequests(requests))
        )
    }

    /**
     * use json-schema by default
     */
    fun testParseRequestsToCollection() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.NONE.name
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        val collectionInfo = GsonUtils.fromJson<HashMap<String, Any?>>(
            ResultLoader.load("testParseRequestsToCollection-original-collection")
        )!!
        postmanFormatter.parseRequestsToCollection(collectionInfo, requests)
        assertEquals(
            ResultLoader.load("testParseRequestsToCollection"),
            GsonUtils.prettyJson(collectionInfo)
        )
    }

    fun testParsePath() {
        // Test case input
        val path = "/users/{userId}/orders/{orderId:int}/details"

        // Expected output
        val expectedSegments = listOf("users", ":userId", "orders", ":orderId", "details")

        // Call the function
        val actualSegments = PostmanFormatter.parsePath(path)

        // Assert the result
        assertEquals(expectedSegments, actualSegments)
    }
}