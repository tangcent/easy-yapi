package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.common.model.rawContentType
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [FeignRequestClassExporter]
 */
internal class FeignRequestClassExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userClientPsiClass: PsiClass

    private lateinit var primitiveUserClientPsiClass: PsiClass

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
        loadFile("spring/FeignClient.java")
        loadFile("feign/Body.java")
        loadFile("feign/Headers.java")
        loadFile("feign/Param.java")
        loadFile("feign/RequestLine.java")
        userClientPsiClass = loadClass("api/feign/UserClient.java")!!
        primitiveUserClientPsiClass = loadClass("api/feign/PrimitiveUserClient.java")!!
        settings.feignEnable = true

        //clear log
        LoggerCollector.getLog()
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, demo:\"123456\"}\n" +
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

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }

        builder.bind(ClassExporter::class) { it.with(FeignRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class) }
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
    }

    fun testExportFromUserClient() {
        settings.queryExpanded = true
        settings.formExpanded = true
        val requests = ArrayList<Request>()

        actionContext.withBoundary {
            classExporter.export(userClientPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("/user/index", request.path!!.url())
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userClientPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("/user/get/{id}", request.path!!.url())
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userClientPsiClass.methods[1], (request.resource as PsiResource).resource())
        }

        assertEquals(ResultLoader.load("testExportFromUserClient"), LoggerCollector.getLog().toUnixString())
    }

    fun testExportFromPrimitiveUserClientPsiClass() {
        settings.queryExpanded = true
        settings.formExpanded = true
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(primitiveUserClientPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("/primitive/user/add", request.path!!.url())
            assertEquals("create an user", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("POST", request.method)
            assertEquals("application/x-www-form-urlencoded", request.rawContentType())
            assertEquals(primitiveUserClientPsiClass.methods[0], (request.resource as PsiResource).resource())
            assertNull(request.body)
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":false,\"age\":false,\"sex\":false,\"birthDay\":false,\"regtime\":false},\"@default\":{\"id\":0,\"name\":\"tangcent\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"@demo\":{\"sex\":\"1\"},\"birthDay\":\"\",\"regtime\":\"\"}}",
                request.response!!.first().body.toJson()
            )
        }
        requests[1].let { request ->
            assertEquals("/primitive/user/list/{type}", request.path!!.url())
            assertEquals("list users", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("POST", request.method)
            assertEquals("application/json", request.rawContentType())
            request.paths!!.first().let {
                assertEquals("type", it.name)
                assertEquals(
                    "user type \n" +
                            "1 :administration\n" +
                            "2 :a person, an animal or a plant\n" +
                            "3 :Anonymous visitor", it.desc
                )
            }
            request.headers!!.first { it.name == "token" }.let {
                assertTrue(it.value.isNullOrEmpty())
                assertEquals("auth token", it.desc)
            }
            request.headers!!.first { it.name == "id" }.let {
                assertEquals("{id}", it.value)
                assertEquals("user id", it.desc)
            }
            assertEquals(primitiveUserClientPsiClass.methods[1], (request.resource as PsiResource).resource())
            assertEquals(
                "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":false,\"age\":false,\"sex\":false,\"birthDay\":false,\"regtime\":false},\"@default\":{\"id\":0,\"name\":\"tangcent\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"@demo\":{\"sex\":\"1\"},\"birthDay\":\"\",\"regtime\":\"\"}",
                request.body.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@required\":{\"code\":false,\"msg\":false,\"data\":false},\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":[{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":false,\"age\":false,\"sex\":false,\"birthDay\":false,\"regtime\":false},\"@default\":{\"id\":0,\"name\":\"tangcent\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"@demo\":{\"sex\":\"1\"},\"birthDay\":\"\",\"regtime\":\"\"}]}",
                request.response!!.first().body.toJson()
            )
        }

        assertEquals(
            ResultLoader.load("testExportFromPrimitiveUserClientPsiClass"),
            LoggerCollector.getLog().toUnixString()
        )
    }
}