package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [SpringRequestClassExporter]
 */
internal class SpringRequestClassExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userCtrlPsiClass: PsiClass

    private lateinit var testCtrlPsiClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(Void::class)
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
        loadFile("model/Node.java")
        loadFile("model/Root.java")
        loadFile("model/CustomMap.java")
        loadFile("model/PageRequest.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        testCtrlPsiClass = loadClass("api/TestCtrl.java")!!
        settings.inferEnable=true
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.common.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "ignore=#ignore\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date are parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }


    fun testExportFromUserCtrl() {
        settings.queryExpanded = true
        settings.formExpanded = true
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
    }

    fun testExportFromTestCtrlWithExpanded() {
        settings.queryExpanded = true
        settings.formExpanded = true
        val requests = ArrayList<Request>()
        classExporter.export(testCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals(testCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())
            assertEquals("test RequestHeader", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/header", request.path.toString())
            assertEquals("GET", request.method)
            assertNotNull(request.headers)
            val headers = request.headers!!
            assertEquals("x-token", headers[0].name)
            assertEquals("", headers[0].value)
            assertEquals("input token", headers[0].desc)
            assertEquals(true, headers[0].required)
            assertEquals("token", headers[1].name)
            assertEquals("", headers[1].value)
            assertEquals("auth token", headers[1].desc)
            assertEquals(true, headers[1].required)
        }
        requests[1].let { request ->
            assertEquals(testCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())
            assertEquals("test query with array parameters", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/arrays", request.path.toString())
            assertEquals("GET", request.method)
            val querys = request.querys
            assertNotNull(querys)
            querys!!
            assertEquals("string", querys[0].name)
            assertEquals("", querys[0].value)
            assertEquals("string array", querys[0].desc)
            assertEquals(false, querys[0].required)
            assertEquals("int", querys[1].name)
            assertEquals("1", querys[1].value)
            assertEquals("integer array", querys[1].desc)
            assertEquals(true, querys[1].required)
        }
        requests[2].let { request ->
            assertEquals(testCtrlPsiClass.methods[3], (request.resource as PsiResource).resource())
            assertEquals("test query with javax.servlet.http.HttpServletRequest", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/httpServletRequest", request.path.toString())
            assertEquals("GET", request.method)
        }
        requests[3].let { request ->
            assertEquals(testCtrlPsiClass.methods[4], (request.resource as PsiResource).resource())
            assertEquals("test query with javax.servlet.http.HttpServletResponse", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/httpServletResponse", request.path.toString())
            assertEquals("GET", request.method)
        }
        requests[4].let { request ->
            assertEquals(testCtrlPsiClass.methods[5], (request.resource as PsiResource).resource())
            assertEquals("test api return void", request.name)
            assertEquals("/test/return/void", request.path.toString())
            assertEquals("GET", request.method)
            assertNull(request.response!![0].body)
        }
        requests[5].let { request ->
            assertEquals(testCtrlPsiClass.methods[6], (request.resource as PsiResource).resource())
            assertEquals("test api return Void", request.name)
            assertEquals("/test/return/Void", request.path.toString())
            assertEquals("GET", request.method)
            assertNull(request.response!![0].body)
        }
        requests[6].let { request ->
            assertEquals(testCtrlPsiClass.methods[7], (request.resource as PsiResource).resource())
            assertEquals("test api return Result<Void>", request.name)
            assertEquals("/test/return/result/Void", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\"}",
                request.response!![0].body.toJson())
        }
        requests[7].let { request ->
            assertEquals(testCtrlPsiClass.methods[8], (request.resource as PsiResource).resource())
            assertEquals("return nested node", request.name)
            assertEquals("/test/return/node", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"value\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"children[0].value\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"value\":0,\"@comment\":{},\"parent\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{}}]}}",
                request.response!![0].body.toJson())
        }
        requests[8].let { request ->
            assertEquals(testCtrlPsiClass.methods[9], (request.resource as PsiResource).resource())
            assertEquals("return root with nested nodes", request.name)
            assertEquals("/test/return/root", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"value\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"children[0].value\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"children[0].children[0].value\",\"value\":\"0\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"value\":0,\"@comment\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{}}]}]}}",
                request.response!![0].body.toJson())
        }
        requests[9].let { request ->
            assertEquals(testCtrlPsiClass.methods[10], (request.resource as PsiResource).resource())
            assertEquals("return customMap", request.name)
            assertEquals("/test/return/customMap", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"key\",\"value\":\"\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[10].let { request ->
            assertEquals(testCtrlPsiClass.methods[11], (request.resource as PsiResource).resource())
            assertEquals("user page query", request.name)
            assertEquals("/test/call/page/user", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"user.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"users[0].name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"t.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[11].let { request ->
            assertEquals(testCtrlPsiClass.methods[12], (request.resource as PsiResource).resource())
            assertEquals("user page query with ModelAttribute", request.name)
            assertEquals("/test/call/page/user/form", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[12].let { request ->
            assertEquals(testCtrlPsiClass.methods[13], (request.resource as PsiResource).resource())
            assertEquals("user page query with POST", request.name)
            assertEquals("/test/call/page/user/post", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[13].let { request ->
            assertEquals(testCtrlPsiClass.methods[14], (request.resource as PsiResource).resource())
            assertEquals("user page query with array", request.name)
            assertEquals("/test/call/page/user/array", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":[{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}]}",
                request.response!![0].body.toJson())
        }
    }

    fun testExportFromTestCtrlWithOutExpanded() {
        settings.queryExpanded = false
        settings.formExpanded = false
        val requests = ArrayList<Request>()
        classExporter.export(testCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals(testCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())
            assertEquals("test RequestHeader", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/header", request.path.toString())
            assertEquals("GET", request.method)
            assertNotNull(request.headers)
            val headers = request.headers!!
            assertEquals("x-token", headers[0].name)
            assertEquals("", headers[0].value)
            assertEquals("input token", headers[0].desc)
            assertEquals(true, headers[0].required)
            assertEquals("token", headers[1].name)
            assertEquals("", headers[1].value)
            assertEquals("auth token", headers[1].desc)
            assertEquals(true, headers[1].required)
        }
        requests[1].let { request ->
            assertEquals(testCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())
            assertEquals("test query with array parameters", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/arrays", request.path.toString())
            assertEquals("GET", request.method)
            val querys = request.querys
            assertNotNull(querys)
            querys!!
            assertEquals("string", querys[0].name)
            assertEquals("", querys[0].value)
            assertEquals("string array", querys[0].desc)
            assertEquals(false, querys[0].required)
            assertEquals("int", querys[1].name)
            assertEquals("1", querys[1].value)
            assertEquals("integer array", querys[1].desc)
            assertEquals(true, querys[1].required)
        }
        requests[2].let { request ->
            assertEquals(testCtrlPsiClass.methods[3], (request.resource as PsiResource).resource())
            assertEquals("test query with javax.servlet.http.HttpServletRequest", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/httpServletRequest", request.path.toString())
            assertEquals("GET", request.method)
        }
        requests[3].let { request ->
            assertEquals(testCtrlPsiClass.methods[4], (request.resource as PsiResource).resource())
            assertEquals("test query with javax.servlet.http.HttpServletResponse", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("/test/httpServletResponse", request.path.toString())
            assertEquals("GET", request.method)
        }
        requests[4].let { request ->
            assertEquals(testCtrlPsiClass.methods[5], (request.resource as PsiResource).resource())
            assertEquals("test api return void", request.name)
            assertEquals("/test/return/void", request.path.toString())
            assertEquals("GET", request.method)
            assertNull(request.response!![0].body)
        }
        requests[5].let { request ->
            assertEquals(testCtrlPsiClass.methods[6], (request.resource as PsiResource).resource())
            assertEquals("test api return Void", request.name)
            assertEquals("/test/return/Void", request.path.toString())
            assertEquals("GET", request.method)
            assertNull(request.response!![0].body)
        }
        requests[6].let { request ->
            assertEquals(testCtrlPsiClass.methods[7], (request.resource as PsiResource).resource())
            assertEquals("test api return Result<Void>", request.name)
            assertEquals("/test/return/result/Void", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\"}",
                request.response!![0].body.toJson())
        }
        requests[7].let { request ->
            assertEquals(testCtrlPsiClass.methods[8], (request.resource as PsiResource).resource())
            assertEquals("return nested node", request.name)
            assertEquals("/test/return/node", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"value\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"children\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"value\":0,\"@comment\":{},\"parent\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{}}]}}",
                request.response!![0].body.toJson())
        }
        requests[8].let { request ->
            assertEquals(testCtrlPsiClass.methods[9], (request.resource as PsiResource).resource())
            assertEquals("return root with nested nodes", request.name)
            assertEquals("/test/return/root", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"value\",\"desc\":\"\",\"required\":false},{\"name\":\"children\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"value\":0,\"@comment\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{},\"children\":[{\"value\":0,\"@comment\":{},\"parent\":{}}]}]}}",
                request.response!![0].body.toJson())
        }
        requests[9].let { request ->
            assertEquals(testCtrlPsiClass.methods[10], (request.resource as PsiResource).resource())
            assertEquals("return customMap", request.name)
            assertEquals("/test/return/customMap", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"key\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[10].let { request ->
            assertEquals(testCtrlPsiClass.methods[11], (request.resource as PsiResource).resource())
            assertEquals("user page query", request.name)
            assertEquals("/test/call/page/user", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"size\",\"desc\":\"\",\"required\":false},{\"name\":\"user\",\"desc\":\"\",\"required\":false},{\"name\":\"users\",\"desc\":\"\",\"required\":false},{\"name\":\"t\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[11].let { request ->
            assertEquals(testCtrlPsiClass.methods[12], (request.resource as PsiResource).resource())
            assertEquals("user page query with ModelAttribute", request.name)
            assertEquals("/test/call/page/user/form", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"size\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[12].let { request ->
            assertEquals(testCtrlPsiClass.methods[13], (request.resource as PsiResource).resource())
            assertEquals("user page query with POST", request.name)
            assertEquals("/test/call/page/user/post", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals("[{\"name\":\"size\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson())
        }
        requests[13].let { request ->
            assertEquals(testCtrlPsiClass.methods[14], (request.resource as PsiResource).resource())
            assertEquals("user page query with array", request.name)
            assertEquals("/test/call/page/user/array", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\",\"required\":false},{\"name\":\"type\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"name\",\"desc\":\"user name\",\"required\":false},{\"name\":\"age\",\"desc\":\"user age\",\"required\":false},{\"name\":\"sex\",\"desc\":\"\",\"required\":false},{\"name\":\"birthDay\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"regtime\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson())
            assertEquals("{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":[{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}]}",
                request.response!![0].body.toJson())
        }
    }
}