package com.itangcent.idea.plugin.api.export.http

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
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
 * Test case of [HttpClientFormatter]
 *
 * @author tangcent
 */
internal class HttpClientFormatterTest : PluginContextLightCodeInsightFixtureTestCase() {

    companion object {
        const val HOST = "http://localhost:8080"
    }

    @Inject
    internal lateinit var classExporter: ClassExporter

    @Inject
    protected lateinit var httpClientFormatter: HttpClientFormatter

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
        return "method.additional.header[!@com.itangcent.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, demo:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    fun testParseRequests() {
        val requests = ArrayList<Request>();
        val boundary = actionContext.createBoundary();
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it);
        });
        boundary.waitComplete(false);
        classExporter.export(testCtrlPsiClass, requestOnly {
            requests.add(it);
        });
        boundary.waitComplete();

        assertEquals(ResultLoader.load("testParseRequests"), httpClientFormatter.parseRequests(HOST, requests));
    }

    fun testParseRequestsToExistedDoc() {
        val requests = ArrayList<Request>();
        val boundary = actionContext.createBoundary();
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it);
        });
        boundary.waitComplete(false);
        classExporter.export(testCtrlPsiClass, requestOnly {
            requests.add(it);
        });
        boundary.waitComplete();

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

        assertEquals(
            ResultLoader.load("testParseRequestsToExistedDoc"),
            httpClientFormatter.parseRequests(
                existedDoc, HOST, requests
            )
        );
    }
}