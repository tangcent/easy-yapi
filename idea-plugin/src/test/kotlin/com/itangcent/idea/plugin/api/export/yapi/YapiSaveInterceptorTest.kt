package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.dialog.ConfirmationDialogLabels
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContextBuilder
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
        "title" to "get user info",
        "path" to "/user/get/{id}",
        "method" to "GET",
        "token" to "token111111",
        "desc" to "<p>get user info of specified id</p>",
        "markdown" to "get user info of specified id",
        "req_headers" to listOf(
            mapOf(
                "name" to "Content-Type",
                "value" to "application/json",
                "example" to "application/json",
                "required" to 1
            ),
            mapOf(
                "name" to "token",
                "value" to "",
                "desc" to "auth token",
                "required" to 1
            )
        ),
        "req_query" to listOf(
            mapOf(
                "name" to "id",
                "value" to 0,
                "desc" to "user id",
                "required" to 0
            )
        ),
        "res_body_type" to "json",
        "res_body" to "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\",\"description\":\"response code\"},\"msg\":{\"type\":\"string\",\"description\":\"message\"},\"data\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"user id\"},\"type\":{\"type\":\"integer\",\"description\":\"user type\",\"enum\":[1,2,3],\"enumDesc\":\"1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"mock\":{\"mock\":\"@pick([1,2,3])\"}},\"name\":{\"type\":\"string\",\"description\":\"user name\"},\"age\":{\"type\":\"integer\",\"description\":\"user age\"},\"sex\":{\"type\":\"integer\",\"description\":\"\"},\"birthDay\":{\"type\":\"string\",\"description\":\"user birthDay\"},\"regtime\":{\"type\":\"string\",\"description\":\"user regtime\"}},\"description\":\"response data\"}},\"\$schema\":\"http://json-schema.org/draft-04/schema#\"}"
    )

    private val api2: HashMap<String, Any?> = hashMapOf(
        "_id" to 2,
        "title" to "create an user",
        "path" to "/user/add",
        "method" to "POST",
        "token" to "token111111",
        "desc" to "<p>create an new user</p>",
        "markdown" to "create an new user",
        "req_headers" to listOf(
            mapOf(
                "name" to "Content-Type",
                "value" to "application/json",
                "example" to "application/json",
                "required" to 1
            ),
            mapOf(
                "name" to "token",
                "value" to "",
                "desc" to "auth token",
                "required" to 1
            )
        ),
        "req_body_type" to "json",
        "req_body_other" to "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"user id\"},\"type\":{\"type\":\"integer\",\"description\":\"user type\",\"enum\":[1,2,3],\"enumDesc\":\"1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"mock\":{\"mock\":\"@pick([1,2,3])\"}},\"name\":{\"type\":\"string\",\"description\":\"user name\"},\"age\":{\"type\":\"integer\",\"description\":\"user age\"},\"sex\":{\"type\":\"integer\",\"description\":\"\"},\"birthDay\":{\"type\":\"string\",\"description\":\"user birthDay\"},\"regtime\":{\"type\":\"string\",\"description\":\"user regtime\"}},\"\$schema\":\"http://json-schema.org/draft-04/schema#\"}",
        "res_body_type" to "json",
        "res_body" to "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\",\"description\":\"response code\"},\"msg\":{\"type\":\"string\",\"description\":\"message\"},\"data\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"user id\"},\"type\":{\"type\":\"integer\",\"description\":\"user type\",\"enum\":[1,2,3],\"enumDesc\":\"1 :administration\\n2 :a person, an animal or a plant\\n3 :Anonymous visitor\",\"mock\":{\"mock\":\"@pick([1,2,3])\"}},\"name\":{\"type\":\"string\",\"description\":\"user name\"},\"age\":{\"type\":\"integer\",\"description\":\"user age\"},\"sex\":{\"type\":\"integer\",\"description\":\"\"},\"birthDay\":{\"type\":\"string\",\"description\":\"user birthDay\"},\"regtime\":{\"type\":\"string\",\"description\":\"user regtime\"}},\"description\":\"response data\"}},\"\$schema\":\"http://json-schema.org/draft-04/schema#\"}"
    )

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
        builder.bind(ConfigReader::class) {
            it.with(com.itangcent.idea.plugin.config.EnhancedConfigReader::class).singleton()
        }

        yapiApiHelper = mock()
        val apis = JsonArray()
        val api = Gson().toJsonTree(api1).asJsonObject
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
                com.itangcent.mock.any(ConfirmationDialogLabels()),
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
        settings.builtInConfig = ""
        val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))
    }

    @Test
    fun `test NeverUpdateYapiSaveInterceptor`() {
        settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
        settings.builtInConfig = ""
        val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

        answerTask = Messages.YES
        assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))

        answerTask = Messages.NO
        assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))
    }

    @Test
    fun `test AlwaysAskYapiSaveInterceptor`() {

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            settings.builtInConfig = ""
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))

            answerTask = Messages.NO
            assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            settings.builtInConfig = ""
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.YES
            answerApplyAll = true
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))

            answerTask = Messages.NO
            answerApplyAll = true
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            settings.builtInConfig = ""
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.NO
            answerApplyAll = true
            assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))

            answerTask = Messages.YES
            answerApplyAll = true
            assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
            assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, api2))
        }

        run {
            settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
            settings.builtInConfig = ""
            val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

            answerTask = Messages.CANCEL
            answerApplyAll = false
            assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))
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
            "title" to "get user info",
            "path" to "/user/get/{id}",
            "method" to "GET",
            "token" to "123",
            "desc" to "New description",
            "markdown" to "New markdown"
        )

        // Run interceptor
        assertEquals(null, saveInterceptor.beforeSaveApi(actionContext, apiInfo))

        // Assert that the existing description and markdown are retained
        assertEquals("<p>get user info of specified id</p>", apiInfo["desc"])
        assertEquals("get user info of specified id", apiInfo["markdown"])
    }

    @Test
    fun `test UpdateIfChangedYapiSaveInterceptor`() {
        settings.yapiExportMode = YapiExportMode.UPDATE_IF_CHANGED.name
        settings.builtInConfig = ""

        val saveInterceptor = SpiCompositeLoader.loadComposite<YapiSaveInterceptor>()

        // Test case 1: API doesn't exist - should return true to allow creation
        val newApi = hashMapOf<String, Any?>(
            "title" to "new api",
            "path" to "/new",
            "method" to "GET",
            "token" to "token111111"
        )
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, newApi))

        // Test case 2: API exists but hasn't changed - should return false to skip update
        assertEquals(false, saveInterceptor.beforeSaveApi(actionContext, api1))

        // Test case 3: API exists and has changed - should return true to allow update
        val changedApi = HashMap(api1)
        changedApi["desc"] = "updated description"
        changedApi["req_body_other"] = """{"newField": "value"}"""
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, changedApi))

        // Test cases for individual field changes
        // title change
        val titleChangedApi = HashMap(api1)
        titleChangedApi["title"] = "new title"
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, titleChangedApi))

        // desc change
        val descChangedApi = HashMap(api1)
        descChangedApi["desc"] = "new description"
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, descChangedApi))

        // req_body_type change
        val reqBodyTypeChangedApi = HashMap(api1)
        reqBodyTypeChangedApi["req_body_type"] = "form"
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, reqBodyTypeChangedApi))

        // res_body_type change
        val resBodyTypeChangedApi = HashMap(api1)
        resBodyTypeChangedApi["res_body_type"] = "raw"
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, resBodyTypeChangedApi))

        // req_body_other change
        val reqBodyOtherChangedApi = HashMap(api1)
        reqBodyOtherChangedApi["req_body_other"] = """{"newField": "value"}"""
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, reqBodyOtherChangedApi))

        // res_body change
        val resBodyChangedApi = HashMap(api1)
        resBodyChangedApi["res_body"] = """{"newField": "value"}"""
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, resBodyChangedApi))

        // req_headers change
        val reqHeadersChangedApi = HashMap(api1)
        reqHeadersChangedApi["req_headers"] = listOf(mapOf("name" to "Content-Type", "value" to "application/json"))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, reqHeadersChangedApi))

        // req_query change
        val reqQueryChangedApi = HashMap(api1)
        reqQueryChangedApi["req_query"] = listOf(mapOf("name" to "param", "value" to "value"))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, reqQueryChangedApi))

        // req_params change
        val reqParamsChangedApi = HashMap(api1)
        reqParamsChangedApi["req_params"] = listOf(mapOf("name" to "param", "value" to "value"))
        assertEquals(true, saveInterceptor.beforeSaveApi(actionContext, reqParamsChangedApi))
    }
}