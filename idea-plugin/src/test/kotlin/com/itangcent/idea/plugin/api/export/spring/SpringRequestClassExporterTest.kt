package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import junit.framework.Assert
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

    private lateinit var iuserApiPsiClass: PsiClass

    private lateinit var userApiImplPsiClass: PsiClass

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
        loadFile("spring/PutMapping.java")
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
        iuserApiPsiClass = loadClass("api/IUserApi.java")!!
        userApiImplPsiClass = loadClass("api/UserApiImpl.java")!!
        settings.inferEnable = true

        //clear log
        LoggerCollector.getLog()
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "ignore=#ignore\n" +
                "json.rule.field.name=@com.fasterxml.jackson.annotation.JsonProperty#value\n" +
                "field.required=@javax.validation.constraints.NotBlank\n" +
                "field.required=@javax.validation.constraints.NotNull\n" +
                "field.default.value=#default\n" +
                "field.mock=#mock\n" +
                "field.demo=#demo\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n" +
                "api.class.parse.before=groovy:logger.info(\"before parse class:\"+it)\n" +
                "api.class.parse.after=groovy:logger.info(\"after parse class:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.method.parse.after=groovy:logger.info(\"after parse method:\"+it)\n" +
                "api.param.parse.before=groovy:logger.info(\"before parse param:\"+it)\n" +
                "api.param.parse.after=groovy:logger.info(\"after parse param:\"+it)\n"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class) }
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
    }

    fun testExportFromUserCtrl() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

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

        assertEquals(ResultLoader.load("testExportFromUserCtrl"), LoggerCollector.getLog().toUnixString())
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
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\"}",
                request.response!![0].body.toJson()
            )
        }
        requests[7].let { request ->
            assertEquals(testCtrlPsiClass.methods[8], (request.resource as PsiResource).resource())
            assertEquals("return nested node", request.name)
            assertEquals("/test/return/node", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent.siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub[0].siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings[0].siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]}]}}",
                request.response!![0].body.toJson()
            )
        }
        requests[8].let { request ->
            assertEquals(testCtrlPsiClass.methods[9], (request.resource as PsiResource).resource())
            assertEquals("return root with nested nodes", request.name)
            assertEquals("/test/return/root", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].parent.siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].sub[0].siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].sub[0].id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].sub[0].code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].sub[0].parent.id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children[0].siblings[0].siblings[0].sub[0].parent.code\",\"value\":\"\",\"desc\":\"node code\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":\"\",\"@required\":{\"id\":false,\"children\":false},\"@comment\":{\"id\":\"primary key\",\"children\":\"sub nodes\"},\"children\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}]}]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}]}]}]}]}}",
                request.response!![0].body.toJson()
            )
        }
        requests[9].let { request ->
            assertEquals(testCtrlPsiClass.methods[10], (request.resource as PsiResource).resource())
            assertEquals("return customMap", request.name)
            assertEquals("/test/return/customMap", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"key\",\"value\":\"\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[10].let { request ->
            assertEquals(testCtrlPsiClass.methods[11], (request.resource as PsiResource).resource())
            assertEquals("user page query", request.name)
            assertEquals("/test/call/page/user", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"user.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"users[0].name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"t.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[11].let { request ->
            assertEquals(testCtrlPsiClass.methods[12], (request.resource as PsiResource).resource())
            assertEquals("user page query with ModelAttribute", request.name)
            assertEquals("/test/call/page/user/form", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[12].let { request ->
            assertEquals(testCtrlPsiClass.methods[13], (request.resource as PsiResource).resource())
            assertEquals("user page query with POST", request.name)
            assertEquals("/test/call/page/user/post", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"user.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"users[0].regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"t.regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[13].let { request ->
            assertEquals(testCtrlPsiClass.methods[14], (request.resource as PsiResource).resource())
            assertEquals("user page query with array", request.name)
            assertEquals("/test/call/page/user/array", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":[{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":false,\"age\":false,\"sex\":false,\"birthDay\":false,\"regtime\":false},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"tangcent\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}]}",
                request.response!![0].body.toJson()
            )
        }

        assertEquals(ResultLoader.load("testExportFromTestCtrlWithExpanded"), LoggerCollector.getLog().toUnixString())
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
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\"}",
                request.response!![0].body.toJson()
            )
        }
        requests[7].let { request ->
            assertEquals(testCtrlPsiClass.methods[8], (request.resource as PsiResource).resource())
            assertEquals("return nested node", request.name)
            assertEquals("/test/return/node", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"desc\":\"primary key\",\"required\":false,\"type\":\"text\"},{\"name\":\"code\",\"desc\":\"node code\",\"required\":false,\"type\":\"text\"},{\"name\":\"parent\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"sub\",\"desc\":\"sub nodes\",\"required\":false,\"type\":\"text\"},{\"name\":\"siblings\",\"desc\":\"siblings nodes\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]}]}}",
                request.response!![0].body.toJson()
            )
        }
        requests[8].let { request ->
            assertEquals(testCtrlPsiClass.methods[9], (request.resource as PsiResource).resource())
            assertEquals("return root with nested nodes", request.name)
            assertEquals("/test/return/root", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"value\":\"\",\"desc\":\"primary key\",\"required\":false},{\"name\":\"children\",\"value\":\"{\\n  \\\"id\\\": \\\"\\\",\\n  \\\"code\\\": \\\"\\\",\\n  \\\"parent\\\": {\\n    \\\"id\\\": \\\"\\\",\\n    \\\"code\\\": \\\"\\\",\\n    \\\"parent\\\": {\\n      \\\"id\\\": \\\"\\\",\\n      \\\"code\\\": \\\"\\\"\\n    },\\n    \\\"sub\\\": [\\n      {\\n        \\\"id\\\": \\\"\\\",\\n        \\\"code\\\": \\\"\\\",\\n        \\\"parent\\\": {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\"\\n        },\\n        \\\"sub\\\": []\\n      }\\n    ],\\n    \\\"siblings\\\": [\\n      {\\n        \\\"id\\\": \\\"\\\",\\n        \\\"code\\\": \\\"\\\",\\n        \\\"parent\\\": {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\"\\n        },\\n        \\\"sub\\\": [\\n          {\\n            \\\"id\\\": \\\"\\\",\\n            \\\"code\\\": \\\"\\\",\\n            \\\"parent\\\": {\\n              \\\"id\\\": \\\"\\\",\\n              \\\"code\\\": \\\"\\\"\\n            },\\n            \\\"sub\\\": []\\n          }\\n        ],\\n        \\\"siblings\\\": []\\n      }\\n    ]\\n  },\\n  \\\"sub\\\": [\\n    {\\n      \\\"id\\\": \\\"\\\",\\n      \\\"code\\\": \\\"\\\",\\n      \\\"parent\\\": {\\n        \\\"id\\\": \\\"\\\",\\n        \\\"code\\\": \\\"\\\"\\n      },\\n      \\\"sub\\\": [\\n        {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\",\\n          \\\"parent\\\": {\\n            \\\"id\\\": \\\"\\\",\\n            \\\"code\\\": \\\"\\\"\\n          }\\n        }\\n      ],\\n      \\\"siblings\\\": [\\n        {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\",\\n          \\\"parent\\\": {\\n            \\\"id\\\": \\\"\\\",\\n            \\\"code\\\": \\\"\\\"\\n          },\\n          \\\"sub\\\": [\\n            {\\n              \\\"id\\\": \\\"\\\",\\n              \\\"code\\\": \\\"\\\",\\n              \\\"parent\\\": {\\n                \\\"id\\\": \\\"\\\",\\n                \\\"code\\\": \\\"\\\"\\n              }\\n            }\\n          ]\\n        }\\n      ]\\n    }\\n  ],\\n  \\\"siblings\\\": [\\n    {\\n      \\\"id\\\": \\\"\\\",\\n      \\\"code\\\": \\\"\\\",\\n      \\\"parent\\\": {\\n        \\\"id\\\": \\\"\\\",\\n        \\\"code\\\": \\\"\\\"\\n      },\\n      \\\"sub\\\": [\\n        {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\",\\n          \\\"parent\\\": {\\n            \\\"id\\\": \\\"\\\",\\n            \\\"code\\\": \\\"\\\"\\n          }\\n        }\\n      ],\\n      \\\"siblings\\\": [\\n        {\\n          \\\"id\\\": \\\"\\\",\\n          \\\"code\\\": \\\"\\\",\\n          \\\"parent\\\": {\\n            \\\"id\\\": \\\"\\\",\\n            \\\"code\\\": \\\"\\\"\\n          },\\n          \\\"sub\\\": [\\n            {\\n              \\\"id\\\": \\\"\\\",\\n              \\\"code\\\": \\\"\\\",\\n              \\\"parent\\\": {\\n                \\\"id\\\": \\\"\\\",\\n                \\\"code\\\": \\\"\\\"\\n              }\\n            }\\n          ]\\n        }\\n      ]\\n    }\\n  ]\\n}\",\"desc\":\"sub nodes\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":\"\",\"@required\":{\"id\":false,\"children\":false},\"@comment\":{\"id\":\"primary key\",\"children\":\"sub nodes\"},\"children\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[]}],\"siblings\":[]}]},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}]}]}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}],\"siblings\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"},\"sub\":[{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\",\"parent\":{\"id\":\"\",\"@required\":{\"id\":false,\"code\":false,\"parent\":false,\"sub\":false,\"siblings\":false},\"@comment\":{\"id\":\"primary key\",\"code\":\"node code\",\"sub\":\"sub nodes\",\"siblings\":\"siblings nodes\"},\"code\":\"\"}}]}]}]}]}}",
                request.response!![0].body.toJson()
            )
        }
        requests[9].let { request ->
            assertEquals(testCtrlPsiClass.methods[10], (request.resource as PsiResource).resource())
            assertEquals("return customMap", request.name)
            assertEquals("/test/return/customMap", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"key\",\"value\":\"\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[10].let { request ->
            assertEquals(testCtrlPsiClass.methods[11], (request.resource as PsiResource).resource())
            assertEquals("user page query", request.name)
            assertEquals("/test/call/page/user", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"value\":\"\",\"desc\":\"\",\"required\":false},{\"name\":\"user\",\"value\":\"{\\n  \\\"id\\\": 0,\\n  \\\"type\\\": 0,\\n  \\\"name\\\": \\\"tangcent\\\",\\n  \\\"age\\\": 0,\\n  \\\"sex\\\": 0,\\n  \\\"birthDay\\\": \\\"\\\",\\n  \\\"regtime\\\": \\\"\\\"\\n}\",\"desc\":\"\",\"required\":false},{\"name\":\"users\",\"value\":\"{\\n  \\\"id\\\": 0,\\n  \\\"type\\\": 0,\\n  \\\"name\\\": \\\"tangcent\\\",\\n  \\\"age\\\": 0,\\n  \\\"sex\\\": 0,\\n  \\\"birthDay\\\": \\\"\\\",\\n  \\\"regtime\\\": \\\"\\\"\\n}\",\"desc\":\"\",\"required\":false},{\"name\":\"t\",\"value\":\"{\\n  \\\"id\\\": 0,\\n  \\\"type\\\": 0,\\n  \\\"name\\\": \\\"tangcent\\\",\\n  \\\"age\\\": 0,\\n  \\\"sex\\\": 0,\\n  \\\"birthDay\\\": \\\"\\\",\\n  \\\"regtime\\\": \\\"\\\"\\n}\",\"desc\":\"\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[11].let { request ->
            assertEquals(testCtrlPsiClass.methods[12], (request.resource as PsiResource).resource())
            assertEquals("user page query with ModelAttribute", request.name)
            assertEquals("/test/call/page/user/form", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[12].let { request ->
            assertEquals(testCtrlPsiClass.methods[13], (request.resource as PsiResource).resource())
            assertEquals("user page query with POST", request.name)
            assertEquals("/test/call/page/user/post", request.path.toString())
            assertEquals("POST", request.method)
            assertEquals(
                "[{\"name\":\"size\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"user\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"users\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"t\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
                request.formParams.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"\":\"\"}}",
                request.response!![0].body.toJson()
            )
        }
        requests[13].let { request ->
            assertEquals(testCtrlPsiClass.methods[14], (request.resource as PsiResource).resource())
            assertEquals("user page query with array", request.name)
            assertEquals("/test/call/page/user/array", request.path.toString())
            assertEquals("GET", request.method)
            assertEquals(
                "[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\\n1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"required\":false},{\"name\":\"name\",\"value\":\"tangcent\",\"desc\":\"user name\",\"required\":false},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false}]",
                request.querys.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":[{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":false,\"age\":false,\"sex\":false,\"birthDay\":false,\"regtime\":false},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"tangcent\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}]}",
                request.response!![0].body.toJson()
            )
        }

        assertEquals(ResultLoader.load("testExportFromTestCtrlWithOutExpanded"),
            LoggerCollector.getLog().toUnixString())
    }

    fun testExportFromUserApi() {
        val requests = ArrayList<Request>()
        classExporter.export(userApiImplPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("loginAuth", request.name)
            assertEquals("user/auth/loginAuth", request.path!!.url())
            assertNull(request.desc)
            assertEquals("POST", request.method)
            assertEquals(iuserApiPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("A default api", request.name)
            assertEquals("user/default", request.path!!.url())
            Assert.assertEquals("It is not necessary to implement it", request.desc)
            assertEquals("POST", request.method)
            assertEquals(iuserApiPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        assertEquals(ResultLoader.load("testExportFromUserApi"), LoggerCollector.getLog().toUnixString())
    }
}