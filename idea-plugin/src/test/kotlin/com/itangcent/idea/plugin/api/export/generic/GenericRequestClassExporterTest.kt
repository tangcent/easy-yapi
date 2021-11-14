package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.firstOrNull
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test case of [GenericRequestClassExporter]
 */
internal class GenericRequestClassExporterTest
    : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userClientClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
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
        loadFile("constant/UserType.java")
        loadFile("model/UserInfo.java")
        userClientClass = loadClass("client/UserClient.java")!!
    }

    override fun customConfig(): String {
        return "generic.class.has.api=groovy:it.name().endsWith(\"Client\")\n" +
                "generic.path=#path\n" +
                "generic.path[groovy:it.contextType()==\"class\"]=groovy:```\n" +
                "def name =it.name().replace('.','/')\n" +
                "def index = name.lastIndexOf('/')\n" +
                "\"/\"+tool.camel2Underline(name[0..index]+tool.uncapitalize(name[index+1..-1]))\n" +
                "```\n" +
                "generic.path[groovy:it.contextType()==\"method\"]=groovy:it.name()\n" +
                "generic.http.method[groovy:it.contextType()==\"method\"]=groovy:```\n" +
                "(it.argCnt()==0||it.args().every{it.type().isNormalType()})?\"GET\":null\n" +
                "```\n" +
                "generic.http.method=#method\n" +
                "generic.http.method[#post]=POST\n" +
                "generic.http.method[#POST]=POST\n" +
                "generic.http.method[#get]=GET\n" +
                "generic.http.method[#GET]=GET\n" +
                "#use POST by default\n" +
                "generic.http.method=POST\n" +
                "#always true\n" +
                "generic.method.has.api=true\n" +
                "generic.param.as.json.body=groovy:!it.type().isNormalType()\n" +
                "generic.param.as.form.body=false\n" +
                "method.additional.header={name: \"Authorization\",value: \"123h\",desc: \"Token in header\",required:true, example:\"\"}\n" +
                "method.additional.param={name: \"Authorization\",value: \"123p\",desc: \"Token in param\",required:true}"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ClassExporter::class) { it.with(GenericRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    fun testExport() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        val requests = ArrayList<Request>()
        settings.genericEnable = true
        classExporter.export(userClientClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("GET", request.method)
            assertEquals(URL.of("/com/itangcent/client/user_client/greeting"), request.path)
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals(userClientClass.methods[0], (request.resource as PsiResource).resource())
            checkHeader(request)
            checkParam(request)
        }
        requests[1].let { request ->
            assertEquals("GET", request.method)
            assertEquals(URL.of("/com/itangcent/client/user_client/set"), request.path)
            assertEquals("update username", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals(userClientClass.methods[1], (request.resource as PsiResource).resource())
            checkHeader(request)
            checkParam(request)
        }
        requests[2].let { request ->
            assertEquals("GET", request.method)
            assertEquals(URL.of("/com/itangcent/client/user_client/user/get"), request.path)
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals(userClientClass.methods[2], (request.resource as PsiResource).resource())
            checkHeader(request)
            checkParam(request)
        }
        requests[3].let { request ->
            assertEquals("POST", request.method)
            assertEquals(URL.of("/com/itangcent/client/user_client/add"), request.path)
            assertEquals("create new use", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals(userClientClass.methods[3], (request.resource as PsiResource).resource())
            checkHeader(request)
            checkParam(request)
        }
    }
}


private fun checkHeader(request: Request) {
    assertNotNull(request.headers)
    val header = request.headers!!
        .stream()
        .filter { it.name == "Authorization" }
        .firstOrNull()
    assertNotNull(header)
    assertEquals("Authorization", header.name)
    assertEquals("123h", header.value)
    assertEquals("Token in header", header.desc)
    assertEquals(true, header.required)
}

private fun checkParam(request: Request) {
    assertNotNull(request.querys)
    val param = request.querys!!
        .stream()
        .filter { it.name == "Authorization" }
        .firstOrNull()
    assertNotNull(param)
    assertEquals("Authorization", param.name)
    assertEquals("123p", param.value)
    assertEquals("Token in param", param.desc)
    assertEquals(true, param.required)
}
