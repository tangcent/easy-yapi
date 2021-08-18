package com.itangcent.idea.plugin.api.export.curl

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [CurlFormatter]
 */
internal class CurlFormatterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    internal lateinit var classExporter: ClassExporter

    @Inject
    protected lateinit var curlFormatter: CurlFormatter

    protected lateinit var userCtrlPsiClass: PsiClass

    protected lateinit var testCtrlPsiClass: PsiClass

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
        loadFile("model/Node.java")
        loadFile("model/Root.java")
        loadFile("model/CustomMap.java")
        loadFile("model/PageRequest.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        testCtrlPsiClass = loadClass("api/TestCtrl.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.common.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    fun testParseRequest() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()


        assertEquals("curl -X GET http://localhost:8080/user/greeting", curlFormatter.parseRequest(requests[0]))
        assertEquals(
            "curl -X GET -H 'token: ' http://localhost:8080/user/get/{id}?id=0",
            curlFormatter.parseRequest(requests[1])
        )
        assertEquals(
            "curl -X POST -H 'Content-Type: application/json' -H 'token: ' -d '{\n" +
                    "  \"id\": 0,\n" +
                    "  \"type\": 0,\n" +
                    "  \"name\": \"\",\n" +
                    "  \"age\": 0,\n" +
                    "  \"sex\": 0,\n" +
                    "  \"birthDay\": \"\",\n" +
                    "  \"regtime\": \"\"\n" +
                    "}' http://localhost:8080/user/add", curlFormatter.parseRequest(requests[2])
        )
        assertEquals(
            "curl -X PUT -H 'Content-Type: multipart/form-data' -H 'token: ' -F 'id=' -F 'type=' -F 'name=' -F 'age=' -F 'sex=' -F 'birthDay=' -F 'regtime=' http://localhost:8080/user/update",
            curlFormatter.parseRequest(requests[3])
        )

    }

    fun testParseRequests() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        classExporter.export(testCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()

        assertEquals(ResultLoader.load(), curlFormatter.parseRequests(requests))

    }
}