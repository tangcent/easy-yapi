package com.itangcent.demo

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.common.utils.appendln
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.yapi.YapiFormatter
import com.itangcent.idea.plugin.api.export.yapi.YapiSpringClassExporterBaseTest
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.condition.OS

/**
 * Test case of [javax.validation(grouped)]
 */
internal class ValidationGroupedTest : YapiSpringClassExporterBaseTest() {

    @Inject
    private lateinit var yapiFormatter: YapiFormatter

    internal lateinit var validationCtrlPsiClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        loadSource(java.lang.Class::class)
        loadClass("constant/Add.java")
        loadClass("constant/Update.java")
        loadClass("validation/Validated.java")
        loadClass("validation/NotBlank.java")
        loadClass("validation/NotEmpty.java")
        loadClass("validation/NotNull.java")
        loadClass("model/ValidationGroupedDemoDto.java")
        validationCtrlPsiClass = loadClass("api/ValidationCtrl.java")!!
    }

    override fun customConfig(): String {
        return super.customConfig()
                .appendln("#[javax.validation(grouped)]\n" +
                        "#Support for javax.validation annotations(grouped)\n" +
                        "json.cache.disable=true\n" +
                        "json.group=groovy:session.get(\"json-group\")\n" +
                        "param.before=groovy:```\n" +
                        "    session.set(\"json-group\", it.annValue(\"org.springframework.validation.annotation.Validated\"))\n" +
                        "```\n" +
                        "param.after=groovy:```\n" +
                        "    session.remove(\"json-group\")\n" +
                        "```\n" +
                        "param.required=@javax.validation.constraints.NotBlank\n" +
                        "param.required=@javax.validation.constraints.NotNull\n" +
                        "param.required=@javax.validation.constraints.NotEmpty\n" +
                        "field.required[@javax.validation.constraints.NotBlank]=groovy:```\n" +
                        "    it.annMaps(\"javax.validation.constraints.NotBlank\")?.any{tool.equalOrIntersect(session.get(\"json-group\"),it[\"groups\"])}\n" +
                        "```\n" +
                        "field.required[@javax.validation.constraints.NotNull]=groovy:```\n" +
                        "    it.annMaps(\"javax.validation.constraints.NotNull\")?.any{tool.equalOrIntersect(session.get(\"json-group\"),it[\"groups\"])}\n" +
                        "```\n" +
                        "field.required[@javax.validation.constraints.NotEmpty]=groovy:```\n" +
                        "    it.annMaps(\"javax.validation.constraints.NotEmpty\")?.any{tool.equalOrIntersect(session.get(\"json-group\"),it[\"groups\"])}\n" +
                        "```")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(1618124194123L))
        }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    /**
     * use json-schema by default
     */
    fun testDoc2Item() {
        val requests = ArrayList<Request>()
        classExporter.export(validationCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertEquals("[{\"query_path\":{\"path\":\"/test/validation/demo/add\",\"params\":[]},\"method\":\"POST\",\"req_body_type\":\"json\",\"res_body_type\":\"json\",\"index\":0,\"req_body_other\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"strForAdd\\\":{\\\"type\\\":\\\"string\\\"},\\\"notEmptyForUpdate\\\":{\\\"type\\\":\\\"string\\\"}},\\\"required\\\":[\\\"strForAdd\\\"],\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\"type\":\"static\",\"title\":\"demo-add\",\"req_body_form\":[],\"path\":\"/test/validation/demo/add\",\"req_body_is_json_schema\":true,\"__v\":0,\"markdown\":\"\",\"req_headers\":[{\"name\":\"Content-Type\",\"value\":\"application/json\",\"example\":\"application/json\",\"required\":1},{\"name\":\"token\",\"value\":\"\",\"desc\":\"auth token\",\"example\":\"123456\",\"required\":1}],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[],\"req_query\":[],\"api_opened\":false,\"add_time\":1618124194,\"res_body_is_json_schema\":true,\"status\":\"done\",\"desc\":\"\\u003cp\\u003e\\u003c/p\\u003e\"}]",
                yapiFormatter.doc2Item(requests[0]).toJson())
        assertEquals("[{\"query_path\":{\"path\":\"/test/validation/demo/update\",\"params\":[]},\"method\":\"POST\",\"req_body_type\":\"json\",\"res_body_type\":\"json\",\"index\":0,\"req_body_other\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"strForAdd\\\":{\\\"type\\\":\\\"string\\\"},\\\"notEmptyForUpdate\\\":{\\\"type\\\":\\\"string\\\"}},\\\"required\\\":[\\\"notEmptyForUpdate\\\"],\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\"type\":\"static\",\"title\":\"demo-update\",\"req_body_form\":[],\"path\":\"/test/validation/demo/update\",\"req_body_is_json_schema\":true,\"__v\":0,\"markdown\":\"\",\"req_headers\":[{\"name\":\"Content-Type\",\"value\":\"application/json\",\"example\":\"application/json\",\"required\":1},{\"name\":\"token\",\"value\":\"\",\"desc\":\"auth token\",\"example\":\"123456\",\"required\":1}],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[],\"req_query\":[],\"api_opened\":false,\"add_time\":1618124194,\"res_body_is_json_schema\":true,\"status\":\"done\",\"desc\":\"\\u003cp\\u003e\\u003c/p\\u003e\"}]",
                yapiFormatter.doc2Item(requests[1]).toJson())
    }
}