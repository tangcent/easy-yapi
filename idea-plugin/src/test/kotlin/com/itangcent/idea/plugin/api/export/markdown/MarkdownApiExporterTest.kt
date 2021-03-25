package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiFile
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.ComboClassExporter
import com.itangcent.idea.plugin.api.export.DefaultMethodDocClassExporter
import com.itangcent.idea.plugin.api.export.SpringRequestClassExporter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [MarkdownApiExporter]
 */
internal class MarkdownApiExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    private lateinit var markdownApiExporter: MarkdownApiExporter

    private lateinit var userCtrlPsiFile: PsiFile

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
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(ClassExporter::class) { it.with(ComboClassExporter::class).singleton() }
        builder.bindInstance("AVAILABLE_CLASS_EXPORTER", arrayOf<Any>(SpringRequestClassExporter::class, DefaultMethodDocClassExporter::class))

        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.inferEnable = true
            }))
        }
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.common.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
                "#[converts]*\n" +
                "#The ObjectId and Date are parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String"
    }

    fun testExport() {
        actionContext.cache(CommonDataKeys.PSI_FILE.name, userCtrlPsiFile)
        markdownApiExporter.export()
        actionContext.waitComplete()
        assertEquals("# apis about user\n" +
                "\n" +
                "\n" +
                "---\n" +
                "## say hello\n" +
                "\n" +
                "### BASIC\n" +
                "\n" +
                "**Path：** user/greeting\n" +
                "\n" +
                "**Method：** GET\n" +
                "\n" +
                "**Desc：**\n" +
                "\n" +
                "say hello\n" +
                " not update anything\n" +
                "\n" +
                "### REQUEST\n" +
                "\n" +
                "\n" +
                "\n" +
                "### RESPONSE\n" +
                "\n" +
                "**Header：**\n" +
                "\n" +
                "| name  |  value  |  required  | desc  |\n" +
                "| ------------ | ------------ | ------------ | ------------ | ------------ |\n" +
                "| content-type | application/json;charset=UTF-8 | NO |   |\n" +
                "\n" +
                "**Body：**\n" +
                "\n" +
                "| name | type | desc |\n" +
                "| ------------ | ------------ | ------------ |\n" +
                "|  | string |  | \n" +
                "\n" +
                "**Response Demo：**\n" +
                "\n" +
                "```json\n" +
                "\n" +
                "```\n" +
                "\n" +
                "\n" +
                "\n" +
                "---\n" +
                "## get user info\n" +
                "\n" +
                "### BASIC\n" +
                "\n" +
                "**Path：** user/get/{id}\n" +
                "\n" +
                "**Method：** GET\n" +
                "\n" +
                "**Desc：**\n" +
                "\n" +
                "get user info\n" +
                "\n" +
                "### REQUEST\n" +
                "\n" +
                "\n" +
                "**Headers：**\n" +
                "\n" +
                "| name  |  value  |  required  | desc  |\n" +
                "| ------------ | ------------ | ------------ | ------------ |\n" +
                "| token |  | YES | auth token |\n" +
                "\n" +
                "**Query：**\n" +
                "\n" +
                "| name  |  value  |  required | desc  |\n" +
                "| ------------ | ------------ | ------------ | ------------ |\n" +
                "| id | 0 | NO | user id  |\n" +
                "\n" +
                "\n" +
                "### RESPONSE\n" +
                "\n" +
                "**Header：**\n" +
                "\n" +
                "| name  |  value  |  required  | desc  |\n" +
                "| ------------ | ------------ | ------------ | ------------ | ------------ |\n" +
                "| content-type | application/json;charset=UTF-8 | NO |   |\n" +
                "\n" +
                "**Body：**\n" +
                "\n" +
                "| name | type | desc |\n" +
                "| ------------ | ------------ | ------------ |\n" +
                "| code | integer | response code | \n" +
                "| msg | string | message | \n" +
                "| data | object | response data | \n" +
                "| &ensp;&ensp;&#124;─id | integer | user id | \n" +
                "| &ensp;&ensp;&#124;─type | integer | user type | \n" +
                "| &ensp;&ensp;&#124;─name | string | user name | \n" +
                "| &ensp;&ensp;&#124;─age | integer | user age | \n" +
                "| &ensp;&ensp;&#124;─sex | integer |  | \n" +
                "| &ensp;&ensp;&#124;─birthDay | string | user birthDay | \n" +
                "| &ensp;&ensp;&#124;─regtime | string | user regtime | \n" +
                "\n" +
                "**Response Demo：**\n" +
                "\n" +
                "```json\n" +
                "{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"id\": 0,\n" +
                "    \"type\": 0,\n" +
                "    \"name\": \"Tony Stark\",\n" +
                "    \"age\": 45,\n" +
                "    \"sex\": 0,\n" +
                "    \"birthDay\": \"\",\n" +
                "    \"regtime\": \"\"\n" +
                "  }\n" +
                "}\n" +
                "```", (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim())
    }
}