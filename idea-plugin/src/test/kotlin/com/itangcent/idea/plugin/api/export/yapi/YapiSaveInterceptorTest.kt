package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.sub
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.spi.SpiCompositeLoader
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

/**
 * Test case of [YapiSaveInterceptor]
 */
internal class YapiSaveInterceptorTest : BaseContextTest() {

    private val settings = Settings()

    private lateinit var yapiApiHelper: YapiApiHelper
    private lateinit var messagesHelper: MessagesHelper

    private var answerTask = Messages.YES
    private var answerApplyAll = false

    private val api1: HashMap<String, Any?> = hashMapOf(
        "_id" to 1,
        "name" to "test api",
        "path" to "/test",
        "method" to "GET",
        "token" to "123",
        "desc" to "test api description",
        "markdown" to "test api markdown"
    )

    private val api2: HashMap<String, Any?> = hashMapOf(
        "_id" to 2,
        "name" to "test api 2",
        "path" to "/test2",
        "method" to "POST",
        "token" to "123",
        "desc" to "test api description 2",
        "markdown" to "test api markdown 2"
    )

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(ConfigReader::class) {
            it.with(com.itangcent.idea.plugin.config.EnhancedConfigReader::class).singleton()
        }

        yapiApiHelper = mock()
        val apis = JsonArray()
        val api = JsonObject()
        api1.forEach { (k, v) -> api.addProperty(k, v?.toString()) }
        apis.add(api)
        Mockito.`when`(yapiApiHelper.getProjectIdByToken(any()))
            .thenReturn("123")
        Mockito.`when`(yapiApiHelper.findCarts(any(), any()))
            .thenReturn(arrayListOf(mapOf("_id" to 1), mapOf("_id" to 2)))
        Mockito.`when`(
            yapiApiHelper.listApis(
                com.itangcent.mock.any(""),
                com.itangcent.mock.any(""),
                Mockito.any()
            )
        ).thenReturn(apis)
        Mockito.`when`(yapiApiHelper.getApiInfo(any(), any()))
            .thenAnswer {
                val id = it.getArgument(1, String::class.java)
                return@thenAnswer apis.find { api -> api.sub("_id")?.asString == id }
            }

        builder.bind(YapiApiHelper::class) { it.toInstance(yapiApiHelper) }

        messagesHelper = mock()
        Mockito.`when`(
            messagesHelper.showAskWithApplyAllDialog(
                Mockito.any(),
                Mockito.any(),
                com.itangcent.mock.any { _, _ -> })
        ).thenAnswer {
            val callBack: (Int, Boolean) -> Unit = it.getArgument(2)!!
            callBack(answerTask, answerApplyAll)
        }

        builder.bind(MessagesHelper::class) { it.toInstance(messagesHelper) }
    }

    @Test
    fun `test AlwaysUpdateYapiSaveInterceptor`() {
        settings.yapiExportMode = YapiExportMode.ALWAYS_UPDATE.name
        val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()
        assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
    }

    @Test
    fun `test NeverUpdateYapiSaveInterceptor`() {
        settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
        val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

        answerTask = Messages.YES
        assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

        answerTask = Messages.NO
        assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
    }

    @Test
    fun `test AlwaysAskYapiSaveInterceptor`() {

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.NO
            assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            answerApplyAll = true
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.NO
            answerApplyAll = true
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.NO
            answerApplyAll = true
            assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.YES
            answerApplyAll = true
            assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.CANCEL
            answerApplyAll = false
            assertEquals(false, saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            WaitHelper.waitUtil(5000) { actionContext.isStopped() }
        }
    }

    @Test
    fun `test NoUpdateDescriptionYapiSaveInterceptor`() {
        settings.yapiExportMode = YapiExportMode.ALWAYS_UPDATE.name
        settings.builtInConfig = """
            yapi.no_update.description=true
        """.trimIndent()

        val saveInterceptor = NoUpdateDescriptionYapiSaveInterceptor()

        val apiInfo = hashMapOf<String, Any?>(
            "name" to "test api",
            "path" to "/test",
            "method" to "GET",
            "token" to "123",
            "desc" to "New description",
            "markdown" to "New markdown"
        )

        // Run interceptor
        assertEquals(null, saveInterceptor.beforeSaveApi(yapiApiHelper, apiInfo))

        // Assert that the existing description and markdown are retained
        assertEquals("test api description", apiInfo["desc"])
        assertEquals("test api markdown", apiInfo["markdown"])
    }
}