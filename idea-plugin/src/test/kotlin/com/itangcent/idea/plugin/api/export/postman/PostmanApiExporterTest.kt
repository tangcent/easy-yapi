package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.psi.PsiFile
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.*
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit.STANDARD_TIME
import com.itangcent.test.mock
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.condition.OS
import org.mockito.Mockito
import org.mockito.kotlin.eq
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [PostmanApiExporter]
 */
internal abstract class PostmanApiExporterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    protected lateinit var postmanApiExporter: PostmanApiExporter

    @Inject
    protected lateinit var postmanFormatter: PostmanFormatter

    protected lateinit var userCtrlPsiFile: PsiFile

    protected lateinit var userClientPsiFile: PsiFile

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

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

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(STANDARD_TIME))
        }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("postman"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))
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

    class SpringPostmanApiExporterTest : PostmanApiExporterTest() {

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile)
            builder.mock(PostmanApiHelper::class)
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()!!.trim().toUnixString()
            )
        }
    }

    class ModeCopyPostmanApiExporterTest : PostmanApiExporterTest() {

        private var createdCollection: Any? = null

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.postmanToken = "token-123456789"
                    settings.postmanExportMode = PostmanExportMode.COPY.name
                    settings.postmanWorkspace = "workspace-123456789"
                }))
            }
            builder.workAt(userCtrlPsiFile)
            builder.mock(PostmanApiHelper::class) {
                Mockito.`when`(it.createCollection(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
                    .thenAnswer { invocationOnMock ->
                        createdCollection = invocationOnMock.getArgument(0)
                        return@thenAnswer hashMapOf("name" to "collection-123456")
                    }
            }
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(createdCollection).toUnixString()
            )
        }
    }

    class ModeUpdatePostmanApiExporterTest : PostmanApiExporterTest() {

        private val updatedCollections = HashMap<String, Any>()
        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                    settings.postmanToken = "token-123456789"
                    settings.postmanExportMode = PostmanExportMode.UPDATE.name
                    settings.postmanWorkspace = "workspace-123456789"
                    settings.postmanCollections = "test_default=collection-123456789"
                }))
            }
            builder.workAt(userCtrlPsiFile)
            val collection123456789Json = ResultLoader.load("collection-123456789")
            builder.mock(PostmanApiHelper::class) {
                Mockito.`when`(it.getCollectionInfo(eq("collection-123456789")))
                    .thenReturn(
                        GsonUtils.fromJson<HashMap<String, Any?>>(
                            collection123456789Json
                        )!!
                    )
                Mockito.`when`(it.updateCollection(any("123"), any(hashMapOf())))
                    .thenAnswer { invocationOnMock ->
                        updatedCollections[invocationOnMock.getArgument(0)] = invocationOnMock.getArgument(1)
                        return@thenAnswer true
                    }
            }
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                GsonUtils.prettyJson(updatedCollections["collection-123456789"]).toUnixString()
            )
        }
    }

    class DirectorySpringPostmanApiExporterTest : PostmanApiExporterTest() {

        override fun beforeBind() {
            super.beforeBind()
            loadFile("api/TestCtrl.java")
        }

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.inferEnable = true
                }))
            }
            builder.workAt(userCtrlPsiFile.parent!!)
            builder.mock(PostmanApiHelper::class)
        }

        fun testExportSpring() {
            postmanApiExporter.export()
            actionContext.waitComplete()
            assertEquals(
                ResultLoader.load(),
                (fileSaveHelper as FileSaveHelperAdaptor).content()!!.trim().toUnixString(),
            )
        }
    }
}