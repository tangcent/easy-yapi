package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompositeRequestBuilderListener
import com.itangcent.idea.plugin.api.export.core.RequestBuilderListener
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal abstract class PostmanSpringClassExporterBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    internal lateinit var classExporter: ClassExporter

    internal lateinit var baseControllerPsiClass: PsiClass

    internal lateinit var userCtrlPsiClass: PsiClass

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
        return """
            method.additional.header[!@com.itangcent.annotation.Public]={name: "token",value: "",desc: "auth token",required:true, demo:"123456"}
            #[converts]*
            #The ObjectId and Date will be parsed as strings
            json.rule.convert[org.bson.types.ObjectId]=java.lang.String
            json.rule.convert[java.util.Date]=java.lang.String
            json.rule.convert[java.sql.Timestamp]=java.lang.String
            json.rule.convert[java.time.LocalDateTime]=java.lang.String
            json.rule.convert[java.time.LocalDate]=java.lang.String
            # read folder name from tag `folder`
            folder.name=#folder
            postman.prerequest=```
            pm.environment.set("token", "123456");
            ```
            postman.test=```
            pm.test("Successful POST request", function () {
            pm.expect(pm.response.code).to.be.oneOf([201,202]);
            });
            ```
            path.multi=all
            postman.format.after=groovy:```
                if(url.contains("/admin")){
                    item["name"] = "[admin]"+item["name"]
                    item["request"]["description"] = "[admin]"+item["request"]["description"]
                }
            ```
        """
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(SpringRequestClassExporter::class).singleton() }

        builder.bind(RequestBuilderListener::class) { it.with(CompositeRequestBuilderListener::class).singleton() }

        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.inferEnable = true
            }))
        }

        builder.bind(ExportChannel::class) { it.toInstance(ExportChannel.of("postman")) }
    }
}