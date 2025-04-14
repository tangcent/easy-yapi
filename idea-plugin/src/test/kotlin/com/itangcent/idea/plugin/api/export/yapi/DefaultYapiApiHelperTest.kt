package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.idea.plugin.api.export.yapi.AbstractYapiApiHelper.Companion.GET_PROJECT_URL
import com.itangcent.idea.plugin.api.export.yapi.DefaultYapiApiHelper.Companion.ADD_CART
import com.itangcent.idea.plugin.api.export.yapi.DefaultYapiApiHelper.Companion.GET_CAT_MENU
import com.itangcent.idea.plugin.api.export.yapi.DefaultYapiApiHelper.Companion.GET_INTERFACE
import com.itangcent.idea.plugin.api.export.yapi.DefaultYapiApiHelper.Companion.SAVE_API
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.test.HttpClientProviderMockBuilder
import com.itangcent.test.response404
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [DefaultYapiApiHelper]
 */
internal class DefaultYapiApiHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var yapiApiHelper: YapiApiHelper

    private val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(YapiApiHelper::class) { it.with(DefaultYapiApiHelper::class).singleton() }

        val properties = Properties()
        properties["module1"] = VALID_TOKEN_1
        properties["module2"] = VALID_TOKEN_2
        properties["module3"] = INVALID_TOKEN
        settings.yapiTokens = ByteArrayOutputStream().also { properties.store(it, "") }.toString()

        settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }

        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("$VALID_YAPI_SERVER${DefaultYapiApiHelper.GET_CAT}?token=$VALID_TOKEN_1&catid=$CART_1&limit=1000")
                    .method("GET")
                    .response(
                        content = LIST_CART_1,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_PROJECT_URL?token=$VALID_TOKEN_1")
                    .method("GET")
                    .response(
                        content = PROJECT_INFO,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_PROJECT_URL?token=$VALID_TOKEN_2")
                    .method("GET")
                    .response(
                        content = PROJECT_INFO_2,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_PROJECT_URL?token=$INVALID_TOKEN")
                    .method("GET")
                    .response(
                        content = CALL_FAILED,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_PROJECT_URL?token=$VALID_TOKEN_1&id=$PROJECT_1")
                    .method("GET")
                    .response(
                        content = PROJECT_INFO,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_PROJECT_URL?token=$VALID_TOKEN_2&id=$PROJECT_2")
                    .method("GET")
                    .response(
                        content = PROJECT_INFO_2,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$SAVE_API")
                    .method("POST")
                    .response(
                        content = SAVE_OR_UPDATE_SUCCESS,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_CAT_MENU?project_id=$PROJECT_1&token=$VALID_TOKEN_1")
                    .method("GET")
                    .response(
                        content = CART_MENU,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$ADD_CART")
                    .method("POST")
                    .response(
                        content = ADD_CART_SUCCESS,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .url("$VALID_YAPI_SERVER$GET_INTERFACE?token=$VALID_TOKEN_1&id=$INTER_1")
                    .method("GET")
                    .response(
                        content = INTER_1_INFO,
                        contentType = ContentType.APPLICATION_JSON
                    )
                    .notFound().response404()
                    .build()
            )
        }
    }

    @Test
    fun testGetApiInfo() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "{\"query_path\":{\"path\":\"/demo/user/update\",\"params\":[]},\"edit_uid\":0,\"status\":\"done\",\"type\":\"static\",\"req_body_is_json_schema\":false,\"res_body_is_json_schema\":true,\"api_opened\":false,\"index\":0,\"tag\":[],\"_id\":2109671,\"res_body\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"code\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"200\\\"},\\\"description\\\":\\\"响应码\\\"},\\\"msg\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"响应消息\\\"},\\\"day\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"20230103\\\"},\\\"description\\\":\\\"outer\\\"},\\\"data\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"day\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"20230103\\\"}},\\\"field\\\":{\\\"type\\\":\\\"integer\\\"},\\\"id\\\":{\\\"type\\\":\\\"integer\\\"},\\\"type\\\":{\\\"type\\\":\\\"integer\\\"},\\\"name\\\":{\\\"type\\\":\\\"string\\\"},\\\"age\\\":{\\\"type\\\":\\\"integer\\\"},\\\"sex\\\":{\\\"type\\\":\\\"integer\\\"},\\\"birthDay\\\":{\\\"type\\\":\\\"string\\\"},\\\"regtime\\\":{\\\"type\\\":\\\"string\\\"}},\\\"description\\\":\\\"响应数据\\\"}},\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\"method\":\"PUT\",\"res_body_type\":\"json\",\"title\":\"更新用户信息\",\"path\":\"/demo/user/update\",\"catid\":470385,\"markdown\":\"\",\"req_headers\":[{\"required\":\"1\",\"_id\":\"6429775698ac560015115ed5\",\"name\":\"Content-Type\",\"value\":\"application/x-www-form-urlencoded\",\"example\":\"application/x-www-form-urlencoded\"}],\"req_query\":[],\"desc\":\"<p></p>\",\"project_id\":155080,\"req_params\":[],\"uid\":214663,\"add_time\":1680439126,\"up_time\":1680439126,\"req_body_form\":[],\"__v\":0,\"username\":\"tangcent\"}",
            yapiApiHelper.getApiInfo(VALID_TOKEN_1, INTER_1).toString()
        )
        assertNull(yapiApiHelper.getApiInfo(VALID_TOKEN_1, "2109999"))
        assertNull(yapiApiHelper.getApiInfo(INVALID_TOKEN, "2209999"))
    }

    @Test
    fun testGetProjectWeb() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "http://yapi.itangcent.com/project/155080/interface/api",
            (yapiApiHelper as AbstractYapiApiHelper).getProjectWeb("module1")
        )
        assertNull((yapiApiHelper as AbstractYapiApiHelper).getProjectWeb("module3"))
    }

    @Test
    fun testFindApi() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals("2109671", yapiApiHelper.findApi(VALID_TOKEN_1, CART_1, "更新用户信息"))
        assertNull(yapiApiHelper.findApi(VALID_TOKEN_1, CART_1, "Get Chaos's current coordinates"))
        assertNull(yapiApiHelper.findApi(INVALID_TOKEN, CART_1, "更新用户信息"))
    }

    @Test
    fun testFindApis() {
        settings.yapiServer = VALID_YAPI_SERVER
        val apis = yapiApiHelper.findApis(VALID_TOKEN_1, CART_1)
        assertEquals(14, apis?.size)
        assertNull(yapiApiHelper.findApis(INVALID_TOKEN, CART_1))
    }

    @Test
    fun testListApis() {
        settings.yapiServer = VALID_YAPI_SERVER
        val apis = yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        assertEquals(14, apis?.size())
        assertNull(yapiApiHelper.listApis(INVALID_TOKEN, CART_1))
    }

    @Test
    fun testGetApiWeb() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "http://yapi.itangcent.com/project/155080/interface/api/2363738",
            yapiApiHelper.getApiWeb("module1", "用户相关", "删除用户信息")
        )
        assertNull(yapiApiHelper.getApiWeb("module1", "用户相关", "Get Chaos's current coordinates"))
        assertNull(yapiApiHelper.getApiWeb("module3", "用户相关", "删除用户信息"))
    }

    @Test
    fun testSaveApiInfo() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertFalse(
            yapiApiHelper.saveApiInfo(
                hashMapOf(
                    "token" to VALID_TOKEN_1,
                    "catid" to CART_1,
                    "status" to "done",
                    "api_opened" to false,
                    "tag" to arrayOf("deprecated"),
                    "method" to "PUT",
                    "title" to "更新用户信息",
                    "path" to "/user/update",
                    "catid" to 470385
                )
            )
        )
        assertTrue(
            yapiApiHelper.saveApiInfo(
                hashMapOf(
                    "token" to VALID_TOKEN_1,
                    "catid" to CART_1,
                    "status" to "done",
                    "api_opened" to false,
                    "tag" to arrayOf("deprecated"),
                    "method" to "PUT",
                    "title" to "更新用户信息(v2)",
                    "path" to "/v2/user/update",
                    "catid" to 470385
                )
            )
        )
    }

    @Test
    fun testFindCartWeb() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "http://yapi.itangcent.com/project/155080/interface/api/cat_470385",
            yapiApiHelper.findCartWeb("module1", "用户相关")
        )
        assertNull(yapiApiHelper.findCartWeb("module1", "Chaos's apis"))
        assertNull(yapiApiHelper.findCartWeb("module3", "用户相关"))
    }

    @Test
    fun testGetCartWeb() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "http://yapi.itangcent.com/project/155080/interface/api/cat_470385",
            yapiApiHelper.getCartWeb(PROJECT_1, CART_1)
        )
    }

    @Test
    fun testFindCart() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "470385",
            yapiApiHelper.findCart(VALID_TOKEN_1, "用户相关")
        )
        assertNull(yapiApiHelper.findCart(VALID_TOKEN_1, "Chaos's apis"))
        assertNull(yapiApiHelper.findCart(INVALID_TOKEN, "用户相关"))
    }

    @Test
    fun testAddCart() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertTrue(yapiApiHelper.addCart(VALID_TOKEN_1, "test", "测试包"))
        assertFalse(yapiApiHelper.addCart(INVALID_TOKEN, "test", "测试包"))
    }

    @Test
    fun testFindCarts() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "[{\"index\":0,\"_id\":466029,\"name\":\"公共分类\",\"project_id\":155080,\"desc\":\"公共分类\",\"uid\":214663,\"add_time\":1654391146,\"up_time\":1654391146,\"__v\":0}," +
                    "{\"index\":0,\"_id\":470385,\"name\":\"用户相关\",\"project_id\":155080,\"desc\":\"用户相关\",\"uid\":214663,\"add_time\":1654777919,\"up_time\":1654777919,\"__v\":0}," +
                    "{\"index\":0,\"_id\":528244,\"name\":\"用户相关Client\",\"project_id\":155080,\"desc\":\"用户相关Client\",\"uid\":214663,\"add_time\":1661265770,\"up_time\":1661265770,\"__v\":0}," +
                    "{\"index\":0,\"_id\":528580,\"name\":\"test\",\"project_id\":155080,\"desc\":\"测试包\",\"uid\":214663,\"add_time\":1661386891,\"up_time\":1661386891,\"__v\":0}]",
            yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1).toJson()
        )
    }

    @Test
    fun testFindCartById() {
        settings.yapiServer = VALID_YAPI_SERVER
        assertEquals(
            "{\"index\":0,\"_id\":528244,\"name\":\"用户相关Client\",\"project_id\":155080,\"desc\":\"用户相关Client\",\"uid\":214663,\"add_time\":1661265770,\"up_time\":1661265770,\"__v\":0}",
            yapiApiHelper.findCartById(VALID_TOKEN_1, "528244").toJson()
        )
    }

    @Test
    fun testCopyApi() {
        settings.yapiServer = VALID_YAPI_SERVER

        yapiApiHelper.copyApi(mapOf("token" to VALID_TOKEN_1), mapOf("token" to VALID_TOKEN_2))
        yapiApiHelper.copyApi(mapOf("token" to VALID_TOKEN_1), mapOf("token" to VALID_TOKEN_2, "catid" to "470386"))
        yapiApiHelper.copyApi(mapOf("token" to VALID_TOKEN_1, "catid" to "470386"), mapOf("token" to VALID_TOKEN_2))
        yapiApiHelper.copyApi(
            mapOf("token" to VALID_TOKEN_1, "catid" to CART_1),
            mapOf("token" to VALID_TOKEN_2, "catid" to "470386")
        )
        yapiApiHelper.copyApi(mapOf("token" to VALID_TOKEN_1, "id" to INTER_1), mapOf("token" to VALID_TOKEN_2))
        yapiApiHelper.copyApi(
            mapOf("token" to VALID_TOKEN_1, "id" to INTER_1),
            mapOf("token" to VALID_TOKEN_2, "catid" to "470386")
        )

        yapiApiHelper.copyApi(mapOf("module" to "module1"), mapOf("module" to "module2"))
        yapiApiHelper.copyApi(mapOf("module" to "module1"), mapOf("module" to "module2", "catid" to "470386"))
        yapiApiHelper.copyApi(mapOf("module" to "module1", "catid" to "470386"), mapOf("module" to "module2"))
        yapiApiHelper.copyApi(
            mapOf("module" to "module1", "catid" to CART_1),
            mapOf("module" to "module2", "catid" to "470386")
        )
        yapiApiHelper.copyApi(mapOf("module" to "module1", "id" to INTER_1), mapOf("module" to "module2"))
        yapiApiHelper.copyApi(
            mapOf("module" to "module1", "id" to INTER_1),
            mapOf("module" to "module2", "catid" to "470386")
        )
    }

    companion object {
        const val VALID_YAPI_SERVER = "http://yapi.itangcent.com"
        const val VALID_TOKEN_1 = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce111"
        const val VALID_TOKEN_2 = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce7222"
        const val INVALID_TOKEN = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce7333"
        const val PROJECT_1 = "155080"
        const val PROJECT_2 = "218466"
        const val CART_1 = "470385"
        const val INTER_1 = "2109671"

        const val INTER_1_INFO: String = "{\n" +
                "    \"errcode\": 0,\n" +
                "    \"errmsg\": \"成功！\",\n" +
                "    \"data\": {\n" +
                "        \"query_path\": {\n" +
                "            \"path\": \"/demo/user/update\",\n" +
                "            \"params\": []\n" +
                "        },\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"type\": \"static\",\n" +
                "        \"req_body_is_json_schema\": false,\n" +
                "        \"res_body_is_json_schema\": true,\n" +
                "        \"api_opened\": false,\n" +
                "        \"index\": 0,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2109671,\n" +
                "        \"res_body\": \"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"code\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"200\\\"},\\\"description\\\":\\\"响应码\\\"},\\\"msg\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"响应消息\\\"},\\\"day\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"20230103\\\"},\\\"description\\\":\\\"outer\\\"},\\\"data\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"day\\\":{\\\"type\\\":\\\"integer\\\",\\\"mock\\\":{\\\"mock\\\":\\\"20230103\\\"}},\\\"field\\\":{\\\"type\\\":\\\"integer\\\"},\\\"id\\\":{\\\"type\\\":\\\"integer\\\"},\\\"type\\\":{\\\"type\\\":\\\"integer\\\"},\\\"name\\\":{\\\"type\\\":\\\"string\\\"},\\\"age\\\":{\\\"type\\\":\\\"integer\\\"},\\\"sex\\\":{\\\"type\\\":\\\"integer\\\"},\\\"birthDay\\\":{\\\"type\\\":\\\"string\\\"},\\\"regtime\\\":{\\\"type\\\":\\\"string\\\"}},\\\"description\\\":\\\"响应数据\\\"}},\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\n" +
                "        \"method\": \"PUT\",\n" +
                "        \"res_body_type\": \"json\",\n" +
                "        \"title\": \"更新用户信息\",\n" +
                "        \"path\": \"/demo/user/update\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"markdown\": \"\",\n" +
                "        \"req_headers\": [\n" +
                "            {\n" +
                "                \"required\": \"1\",\n" +
                "                \"_id\": \"6429775698ac560015115ed5\",\n" +
                "                \"name\": \"Content-Type\",\n" +
                "                \"value\": \"application/x-www-form-urlencoded\",\n" +
                "                \"example\": \"application/x-www-form-urlencoded\"\n" +
                "            }\n" +
                "        ],\n" +
                "        \"req_query\": [],\n" +
                "        \"desc\": \"<p></p>\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"req_params\": [],\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1680439126,\n" +
                "        \"up_time\": 1680439126,\n" +
                "        \"req_body_form\": [],\n" +
                "        \"__v\": 0,\n" +
                "        \"username\": \"tangcent\"\n" +
                "    }\n" +
                "}"

        const val LIST_CART_1 = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"成功！\",\n" +
                "  \"data\": {\n" +
                "    \"count\": 14,\n" +
                "    \"total\": 1,\n" +
                "    \"list\": [\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2109671,\n" +
                "        \"method\": \"PUT\",\n" +
                "        \"title\": \"更新用户信息\",\n" +
                "        \"path\": \"/user/update\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1654777920\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2109678,\n" +
                "        \"title\": \"增加新用户\",\n" +
                "        \"path\": \"/user/add\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"method\": \"POST\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1654777936\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": true,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363696,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"打个招呼\",\n" +
                "        \"path\": \"/user/index\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994952\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [\n" +
                "          \"deprecated\"\n" +
                "        ],\n" +
                "        \"_id\": 2363703,\n" +
                "        \"method\": \"PUT\",\n" +
                "        \"title\": \"更新用户名\",\n" +
                "        \"path\": \"/user/set\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994955\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"undone\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [\n" +
                "          \"deprecated\"\n" +
                "        ],\n" +
                "        \"_id\": 2363710,\n" +
                "        \"title\": \"获取用户信息\",\n" +
                "        \"path\": \"/user/get/{id}\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994958\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"undone\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363717,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"根据用户id获取用户信息\",\n" +
                "        \"path\": \"/user/get\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994959\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363724,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"获取用户列表\",\n" +
                "        \"path\": \"/user/list\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994961\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363731,\n" +
                "        \"title\": \"获取指定类型用户列表\",\n" +
                "        \"path\": \"/user/list/{type}\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994962\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363738,\n" +
                "        \"title\": \"删除用户信息\",\n" +
                "        \"path\": \"/user/{id}\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"method\": \"DELETE\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994963\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363745,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"获取当前用户类型\",\n" +
                "        \"path\": \"/user/type\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994964\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363752,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"获取所有用户类型\",\n" +
                "        \"path\": \"/user/types\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994965\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [\n" +
                "          \"a\",\n" +
                "          \"zs\",\n" +
                "          \"b\",\n" +
                "          \"c\",\n" +
                "          \"deprecated\"\n" +
                "        ],\n" +
                "        \"_id\": 2363759,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"更新用户名\",\n" +
                "        \"path\": \"/user/set\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994967\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"done\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [\n" +
                "          \"public\"\n" +
                "        ],\n" +
                "        \"_id\": 2363766,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"title\": \"当前ctrl名称\",\n" +
                "        \"path\": \"/user/ctrl/name\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1660994971\n" +
                "      },\n" +
                "      {\n" +
                "        \"edit_uid\": 0,\n" +
                "        \"status\": \"undone\",\n" +
                "        \"api_opened\": false,\n" +
                "        \"tag\": [],\n" +
                "        \"_id\": 2363927,\n" +
                "        \"title\": \"根据用户id获取用户信息\",\n" +
                "        \"path\": \"/user/get000000000000000000000000000000000000?id={id}\",\n" +
                "        \"catid\": 470385,\n" +
                "        \"method\": \"GET\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1661045846\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}"

        const val PROJECT_INFO = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"成功！\",\n" +
                "  \"data\": {\n" +
                "    \"switch_notice\": true,\n" +
                "    \"is_mock_open\": false,\n" +
                "    \"strice\": false,\n" +
                "    \"is_json5\": false,\n" +
                "    \"_id\": 155080,\n" +
                "    \"name\": \"spring-demo-20220605\",\n" +
                "    \"desc\": \"\",\n" +
                "    \"basepath\": \"\",\n" +
                "    \"project_type\": \"private\",\n" +
                "    \"uid\": 214663,\n" +
                "    \"group_id\": 213114,\n" +
                "    \"icon\": \"code-o\",\n" +
                "    \"color\": \"gray\",\n" +
                "    \"add_time\": 1654391146,\n" +
                "    \"up_time\": 1661267790,\n" +
                "    \"env\": [\n" +
                "      {\n" +
                "        \"header\": [],\n" +
                "        \"global\": [],\n" +
                "        \"_id\": \"629c016a5d3cd000151779ab\",\n" +
                "        \"name\": \"local\",\n" +
                "        \"domain\": \"http://127.0.0.1\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"tag\": [],\n" +
                "    \"cat\": [\n" +
                "      {\n" +
                "        \"index\": 0,\n" +
                "        \"_id\": 466029,\n" +
                "        \"name\": \"公共分类\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"desc\": \"公共分类\",\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1654391146,\n" +
                "        \"up_time\": 1654391146,\n" +
                "        \"__v\": 0\n" +
                "      },\n" +
                "      {\n" +
                "        \"index\": 0,\n" +
                "        \"_id\": 470385,\n" +
                "        \"name\": \"用户相关\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"desc\": \"用户相关\",\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1654777919,\n" +
                "        \"up_time\": 1654777919,\n" +
                "        \"__v\": 0\n" +
                "      },\n" +
                "      {\n" +
                "        \"index\": 0,\n" +
                "        \"_id\": 528244,\n" +
                "        \"name\": \"用户相关Client\",\n" +
                "        \"project_id\": 155080,\n" +
                "        \"desc\": \"用户相关Client\",\n" +
                "        \"uid\": 214663,\n" +
                "        \"add_time\": 1661265770,\n" +
                "        \"up_time\": 1661265770,\n" +
                "        \"__v\": 0\n" +
                "      }\n" +
                "    ],\n" +
                "    \"role\": \"owner\"\n" +
                "  }\n" +
                "}"

        const val PROJECT_INFO_2 = "{\n" +
                "    \"errcode\": 0,\n" +
                "    \"errmsg\": \"成功！\",\n" +
                "    \"data\": {\n" +
                "        \"switch_notice\": true,\n" +
                "        \"is_mock_open\": false,\n" +
                "        \"strice\": false,\n" +
                "        \"is_json5\": false,\n" +
                "        \"_id\": 218466,\n" +
                "        \"name\": \"test3\",\n" +
                "        \"desc\": \"\",\n" +
                "        \"basepath\": \"\",\n" +
                "        \"project_type\": \"private\",\n" +
                "        \"uid\": 214663,\n" +
                "        \"group_id\": 214692,\n" +
                "        \"icon\": \"code-o\",\n" +
                "        \"color\": \"yellow\",\n" +
                "        \"add_time\": 1680437983,\n" +
                "        \"up_time\": 1680677555,\n" +
                "        \"env\": [\n" +
                "            {\n" +
                "                \"header\": [],\n" +
                "                \"global\": [],\n" +
                "                \"_id\": \"642972dfb3b5e40015b8c70f\",\n" +
                "                \"name\": \"local\",\n" +
                "                \"domain\": \"http://127.0.0.1\"\n" +
                "            }\n" +
                "        ],\n" +
                "        \"tag\": [],\n" +
                "        \"cat\": [\n" +
                "            {\n" +
                "                \"index\": 0,\n" +
                "                \"_id\": 728774,\n" +
                "                \"name\": \"公共分类\",\n" +
                "                \"project_id\": 218466,\n" +
                "                \"desc\": \"公共分类\",\n" +
                "                \"uid\": 214663,\n" +
                "                \"add_time\": 1680437983,\n" +
                "                \"up_time\": 1680437983,\n" +
                "                \"__v\": 0\n" +
                "            },\n" +
                "            {\n" +
                "                \"index\": 0,\n" +
                "                \"_id\": 730856,\n" +
                "                \"name\": \"用户相关\",\n" +
                "                \"project_id\": 218466,\n" +
                "                \"desc\": \"\",\n" +
                "                \"uid\": 214663,\n" +
                "                \"add_time\": 1680567149,\n" +
                "                \"up_time\": 1680567149,\n" +
                "                \"__v\": 0\n" +
                "            },\n" +
                "            {\n" +
                "                \"index\": 0,\n" +
                "                \"_id\": 730863,\n" +
                "                \"name\": \"Json字段测试相关\",\n" +
                "                \"project_id\": 218466,\n" +
                "                \"desc\": \"\",\n" +
                "                \"uid\": 214663,\n" +
                "                \"add_time\": 1680567171,\n" +
                "                \"up_time\": 1680567171,\n" +
                "                \"__v\": 0\n" +
                "            }\n" +
                "        ],\n" +
                "        \"role\": \"owner\"\n" +
                "    }\n" +
                "}"

        const val CART_MENU = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"成功！\",\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"_id\": 466029,\n" +
                "      \"name\": \"公共分类\",\n" +
                "      \"project_id\": 155080,\n" +
                "      \"desc\": \"公共分类\",\n" +
                "      \"uid\": 214663,\n" +
                "      \"add_time\": 1654391146,\n" +
                "      \"up_time\": 1654391146,\n" +
                "      \"__v\": 0\n" +
                "    },\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"_id\": 470385,\n" +
                "      \"name\": \"用户相关\",\n" +
                "      \"project_id\": 155080,\n" +
                "      \"desc\": \"用户相关\",\n" +
                "      \"uid\": 214663,\n" +
                "      \"add_time\": 1654777919,\n" +
                "      \"up_time\": 1654777919,\n" +
                "      \"__v\": 0\n" +
                "    },\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"_id\": 528244,\n" +
                "      \"name\": \"用户相关Client\",\n" +
                "      \"project_id\": 155080,\n" +
                "      \"desc\": \"用户相关Client\",\n" +
                "      \"uid\": 214663,\n" +
                "      \"add_time\": 1661265770,\n" +
                "      \"up_time\": 1661265770,\n" +
                "      \"__v\": 0\n" +
                "    },\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"_id\": 528580,\n" +
                "      \"name\": \"test\",\n" +
                "      \"project_id\": 155080,\n" +
                "      \"desc\": \"测试包\",\n" +
                "      \"uid\": 214663,\n" +
                "      \"add_time\": 1661386891,\n" +
                "      \"up_time\": 1661386891,\n" +
                "      \"__v\": 0\n" +
                "    }\n" +
                "  ]\n" +
                "}"

        const val SAVE_OR_UPDATE_SUCCESS = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"成功！\",\n" +
                "  \"data\": {\n" +
                "    \"n\": 1,\n" +
                "    \"nModified\": 1,\n" +
                "    \"opTime\": {\n" +
                "      \"ts\": \"7135596324124098561\",\n" +
                "      \"t\": 11\n" +
                "    },\n" +
                "    \"electionId\": \"7fffffff000000000000000b\",\n" +
                "    \"ok\": 1\n" +
                "  }\n" +
                "}"

        const val ADD_CART_SUCCESS = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"成功！\",\n" +
                "  \"data\": {\n" +
                "    \"index\": 0,\n" +
                "    \"name\": \"test\",\n" +
                "    \"project_id\": 170566,\n" +
                "    \"desc\": \"测试包\",\n" +
                "    \"uid\": 214663,\n" +
                "    \"add_time\": 1661440466,\n" +
                "    \"up_time\": 1661440466,\n" +
                "    \"_id\": 529340,\n" +
                "    \"__v\": 0\n" +
                "  }\n" +
                "}"

        const val CALL_FAILED = "{\n" +
                "  \"errcode\": 0,\n" +
                "  \"errmsg\": \"failed！\"" +
                "}"

    }
}