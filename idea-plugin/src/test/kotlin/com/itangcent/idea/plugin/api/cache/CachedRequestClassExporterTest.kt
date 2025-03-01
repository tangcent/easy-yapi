package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.cache.CacheSwitcher
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Test case of [CachedRequestClassExporter]
 */
internal class CachedRequestClassExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    @Inject
    private lateinit var cacheSwitcher: CacheSwitcher

    @Inject
    private lateinit var springRequestClassExporter: SpringRequestClassExporter

    private lateinit var baseControllerPsiClass: PsiClass

    private lateinit var userCtrlPsiClass: PsiClass

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
        baseControllerPsiClass = loadClass("api/BaseController.java")!!
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, demo:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))

        springRequestClassExporter = Mockito.spy(SpringRequestClassExporter())
        builder.bind(SpringRequestClassExporter::class) { it.toInstance(springRequestClassExporter) }

        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.inferEnable = true
            }))
        }
    }

    override fun afterBind() {
        super.afterBind()
        ActionContext.getContext()!!.init(springRequestClassExporter)
    }

    fun testExport() {
        val requests = ArrayList<Request>()
        val boundary = actionContext.createBoundary()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        boundary.waitComplete(false)
        requests[0].let { request ->
            assertEquals("current ctrl name", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals(baseControllerPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[2].let { request ->
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        Mockito.verify(springRequestClassExporter, times(1))
            .export(any(), any())

        boundary.waitComplete(TimeUnit.SECONDS.toMillis(10), false)//wait 10s to save cache
        //export again
        val requestsAgain = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requestsAgain.add(it)
        })
        boundary.waitComplete(false)
        assertEquals(requests, requestsAgain)
        Mockito.verify(springRequestClassExporter, times(1))
            .export(any(), any())

        //export thrice
        cacheSwitcher.notUseCache()
        val requestsThrice = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requestsThrice.add(it)
        })
        boundary.waitComplete(false)
        assertEquals(requests, requestsThrice)
        Mockito.verify(springRequestClassExporter, times(2))
            .export(any(), any())

        //export quartic
        cacheSwitcher.useCache()
        val requestsQuartic = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requestsQuartic.add(it)
        })
        boundary.waitComplete(false)
        assertEquals(requests, requestsQuartic)
        Mockito.verify(springRequestClassExporter, times(2))
            .export(any(), any())
    }
}