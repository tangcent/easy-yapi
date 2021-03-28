package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.condition.OS
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList

/**
 * Test case of [PostmanSpringRequestClassExporter]
 * 1.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_PRE_REQUEST]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_TEST]
 *
 */
internal class PostmanSpringRequestClassExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

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
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.common.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date are parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n" +
                "# read folder name from tag `folder`\n" +
                "postman.prerequest=```\n" +
                "pm.environment.set(\"token\", \"123456\");\n" +
                "```\n" +
                "postman.test=```\n" +
                "pm.test(\"Successful POST request\", function () {\n" +
                "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                "});\n" +
                "```"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(PostmanSpringRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.inferEnable = true
            }))
        }
    }

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    fun testExport() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("say hello\n" +
                    " not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())

            assertEquals("pm.environment.set(\"token\", \"123456\");", request.getExt(ClassExportRuleKeys.POST_PRE_REQUEST.name()))
            assertEquals("pm.test(\"Successful POST request\", function () {\n" +
                    "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                    "});", request.getExt(ClassExportRuleKeys.POST_TEST.name()))
        }
        requests[1].let { request ->
            assertEquals("get user info", request.name)
            assertEquals("get user info", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertEquals("pm.environment.set(\"token\", \"123456\");", request.getExt(ClassExportRuleKeys.POST_PRE_REQUEST.name()))
            assertEquals("pm.test(\"Successful POST request\", function () {\n" +
                    "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                    "});", request.getExt(ClassExportRuleKeys.POST_TEST.name()))
        }

    }
}