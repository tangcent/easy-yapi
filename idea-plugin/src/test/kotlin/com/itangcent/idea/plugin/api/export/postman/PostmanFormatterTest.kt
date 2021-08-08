package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.common.utils.asDate
import com.itangcent.common.utils.formatDate
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.PostmanWorkspaceChecker
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.condition.OS

/**
 * Test case of [PostmanFormatter]
 */
internal class PostmanFormatterTest : PostmanSpringClassExporterBaseTest() {

    @Inject
    private lateinit var postmanFormatter: PostmanFormatter

    private val settings = Settings()

    private val date = 1618124194123L.asDate().formatDate("EEE, dd MMM yyyyHH:mm:ss 'GMT'")

    override fun beforeBind() {
        super.beforeBind()
        settings.inferEnable = true
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(1618124194123L))
        }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
        val postmanWorkspaceChecker = object : PostmanWorkspaceChecker {
            override fun checkWorkspace(workspace: String): Boolean {
                return true
            }
        }
        builder.bindInstance(PostmanWorkspaceChecker::class, postmanWorkspaceChecker)
        builder.bindInstance(PostmanApiHelper::class, PostmanCachedApiHelper())
    }

    override fun afterBind() {
        super.afterBind()
        postmanFormatter.responseTimeGenerator = { 50 }
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
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"say hello-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset\\u003dUTF-8\"}],\"body\":\"\",\"status\":\"OK\"}],\"name\":\"say hello\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[0]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"get user info-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset\\u003dUTF-8\"}],\"body\":\"{\\n  \\\"code\\\": 0,\\n  \\\"msg\\\": \\\"success\\\",\\n  \\\"data\\\": {\\n    \\\"id\\\": 0,\\n    \\\"type\\\": 0,\\n    \\\"name\\\": \\\"Tony Stark\\\",\\n    \\\"age\\\": 45,\\n    \\\"sex\\\": 0,\\n    \\\"birthDay\\\": \\\"\\\",\\n    \\\"regtime\\\": \\\"\\\"\\n  }\\n}\",\"status\":\"OK\"}],\"name\":\"get user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[1]).toJson()!!.toUnixString()
        )
    }

    /**
     * use json5
     */
    fun testRequest2ItemWithJson5() {
        settings.postmanJson5FormatType = PostmanJson5FormatType.ALL.name
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"say hello-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset\\u003dUTF-8\"}],\"body\":\"\\\"\\\"\",\"status\":\"OK\"}],\"name\":\"say hello\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[0]).toJson()!!.toUnixString()
        )
        assertEquals(
            "{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"get user info-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$date\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset\\u003dUTF-8\"}],\"body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"success\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        /**\\n         * user type\\n         * 1 :administration\\n         * 2 :a person, an animal or a plant\\n         * 3 :Anonymous visitor\\n         */\\n        \\\"type\\\": 0,\\n        \\\"name\\\": \\\"Tony Stark\\\", //user name\\n        \\\"age\\\": 45, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\",\"status\":\"OK\"}],\"name\":\"get user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}",
            postmanFormatter.request2Item(requests[1]).toJson()!!.toUnixString()
        )
    }
}