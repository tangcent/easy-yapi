package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiFile
import com.itangcent.common.utils.GsonUtils
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.*
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit.STANDARD_TIME
import com.itangcent.test.assertLinesEqualsIgnoreOrder
import com.itangcent.test.mock
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [YapiApiExporter]
 */
internal abstract class YapiApiExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    protected val apis: ArrayList<Any> = ArrayList()

    @Inject
    protected lateinit var yapiApiExporter: YapiApiExporter

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
        //clear log
        LoggerCollector.getLog()
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(STANDARD_TIME))
        }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("yapi"))
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

    class SpringYapiApiExporterTest : YapiApiExporterTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.yapiServer = "http://127.0.0.1:3088"
                    settings.yapiTokens = "test_default=token111111"
                }))
            }
            builder.mock<YapiApiHelper> {
                this.on { it.findCart(eq("token111111"), eq("apis about user")) }
                    .thenReturn(null, "111111")
                this.on { it.getProjectIdByToken(eq("token111111")) }
                    .thenReturn("12345")
                this.on { it.addCart(any(), any(), any()) }
                    .thenReturn(true)
                this.on { it.getCartWeb(any(""), any("")) }
                    .thenAnswer {
                        "http://127.0.0.1:3088/project/${it.getArgument<String>(0)}" +
                                "/interface/api/cat_${it.getArgument<String>(1)}"
                    }
                this.on { it.saveApiInfo(any(HashMap())) }
                    .thenAnswer {
                        apis.add(it.getArgument(0))
                        true
                    }
            }
            builder.workAt(userCtrlPsiFile)
        }

        fun testExportSpring() {
            yapiApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(apis)
            )
            assertLinesEqualsIgnoreOrder(
                ResultLoader.load("log"),
                LoggerCollector.getLog().toUnixString()
            )
        }
    }

    class DirectorySpringYapiApiExporterTest : YapiApiExporterTest() {

        override fun beforeBind() {
            super.beforeBind()
            loadFile("api/TestCtrl.java")
        }

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.yapiServer = "http://127.0.0.1:3088"
                    settings.yapiTokens = "test_default=token111111"
                }))
            }
            builder.mock<YapiApiHelper> {
                this.on { it.findCart(eq("token111111"), eq("apis about user")) }
                    .thenReturn("111111")
                this.on { it.findCart(eq("token111111"), eq("test apis")) }
                    .thenReturn("222222")
                this.on { it.getProjectIdByToken(eq("token111111")) }
                    .thenReturn("12345")
                this.on { it.getCartWeb(any(""), any("")) }
                    .thenAnswer {
                        "http://127.0.0.1:3088/project/${it.getArgument<String>(0)}" +
                                "/interface/api/cat_${it.getArgument<String>(1)}"
                    }
                this.on { it.saveApiInfo(any(HashMap())) }
                    .thenAnswer {
                        apis.add(it.getArgument(0))
                        true
                    }
            }
            builder.workAt(userCtrlPsiFile.parent!!)
        }

        fun testExportSpring() {
            yapiApiExporter.export()
            actionContext.waitComplete()
            apis.sortBy { it.toString() }
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(apis)
            )
            assertLinesEqualsIgnoreOrder(
                ResultLoader.load("log"),
                LoggerCollector.getLog().toUnixString()
            )
        }
    }

    class GenericMethodYapiApiExporterTest : YapiApiExporterTest() {
        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.methodDocEnable = true
                    settings.genericEnable = true
                    settings.yapiServer = "http://127.0.0.1:3088"
                    settings.yapiTokens = "test_default=token111111"
                }))
            }
            builder.mock<YapiApiHelper> {
                this.on { it.findCart(eq("token111111"), eq("rpc apis about user")) }
                    .thenReturn("333333")
                this.on { it.getProjectIdByToken(eq("token111111")) }
                    .thenReturn("12345")
                this.on { it.getCartWeb(any(""), any("")) }
                    .thenAnswer {
                        "http://127.0.0.1:3088/project/${it.getArgument<String>(0)}" +
                                "/interface/api/cat_${it.getArgument<String>(1)}"
                    }
                this.on { it.saveApiInfo(any(HashMap())) }
                    .thenAnswer {
                        apis.add(it.getArgument(0))
                        true
                    }
            }
            builder.workAt(userClientPsiFile)
        }

        fun testExportGenericMethod() {
            yapiApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(apis)
            )
            assertEquals(
                ResultLoader.load("log"),
                LoggerCollector.getLog().toUnixString()
            )
        }
    }
}
