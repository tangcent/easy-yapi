package com.itangcent.idea.plugin.api.export.curl

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import com.itangcent.utils.WaitHelper
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.stub
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [CurlExporter]
 */
internal class CurlExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    private lateinit var curlExporter: CurlExporter

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var userCtrlPsiClass: PsiClass

    private var command = ""

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

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }

        val messagesHelper = org.mockito.kotlin.mock<MessagesHelper>()
        messagesHelper.stub {
            on {
                this.showInfoDialog(
                    anyString(),
                    anyString()
                )
            }.thenAnswer {
                command = it.getArgument<String>(0)
                return@thenAnswer null
            }
        }
        builder.bindInstance(MessagesHelper::class, messagesHelper)

    }

    fun testExport() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()

        assertNoThrowable { curlExporter.export(emptyList()) }

        curlExporter.export(requests[0])
        assertEquals("curl -X GET http://localhost:8080/user/greeting", command)

        command = ""
        curlExporter.export(listOf(requests[1]))
        assertEquals("curl -X GET http://localhost:8080/user/get/{id}?id=0", command)

        command = ""
        curlExporter.export(requests)
        assertEquals("", command)
        WaitHelper.waitUtil(10000) { (fileSaveHelper as FileSaveHelperAdaptor).content().notNullOrEmpty() }
        assertEquals(
            ResultLoader.load(),
            (fileSaveHelper as FileSaveHelperAdaptor).content()
        )
    }
}