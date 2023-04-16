package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.intellij.psi.PsiFile
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompositeClassExporter
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [MarkdownApiExporter]
 */
internal abstract class MarkdownApiExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    protected lateinit var markdownApiExporter: MarkdownApiExporter

    protected lateinit var userCtrlPsiFile: PsiFile

    protected lateinit var userClientPsiFile: PsiFile

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
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
        userClientPsiFile = loadFile("client/UserClient.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(TimeZoneKit.STANDARD_TIME))
        }

        builder.bind(ClassExporter::class) { it.with(CompositeClassExporter::class).singleton() }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))
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

    class SpringMarkdownApiExporterTest : MarkdownApiExporterTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile)
        }

        fun testExportSpring() {
            markdownApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim()
            )
        }
    }

    class SpringUltimateMarkdownApiExporterTest : MarkdownApiExporterTest() {

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.markdownFormatType = MarkdownFormatType.ULTIMATE.name
                }))
            }
            builder.workAt(userCtrlPsiFile)
        }

        fun testExportSpring() {
            markdownApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim()
            )
        }
    }

    class DirectorySpringMarkdownApiExporterTest : MarkdownApiExporterTest() {

        override fun beforeBind() {
            super.beforeBind()
            loadFile("api/TestCtrl.java")
        }

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile.parent!!)
        }

        fun testExportSpring() {
            markdownApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim()
            )
        }
    }

    class CustomizedDirectorySpringMarkdownApiExporterTest : MarkdownApiExporterTest() {

        override fun beforeBind() {
            super.beforeBind()
            loadFile("api/TestCtrl.java")
        }

        override fun customConfig(): String {
            return "\n" +
                    "md.title=groovy:```\n" +
                    "    //increase index\n" +
                    "    def i = session.get(deep+\".i\")\n" +
                    "    i=i==null?1:i+1\n" +
                    "    session.set(deep+\".i\",i)\n" +
                    "\n" +
                    "    //title index\n" +
                    "    def t = \"\"\n" +
                    "    for(d in 1..deep){\n" +
                    "        if(d>1){\n" +
                    "            t += \".\"\n" +
                    "        }\n" +
                    "        t += session.get(d+\".i\")\n" +
                    "    }\n" +
                    "\n" +
                    "    return tool.repeat(\"#\",deep) + \" \" + t + \" \" + title\n" +
                    "```\n" +
                    "# for api\n" +
                    "md.basic=> `BASIC`\n" +
                    "md.basic.path=groovy:\"**`Path params`:** \"+doc.path\n" +
                    "md.basic.method=groovy:\"**`Method`:** \"+doc.method\n" +
                    "md.basic.desc=groovy:\"**`desc`:** \"+doc.desc\n" +
                    "md.request=> `QUERY`\n" +
                    "md.request.path=**`Path params`:**\n" +
                    "md.request.headers=**Headers:**\n" +
                    "md.request.query=**Query:**\n" +
                    "md.request.body=**Body:**\n" +
                    "md.request.body.demo=**`Body Demo`:**\n" +
                    "md.request.form=**`Form`:**\n" +
                    "md.response=> `Response`\n" +
                    "md.response.headers=**Headers:**\n" +
                    "md.response.body=**Body:**\n" +
                    "md.response.body.demo=**`Response demo`:**\n" +
                    "# for method doc\n" +
                    "md.methodDoc.desc=groovy:\"**`desc`:** \"+doc.desc\n" +
                    "md.methodDoc.params=**`params`:**\n" +
                    "md.methodDoc.return=**`return`:**\n" +
                    "\n" +
                    "md.table.request.pathParams.name.name=`name`\n" +
                    "md.table.request.pathParams.value.name=`value`\n" +
                    "md.table.request.pathParams.desc.name=`desc`\n" +
                    "md.table.request.headers.name.name=`name`\n" +
                    "md.table.request.headers.desc.align=----:\n" +
                    "md.table.request.headers.value.name=`value`\n" +
                    "md.table.request.headers.desc.name=`desc`\n" +
                    "md.table.request.headers.required.ignore=true\n" +
                    "md.table.request.headers.required.name=`required`\n" +
                    "md.table.request.querys.name.name=`name`\n" +
                    "md.table.request.querys.value.name=`value`\n" +
                    "md.table.request.querys.desc.name=`desc`\n" +
                    "md.table.request.querys.desc.align=----:\n" +
                    "md.table.request.querys.required.name=`required`\n" +
                    "md.table.request.form.name.name=`name`\n" +
                    "md.table.request.form.value.name=`value`\n" +
                    "md.table.request.form.desc.name=`desc`\n" +
                    "md.table.request.form.type.name=`type`\n" +
                    "md.table.request.form.type.align=----:\n" +
                    "md.table.request.form.required.name=`required`\n" +
                    "md.table.response.headers.name.name=`name`\n" +
                    "md.table.response.headers.value.name=`value`\n" +
                    "md.table.response.headers.desc.name=`desc`\n" +
                    "md.table.response.headers.desc.align=:----:\n" +
                    "md.table.response.headers.required.name=`required`\n" +
                    "md.table.request.body.name.name=`name`\n" +
                    "md.table.request.body.default.name=`default value`\n" +
                    "md.table.request.body.desc.name=`desc`\n" +
                    "md.table.request.body.type.name=`type`\n" +
                    "md.table.request.body.type.align=:----:\n" +
                    "md.table.request.body.required.name=`required`\n" +
                    "md.table.response.body.name.name=`name`\n" +
                    "md.table.response.body.default.name=`default value`\n" +
                    "md.table.response.body.desc.name=`desc`\n" +
                    "md.table.response.body.type.name=`type`\n" +
                    "md.table.response.body.type.align=:----:\n" +
                    "md.table.response.body.required.name=`required`\n" +
                    "md.table.methodDoc.params.name.name=`name`\n" +
                    "md.table.methodDoc.params.default.name=`default value`\n" +
                    "md.table.methodDoc.params.desc.name=`desc`\n" +
                    "md.table.methodDoc.params.type.name=`type`\n" +
                    "md.table.methodDoc.params.type.align=:----:\n" +
                    "md.table.methodDoc.params.required.name=`required`\n" +
                    "md.table.methodDoc.return.name.name=`name`\n" +
                    "md.table.methodDoc.return.default.name=`default value`\n" +
                    "md.table.methodDoc.return.desc.name=`desc`\n" +
                    "md.table.methodDoc.return.type.name=`type`\n" +
                    "md.table.methodDoc.return.type.align=:----\n" +
                    "md.table.methodDoc.return.required.name=`required`\n" +
                    "# alias for bool\n" +
                    "md.bool.true=Y\n" +
                    "md.bool.false=N\n" +
                    "md.header=> Copyright©TANGCENT. All Rights Reserved\n" +
                    "md.footer=-----------^-----------^-----------^-----------^-----------\n" +
                    super.customConfig()
        }

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile.parent!!)
        }

        fun testExportSpring() {
            markdownApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim()
            )
        }
    }

    class GenericMethodMarkdownApiExporterTest : MarkdownApiExporterTest() {
        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.methodDocEnable = true
                    settings.genericEnable = true
                }))
            }
            builder.workAt(userClientPsiFile)
        }

        fun testExportGenericMethod() {
            markdownApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()?.trim()
            )
        }
    }
}