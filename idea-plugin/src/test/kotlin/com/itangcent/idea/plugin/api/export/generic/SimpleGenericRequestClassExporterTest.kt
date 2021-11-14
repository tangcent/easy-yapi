package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.MethodDoc
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
 * Test case of [SimpleGenericRequestClassExporter]
 */
internal class SimpleGenericRequestClassExporterTest
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
        loadFile("annotation/Public.java")
        loadFile("constant/UserType.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        userClientClass = loadClass("client/UserClient.java")!!
    }

    override fun customConfig(): String {
        return "generic.class.has.api=groovy:it.name().endsWith(\"Client\")\n" +
                "generic.method.has.api=true"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ClassExporter::class) { it.with(SimpleGenericRequestClassExporter::class).singleton() }
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
            assertEquals("say hello", request.name)
            assertNull(request.desc)
            assertEquals(userClientClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("update username", request.name)
            assertNull(request.desc)
            assertEquals(userClientClass.methods[1], (request.resource as PsiResource).resource())
        }
    }
}