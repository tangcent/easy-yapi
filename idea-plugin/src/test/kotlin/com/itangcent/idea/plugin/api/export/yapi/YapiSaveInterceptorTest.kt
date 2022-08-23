package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.spi.SpiCompositeLoader
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [YapiSaveInterceptor]
 */
internal class YapiSaveInterceptorTest : BaseContextTest() {

    private val settings = Settings()

    private val yapiApiHelper: YapiApiHelper = mock()
    private val messagesHelper: MessagesHelper = mock()

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(YapiApiHelper::class) { it.toInstance(yapiApiHelper) }
        builder.bind(MessagesHelper::class) { it.toInstance(messagesHelper) }
    }

    @Test
    fun beforeSaveApi() {
        val api1: HashMap<String, Any?> = hashMapOf(
            "name" to "test api",
            "path" to "/test",
            "method" to "GET",
            "token" to "123"
        )
        val api2: HashMap<String, Any?> = hashMapOf(
            "name" to "test api 2",
            "path" to "/test2",
            "method" to "POST",
            "token" to "123"
        )
        var answerTask = Messages.YES
        var answerApplyAll = false
        run {
            val apis = JsonArray()
            val api = JsonObject()
            api1.forEach { (k, v) -> api.addProperty(k, v?.toString()) }
            apis.add(api)
            Mockito.`when`(yapiApiHelper.getProjectIdByToken(any()))
                .thenReturn("123")
            Mockito.`when`(yapiApiHelper.findCarts(any(), any()))
                .thenReturn(arrayListOf(mapOf("_id" to 1)))
            Mockito.`when`(yapiApiHelper.listApis(com.itangcent.mock.any(""),
                com.itangcent.mock.any(""),
                Mockito.any()))
                .thenReturn(apis)

            Mockito.doAnswer {
                val callBack: (Int, Boolean) -> Unit = it.getArgument(2)!!
                callBack(answerTask, answerApplyAll)
            }.`when`(messagesHelper)
                .showAskWithApplyAllDialog(Mockito.any(), Mockito.any(), com.itangcent.mock.any { _, _ -> })
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_UPDATE.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.NO
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.NO
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            answerApplyAll = true
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.NO
            answerApplyAll = true
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.NO
            answerApplyAll = true
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))

            answerTask = Messages.YES
            answerApplyAll = true
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            assertTrue(saveInterceptor.beforeSaveApi(yapiApiHelper, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.CANCEL
            answerApplyAll = false
            assertFalse(saveInterceptor.beforeSaveApi(yapiApiHelper, api1))
            WaitHelper.waitUtil(5000) { actionContext.isStopped() }
        }
    }
}