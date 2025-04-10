package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
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
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [CachedYapiApiHelper]
 */
internal class CachedYapiApiHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var yapiApiHelper: YapiApiHelper

    private lateinit var httpClientProviderSpy: HttpClientProvider

    private val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(YapiApiHelper::class) { it.with(CachedYapiApiHelper::class).singleton() }

        val properties = Properties()
        properties["module1"] = VALID_TOKEN_1
        properties["module2"] = VALID_TOKEN_2
        properties["module3"] = INVALID_TOKEN
        settings.yapiTokens = ByteArrayOutputStream().also { properties.store(it, "") }.toString()
        settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
        settings.yapiServer = VALID_YAPI_SERVER
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }


        val mockHttpClientProvider = HttpClientProviderMockBuilder.builder()
            .url("$VALID_YAPI_SERVER${AbstractYapiApiHelper.GET_PROJECT_URL}?token=$VALID_TOKEN_1")
            .method("GET")
            .response(
                content = PROJECT_INFO,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${AbstractYapiApiHelper.GET_PROJECT_URL}?token=$VALID_TOKEN_2")
            .method("GET")
            .response(
                content = PROJECT_INFO_2,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${AbstractYapiApiHelper.GET_PROJECT_URL}?token=$INVALID_TOKEN")
            .method("GET")
            .response(
                content = CALL_FAILED,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${DefaultYapiApiHelper.GET_CAT_MENU}?project_id=$PROJECT_1&token=$VALID_TOKEN_1")
            .method("GET")
            .response(
                content = CART_MENU,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${DefaultYapiApiHelper.GET_CAT}?token=$VALID_TOKEN_1&catid=$CART_1&limit=1000")
            .method("GET")
            .response(
                content = LIST_CART_1,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${DefaultYapiApiHelper.SAVE_API}")
            .method("POST")
            .response(
                content = SAVE_OR_UPDATE_SUCCESS,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .url("$VALID_YAPI_SERVER${DefaultYapiApiHelper.ADD_CART}")
            .method("POST")
            .response(
                content = ADD_CART_SUCCESS,
                contentType = org.apache.http.entity.ContentType.APPLICATION_JSON
            )
            .build()
        httpClientProviderSpy = Mockito.spy<HttpClientProvider>(mockHttpClientProvider)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(httpClientProviderSpy)
        }
    }

    @Test
    fun testGetProjectIdByToken() {
        // First call should make a real API call
        assertEquals(PROJECT_1, yapiApiHelper.getProjectIdByToken(VALID_TOKEN_1))

        verify(httpClientProviderSpy, times(1)).getHttpClient()

        // Second call should use the cached value
        assertEquals(PROJECT_1, yapiApiHelper.getProjectIdByToken(VALID_TOKEN_1))
        verify(httpClientProviderSpy, times(1)).getHttpClient() // Still only one call

        // Invalid token should return null consistently
        assertNull(yapiApiHelper.getProjectIdByToken(INVALID_TOKEN))
        assertNull(yapiApiHelper.getProjectIdByToken(INVALID_TOKEN))
    }

    @Test
    fun testFindCarts() {
        // First call should make a real API call
        val carts = yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1)
        assertNotNull(carts)
        assertEquals(4, carts.size)
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        // Second call should use cached value
        val cachedCarts = yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1)
        assertNotNull(cachedCarts)
        assertEquals(4, cachedCarts.size)
        verify(httpClientProviderSpy, times(1)).getHttpClient()
    }

    @Test
    fun testListApis() {
        // First call should make a real API call
        val apis = yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        assertNotNull(apis)
        assertEquals(2, apis.size())
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        // Second call should use cached value
        val cachedApis = yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        assertNotNull(cachedApis)
        assertEquals(2, cachedApis.size())
        verify(httpClientProviderSpy, times(1)).getHttpClient()
    }

    @Test
    fun testSaveApiInfoClearsCache() {
        // Load APIs into cache
        val apis = yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        assertNotNull(apis)
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        val cachedApis = yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        assertNotNull(cachedApis)
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        // Save API which should invalidate cache
        val apiInfo = hashMapOf<String, Any?>(
            "token" to VALID_TOKEN_1,
            "catid" to CART_1,
            "method" to "PUT",
            "title" to "Test API",
            "path" to "/test/api"
        )

        assertTrue(yapiApiHelper.saveApiInfo(apiInfo))

        // Verify that another HTTP request will be made for the next listApis call
        yapiApiHelper.listApis(VALID_TOKEN_1, CART_1)
        verify(httpClientProviderSpy, times(3)).getHttpClient()
    }

    @Test
    fun testAddCartClearsCache() {
        // Load carts into cache
        val carts = yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1)
        assertNotNull(carts)
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1)
        assertNotNull(carts)
        verify(httpClientProviderSpy, times(1)).getHttpClient()

        // Add cart which should invalidate cache
        assertTrue(yapiApiHelper.addCart(PROJECT_1, VALID_TOKEN_1, "testCart", "Test description"))
        verify(httpClientProviderSpy, times(2)).getHttpClient()

        // Verify that another HTTP request will be made for the next findCarts call
        yapiApiHelper.findCarts(PROJECT_1, VALID_TOKEN_1)
        verify(httpClientProviderSpy, times(3)).getHttpClient()
    }

    companion object {
        const val VALID_YAPI_SERVER = "http://yapi.itangcent.com"
        const val VALID_TOKEN_1 = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce111"
        const val VALID_TOKEN_2 = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce7222"
        const val INVALID_TOKEN = "83bbd4a265eb021b136b94c2ebeabdfcdf7faa44dd660734bfe485d10cce7333"
        const val PROJECT_1 = "155080"
        const val PROJECT_2 = "218466"
        const val CART_1 = "470385"

        const val PROJECT_INFO = """
        {
          "errcode": 0,
          "errmsg": "成功！",
          "data": {
            "switch_notice": true,
            "is_mock_open": false,
            "strice": false,
            "is_json5": false,
            "_id": 155080,
            "name": "spring-demo-20220605",
            "desc": "",
            "basepath": "",
            "project_type": "private",
            "uid": 214663,
            "group_id": 213114,
            "icon": "code-o",
            "color": "gray",
            "add_time": 1654391146,
            "up_time": 1661267790,
            "role": "owner"
          }
        }
        """

        const val PROJECT_INFO_2 = """
        {
            "errcode": 0,
            "errmsg": "成功！",
            "data": {
                "switch_notice": true,
                "is_mock_open": false,
                "strice": false,
                "is_json5": false,
                "_id": 218466,
                "name": "test3",
                "desc": "",
                "basepath": "",
                "project_type": "private",
                "uid": 214663,
                "group_id": 214692,
                "icon": "code-o",
                "color": "yellow",
                "add_time": 1680437983,
                "up_time": 1680677555,
                "role": "owner"
            }
        }
        """

        const val CART_MENU = """
        {
          "errcode": 0,
          "errmsg": "成功！",
          "data": [
            {
              "index": 0,
              "_id": 466029,
              "name": "公共分类",
              "project_id": 155080,
              "desc": "公共分类",
              "uid": 214663,
              "add_time": 1654391146,
              "up_time": 1654391146,
              "__v": 0
            },
            {
              "index": 0,
              "_id": 470385,
              "name": "用户相关",
              "project_id": 155080,
              "desc": "用户相关",
              "uid": 214663,
              "add_time": 1654777919,
              "up_time": 1654777919,
              "__v": 0
            },
            {
              "index": 0,
              "_id": 528244,
              "name": "用户相关Client",
              "project_id": 155080,
              "desc": "用户相关Client",
              "uid": 214663,
              "add_time": 1661265770,
              "up_time": 1661265770,
              "__v": 0
            },
            {
              "index": 0,
              "_id": 528580,
              "name": "test",
              "project_id": 155080,
              "desc": "测试包",
              "uid": 214663,
              "add_time": 1661386891,
              "up_time": 1661386891,
              "__v": 0
            }
          ]
        }
        """

        const val LIST_CART_1 = """
        {
          "errcode": 0,
          "errmsg": "成功！",
          "data": {
            "count": 14,
            "total": 1,
            "list": [
              {
                "edit_uid": 0,
                "status": "done",
                "api_opened": false,
                "tag": [],
                "_id": 2109671,
                "method": "PUT",
                "title": "更新用户信息",
                "path": "/user/update",
                "catid": 470385,
                "project_id": 155080,
                "uid": 214663,
                "add_time": 1654777920
              },
              {
                "edit_uid": 0,
                "status": "done",
                "api_opened": false,
                "tag": [],
                "_id": 2109678,
                "title": "增加新用户",
                "path": "/user/add",
                "catid": 470385,
                "method": "POST",
                "project_id": 155080,
                "uid": 214663,
                "add_time": 1654777936
              }
            ]
          }
        }
        """

        const val ADD_CART_SUCCESS = """
        {
          "errcode": 0,
          "errmsg": "成功！",
          "data": {
            "index": 0,
            "name": "test",
            "project_id": 170566,
            "desc": "测试包",
            "uid": 214663,
            "add_time": 1661440466,
            "up_time": 1661440466,
            "_id": 529340,
            "__v": 0
          }
        }
        """

        const val SAVE_OR_UPDATE_SUCCESS = """
        {
          "errcode": 0,
          "errmsg": "成功！",
          "data": {
            "n": 1,
            "nModified": 1,
            "opTime": {
              "ts": "7135596324124098561",
              "t": 11
            },
            "electionId": "7fffffff000000000000000b",
            "ok": 1
          }
        }
        """

        const val CALL_FAILED = """
        {
          "errcode": 0,
          "errmsg": "failed！"
        }
        """
    }
}