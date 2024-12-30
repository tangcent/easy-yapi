package com.itangcent.idea.plugin.api

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.Assertions.assertInstanceOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case for [ClassApiExporterHelper]
 */
class ClassApiExporterHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    private lateinit var userCtrlPsiClass: PsiClass
    private lateinit var userCtrlPsiFile: PsiFile


    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.Boolean::class)
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
        loadFile("spring/GetMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/RestController.java")
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
        userCtrlPsiClass = (userCtrlPsiFile as? PsiClassOwner)?.classes?.firstOrNull()!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
        builder.workAt(userCtrlPsiFile)
    }

    fun testExtractParamComment() {
        val method = userCtrlPsiClass.methods.first { it.name == "get" }
        val comments = classApiExporterHelper.extractParamComment(method)

        assertNotNull(comments)
        assertTrue(comments!!.containsKey("id"))
        assertEquals("user id", comments["id"])
    }

    fun testForeachMethod() {
        val methods = mutableListOf<String>()
        classApiExporterHelper.foreachMethod(userCtrlPsiClass) { method ->
            methods.add(method.name())
        }
        actionContext.waitComplete()

        assertTrue(methods.contains("create"))
        assertTrue(methods.contains("get"))
        assertFalse(methods.contains("toString"))
    }

    fun testExport() {
        val docs = classApiExporterHelper.export()
        actionContext.waitComplete()

        assertNotNull(docs)
        assertTrue(docs.isNotEmpty())

        // Verify first API doc
        docs[0].let { doc ->
            assertInstanceOf(Request::class.java, doc)
            doc as Request
            assertEquals("say hello", doc.name)
            assertEquals("GET", doc.method)
            assertEquals("user/greeting", doc.path.toString())
        }
    }
}