package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.psi.PsiFile
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.asDate
import com.itangcent.common.utils.formatDate
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompositeClassExporter
import com.itangcent.idea.plugin.api.export.generic.GenericRequestClassExporter
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.GsonExUtils
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.*
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit.LOCAL_TIME_GMT_STRING
import com.itangcent.test.TimeZoneKit.LOCAL_TIME_RAW_STRING
import com.itangcent.test.TimeZoneKit.STANDARD_TIME
import com.itangcent.test.mock
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.condition.OS
import org.mockito.Mockito
import org.mockito.kotlin.eq
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

/**
 * Test case of [PostmanApiExporter]
 */
internal abstract class PostmanApiExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    protected lateinit var postmanApiExporter: PostmanApiExporter

    @Inject
    protected lateinit var postmanFormatter: PostmanFormatter

    protected lateinit var userCtrlPsiFile: PsiFile

    protected lateinit var userClientPsiFile: PsiFile

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Void::class)
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadSource(java.lang.Long::class)
        loadSource(Collection::class)
        loadSource(Map::class)
        loadSource(List::class)
        loadSource(LinkedList::class)
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        loadSource(HashMap::class)
        loadFile("annotation/Public.java")
        loadFile("constant/UserType.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
        userClientPsiFile = loadFile("client/UserClient.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(CompositeClassExporter::class).singleton() }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(STANDARD_TIME))
        }
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.common.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    override fun afterBind() {
        super.afterBind()
        postmanFormatter.responseTimeGenerator = { 50 }
    }

    class SpringPostmanApiExporterTest : PostmanApiExporterTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)
            builder.bindInstance(
                "AVAILABLE_CLASS_EXPORTER",
                arrayOf<Any>(
                    SpringRequestClassExporter::class,
                    GenericRequestClassExporter::class
                )
            )
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile)
            builder.mock(PostmanApiHelper::class)
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()!!.trim().toUnixString()
            )
        }
    }

    class ModeCopyPostmanApiExporterTest : PostmanApiExporterTest() {

        private var createdCollection: Any? = null

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)
            builder.bindInstance(
                "AVAILABLE_CLASS_EXPORTER",
                arrayOf<Any>(
                    SpringRequestClassExporter::class,
                    GenericRequestClassExporter::class
                )
            )
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.postmanToken = "token-123456789"
                    settings.postmanExportMode = PostmanExportMode.COPY.name
                    settings.postmanWorkspace = "workspace-123456789"
                }))
            }
            builder.workAt(userCtrlPsiFile)
            builder.mock(PostmanApiHelper::class) {
                Mockito.`when`(it.createCollection(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
                    .thenAnswer { invocationOnMock ->
                        createdCollection = invocationOnMock.getArgument(0)
                        return@thenAnswer hashMapOf("name" to "collection-123456")
                    }
            }
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(createdCollection).toUnixString()
            )
        }
    }

    class ModeUpdatePostmanApiExporterTest : PostmanApiExporterTest() {

        private val updatedCollections = HashMap<String, Any>()
        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)
            builder.bindInstance(
                "AVAILABLE_CLASS_EXPORTER",
                arrayOf<Any>(
                    SpringRequestClassExporter::class,
                    GenericRequestClassExporter::class
                )
            )
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.postmanToken = "token-123456789"
                    settings.postmanExportMode = PostmanExportMode.UPDATE.name
                    settings.postmanWorkspace = "workspace-123456789"
                    settings.postmanCollections = "test_default=collection-123456789"
                }))
            }
            builder.workAt(userCtrlPsiFile)
            builder.mock(PostmanApiHelper::class) {
                Mockito.`when`(it.getCollectionInfo(eq("collection-123456789")))
                    .thenReturn(
                        GsonExUtils.fromJson<HashMap<String, Any?>>(
                            "{\"item\":[{\"item\":[{\"request\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"not update anything\",\"header\":[],\"url\":{\"path\":[\"user\",\"greeting\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/greeting\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"say hello-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$LOCAL_TIME_GMT_STRING\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"\\\"\\\"\",\"status\":\"OK\"}],\"name\":\"say hello, hello!\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]},{\"request\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"response\":[{\"originalRequest\":{\"method\":\"GET\",\"description\":\"\",\"header\":[{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"url\":{\"path\":[\"user\",\"get\",\":id\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/get/{id}\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"get user info-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$LOCAL_TIME_GMT_STRING\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"success\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        /**\\n         * user type\\n         * 1 :administration\\n         * 2 :a person, an animal or a plant\\n         * 3 :Anonymous visitor\\n         */\\n        \\\"type\\\": 0,\\n        \\\"name\\\": \\\"Tony Stark\\\", //user name\\n        \\\"age\\\": 45, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\",\"status\":\"OK\"}],\"name\":\"get user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]},{\"request\":{\"method\":\"POST\",\"description\":\"create a new user\",\"header\":[{\"key\":\"Content-Type\",\"value\":\"application/json\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"body\":{\"mode\":\"raw\",\"options\":{\"raw\":{\"language\":\"json\"}},\"raw\":\"{\\n    \\\"id\\\": 0, //user id\\n    /**\\n     * user type\\n     * 1 :administration\\n     * 2 :a person, an animal or a plant\\n     * 3 :Anonymous visitor\\n     */\\n    \\\"type\\\": 0,\\n    \\\"name\\\": \\\"\\\", //user name\\n    \\\"age\\\": 0, //user age\\n    \\\"sex\\\": 0,\\n    \\\"birthDay\\\": \\\"\\\", //user birthDay\\n    \\\"regtime\\\": \\\"\\\" //user regtime\\n}\"},\"url\":{\"path\":[\"user\",\"create\"],\"query\":[{\"key\":\"id\",\"value\":\"0\",\"equals\":true,\"description\":\"user id\"}],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/add\"}},\"response\":[{\"originalRequest\":{\"method\":\"POST\",\"description\":\"\",\"header\":[{\"key\":\"Content-Type\",\"value\":\"application/json\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"body\":{\"mode\":\"raw\",\"options\":{\"raw\":{\"language\":\"json\"}},\"raw\":\"{\\n    \\\"id\\\": 0, //user id\\n    /**\\n     * user type\\n     * 1 :administration\\n     * 2 :a person, an animal or a plant\\n     * 3 :Anonymous visitor\\n     */\\n    \\\"type\\\": 0,\\n    \\\"name\\\": \\\"\\\", //user name\\n    \\\"age\\\": 0, //user age\\n    \\\"sex\\\": 0,\\n    \\\"birthDay\\\": \\\"\\\", //user birthDay\\n    \\\"regtime\\\": \\\"\\\" //user regtime\\n}\"},\"url\":{\"query\":[],\"host\":\"{{test_default}}\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"create new user-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$LOCAL_TIME_GMT_STRING\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        /**\\n         * user type\\n         * 1 :administration\\n         * 2 :a person, an animal or a plant\\n         * 3 :Anonymous visitor\\n         */\\n        \\\"type\\\": 0,\\n        \\\"name\\\": \\\"\\\", //user name\\n        \\\"age\\\": 0, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\",\"status\":\"OK\"}],\"name\":\"create new user\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]},{\"request\":{\"method\":\"POST\",\"description\":\"\",\"header\":[{\"key\":\"Content-Type\",\"value\":\"multipart/form-data\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"body\":{\"mode\":\"formdata\",\"formdata\":[{\"key\":\"id\",\"type\":\"text\",\"description\":\"user id\"},{\"key\":\"type\",\"type\":\"text\",\"description\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\"},{\"key\":\"name\",\"type\":\"text\",\"description\":\"user name\"},{\"key\":\"age\",\"type\":\"text\",\"description\":\"user age\"},{\"key\":\"sex\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"birthDay\",\"type\":\"text\",\"description\":\"user birthDay\"},{\"key\":\"regtime\",\"type\":\"text\",\"description\":\"user regtime\"}]},\"url\":{\"path\":[\"user\",\"update\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/update\"}},\"response\":[{\"originalRequest\":{\"method\":\"PUT\",\"description\":\"\",\"header\":[{\"key\":\"Content-Type\",\"value\":\"multipart/form-data\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"token\",\"value\":\"\",\"type\":\"text\",\"description\":\"auth token\"}],\"body\":{\"mode\":\"formdata\",\"formdata\":[{\"key\":\"id\",\"type\":\"text\",\"description\":\"user id\"},{\"key\":\"type\",\"type\":\"text\",\"description\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\"},{\"key\":\"name\",\"type\":\"text\",\"description\":\"user name\"},{\"key\":\"age\",\"type\":\"text\",\"description\":\"user age\"},{\"key\":\"sex\",\"type\":\"text\",\"description\":\"\"},{\"key\":\"birthDay\",\"type\":\"text\",\"description\":\"user birthDay\"},{\"key\":\"regtime\",\"type\":\"text\",\"description\":\"user regtime\"}]},\"url\":{\"path\":[\"user\",\"update\"],\"query\":[],\"host\":\"{{test_default}}\",\"raw\":\"{{test_default}}/user/update\"}},\"_postman_previewlanguage\":\"json\",\"code\":200,\"_postman_previewtype\":\"text\",\"responseTime\":50,\"name\":\"update user info-Example\",\"header\":[{\"name\":\"date\",\"key\":\"date\",\"value\":\"$LOCAL_TIME_GMT_STRING\",\"description\":\"The date and time that the message was sent\"},{\"name\":\"server\",\"key\":\"server\",\"value\":\"Apache-Coyote/1.1\",\"description\":\"A name for the server\"},{\"name\":\"transfer-encoding\",\"key\":\"transfer-encoding\",\"value\":\"chunked\",\"description\":\"The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.\"},{\"name\":\"content-type\",\"key\":\"content-type\",\"value\":\"application/json;charset=UTF-8\"}],\"body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        /**\\n         * user type\\n         * 1 :administration\\n         * 2 :a person, an animal or a plant\\n         * 3 :Anonymous visitor\\n         */\\n        \\\"type\\\": 0,\\n        \\\"name\\\": \\\"\\\", //user name\\n        \\\"age\\\": 0, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\",\"status\":\"OK\"}],\"name\":\"update user info\",\"event\":[{\"listen\":\"prerequest\",\"script\":{\"exec\":[\"pm.environment.set(\\\"token\\\", \\\"123456\\\");\"],\"type\":\"text/javascript\"}},{\"listen\":\"test\",\"script\":{\"exec\":[\"pm.test(\\\"Successful POST request\\\", function () {\",\"pm.expect(pm.response.code).to.be.oneOf([201,202]);\",\"});\"],\"type\":\"text/javascript\"}}]}],\"name\":\"apis about user\",\"description\":\"apis about user\\naccess user info\"}],\"info\":{\"schema\":\"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\",\"name\":\"test_default-$LOCAL_TIME_RAW_STRING\",\"description\":\"exported at 2021-08-31 22:04:41\"}}"
                        )!!
                    )
                Mockito.`when`(it.updateCollection(any("123"), any(hashMapOf())))
                    .thenAnswer { invocationOnMock ->
                        updatedCollections[invocationOnMock.getArgument(0)] = invocationOnMock.getArgument(1)
                        return@thenAnswer true
                    }
            }
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(updatedCollections["collection-123456789"]).toUnixString()
            )
        }
    }

    class DirectorySpringPostmanApiExporterTest : PostmanApiExporterTest() {

        override fun beforeBind() {
            super.beforeBind()
            loadFile("api/TestCtrl.java")
        }

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)
            builder.bindInstance(
                "AVAILABLE_CLASS_EXPORTER",
                arrayOf<Any>(
                    SpringRequestClassExporter::class,
                    GenericRequestClassExporter::class
                )
            )
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile.parent!!)
            builder.mock(PostmanApiHelper::class)
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()!!.trim().toUnixString(),
            )
        }
    }
}