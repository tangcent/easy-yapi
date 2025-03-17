package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.core.AdditionalParseHelper
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal abstract class YapiSpringClassExporterBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    internal lateinit var classExporter: ClassExporter

    internal lateinit var userCtrlPsiClass: PsiClass

    internal lateinit var defaultCtrlPsiClass: PsiClass

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
        loadSource(java.lang.Deprecated::class)
        loadFile("annotation/Public.java")
        loadFile("constant/UserType.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("model/Model.java")
        loadFile("model/Default.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        defaultCtrlPsiClass = loadClass("api/DefaultCtrl.java")!!
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
            field.default.value=#default
            api.open=@com.itangcent.annotation.Public
            api.status[#undone]=undone
            api.tag[@java.lang.Deprecated]=deprecated
            path.multi=all
            yapi.format.after=groovy:```
                if(url.contains("/admin")){
                    item["title"] = "[admin]"+item["title"]
                    item["markdown"] = "[admin]"+item["markdown"]
                    item["desc"] = "[admin]"+item["desc"]
                }
            ```
            field.order=#order
            field.order.with=groovy:```
                def aDefineClass = a.defineClass()
                def bDefineClass = b.defineClass()
                if(aDefineClass==bDefineClass){
                    return 0
                }else if(aDefineClass.isExtend(bDefineClass.name())){
                    return 1
                }else{
                    return -1
                }
            ```
            """
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ExportChannel::class) { it.toInstance(ExportChannel.of("yapi")) }
        builder.bind(ClassExporter::class) { it.with(YapiSpringRequestClassExporter::class).singleton() }
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
        builder.bind(PsiClassHelper::class) { it.with(YapiPsiClassHelper::class).singleton() }
        builder.bind(AdditionalParseHelper::class) { it.with(YapiAdditionalParseHelper::class).singleton() }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.inferEnable = true
            }))
        }
    }
}