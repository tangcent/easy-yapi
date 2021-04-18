package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.generic.SimpleGenericMethodDocClassExporter
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
 * Test case of [SimpleGenericMethodDocClassExporter]
 */
internal class SimpleMethodDocClassExporterTest
    : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userCtrlPsiClass: PsiClass

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
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ClassExporter::class) { it.with(SimpleGenericMethodDocClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    fun testExport() {
        val methodDocs = ArrayList<MethodDoc>()
        settings.methodDocEnable = false
        classExporter.export(userCtrlPsiClass, methodDocOnly {
            methodDocs.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertTrue(methodDocs.isEmpty())

        //enable export method doc
        settings.methodDocEnable = true
        classExporter.export(userCtrlPsiClass, methodDocOnly {
            methodDocs.add(it)
        })
        (classExporter as Worker).waitCompleted()
        methodDocs[0].let { methodDoc ->
            assertEquals("say hello", methodDoc.name)
            assertNull(methodDoc.desc)
            assertEquals(userCtrlPsiClass.methods[0], (methodDoc.resource as PsiResource).resource())
        }
        methodDocs[1].let { methodDoc ->
            assertEquals("get user info", methodDoc.name)
            assertNull(methodDoc.desc)
            assertEquals(userCtrlPsiClass.methods[1], (methodDoc.resource as PsiResource).resource())
        }
    }
}