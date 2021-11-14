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
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.test.ResultLoader
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

        builder.bind(ClassExporter::class) { it.with(CompositeClassExporter::class).singleton() }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))
    }

    override fun customConfig(): String {
        return "method.additional.header[!@com.itangcent.annotation.Public]={name: \"token\",value: \"\",desc: \"auth token\",required:true, example:\"123456\"}\n" +
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