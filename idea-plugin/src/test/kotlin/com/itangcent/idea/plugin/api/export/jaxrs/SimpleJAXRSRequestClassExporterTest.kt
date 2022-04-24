package com.itangcent.idea.plugin.api.export.jaxrs

import com.google.inject.Inject
import com.intellij.psi.PsiClass
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
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


/**
 * Test case of [SimpleJAXRSRequestClassExporter]
 */
internal class SimpleJAXRSRequestClassExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userResourcePsiClass: PsiClass

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
        loadFile("jaxrs/BeanParam.java")
        loadFile("jaxrs/CookieParam.java")
        loadFile("jaxrs/DefaultValue.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/HEAD.java")
        loadFile("jaxrs/HeaderParam.java")
        loadFile("jaxrs/HttpMethod.java")
        loadFile("jaxrs/OPTIONS.java")
        loadFile("jaxrs/PATCH.java")
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("api/jaxrs/MyGet.java")
        loadFile("api/jaxrs/MyPut.java")
        loadFile("api/jaxrs/UserDTO.java")
        userResourcePsiClass = loadClass("api/jaxrs/UserResource.java")!!
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

        builder.bind(ClassExporter::class) { it.with(SimpleJAXRSRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class) }
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
    }

    fun testExportFromUserResource() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        settings.queryExpanded = true
        settings.formExpanded = true
        val requests = ArrayList<Request>()
        classExporter.export(userResourcePsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("update user name", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[1], (request.resource as PsiResource).resource())
        }

        requests[2].let { request ->
            assertEquals("get user info", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[2], (request.resource as PsiResource).resource())
        }

        requests[3].let { request ->
            assertEquals("get detail for a single user", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[3], (request.resource as PsiResource).resource())
        }

        requests[4].let { request ->
            assertEquals("create an user", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[4], (request.resource as PsiResource).resource())
        }

        requests[5].let { request ->
            assertEquals("update user info", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[5], (request.resource as PsiResource).resource())
        }

        requests[6].let { request ->
            assertEquals("list user of special type", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[6], (request.resource as PsiResource).resource())
        }

        requests[7].let { request ->
            assertEquals("list user of special type", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[7], (request.resource as PsiResource).resource())
        }

        requests[8].let { request ->
            assertEquals("delete user", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[8], (request.resource as PsiResource).resource())
        }

        requests[9].let { request ->
            assertEquals("get current user type", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[9], (request.resource as PsiResource).resource())
        }

        requests[10].let { request ->
            assertEquals("get all user type", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[10], (request.resource as PsiResource).resource())
        }

        requests[11].let { request ->
            assertEquals("update user name", request.name)
            assertNull(request.path)
            assertNull(request.desc)
            assertNull(request.method)
            assertEquals(userResourcePsiClass.methods[11], (request.resource as PsiResource).resource())
        }

        assertEquals(
            "[INFO]\tsearch api from:com.itangcent.jaxrs.UserResource\n",
            LoggerCollector.getLog().toUnixString()
        )
    }
}