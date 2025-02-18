package com.itangcent.idea.plugin.api.export.http

import com.google.inject.Inject
import com.intellij.openapi.application.PathManager
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.common.utils.forceDelete
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


/**
 * Test case of [HttpClientExporter]
 *
 * @author tangcent
 */
class HttpClientExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var httpClientExporter: HttpClientExporter

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userCtrlPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Void::class)
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
        loadFile("api/BaseController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    override fun customConfig(): String {
        return "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n"
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
    }

    private val scratchesPath: Path by lazy { Paths.get(PathManager.getConfigPath(), "scratches") }

    fun testSaveAndOpenHttpFile() {
        val targetFile = "$scratchesPath/test_default/apis about user.http"
        Path.of(targetFile).toFile().forceDelete()

        val requests = ArrayList<Request>()

        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }

        assertNoThrowable { httpClientExporter.export(emptyList()) }

        httpClientExporter.export(requests)

        assertEquals(
            ResultLoader.load("newFile"), Path.of(targetFile).toFile().readText()
        )


        val existedDoc = """
### ref: com.itangcent.api.UserCtrl#greeting()
### say hello

// anything here will be covered
// anything here will be covered
GET http://localhost:8080/user/greeting


###

### ref: com.itangcent.api.UserCtrl#notExisted(java.lang.Long)
### api not existed in userCtrl should be kept

GET http://localhost:8080/user/notExisted
token:


###
"""
        Path.of(targetFile).toFile().writeText(existedDoc)
        httpClientExporter.export(requests)

        assertEquals(
            ResultLoader.load("existedFile"), Path.of(targetFile).toFile().readText()
        )
    }
}