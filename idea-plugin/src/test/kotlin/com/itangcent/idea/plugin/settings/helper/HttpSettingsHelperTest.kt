package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.Assert.assertArrayEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [HttpSettingsHelper]
 */
internal class HttpSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var httpSettingsHelper: HttpSettingsHelper

    @Test
    fun testCheckTrustUrl() {
        settings.trustHosts = arrayOf(
                "https://raw.githubusercontent.com",
                "!https://raw.githubusercontent.com/itangcent",
                "!http://192.168.1.1",
                "!http://localhost",
                "https://api.getpostman.com")
        assertTrue(httpSettingsHelper.checkTrustUrl("https://raw.githubusercontent.com"))
        assertTrue(httpSettingsHelper.checkTrustUrl("https://raw.githubusercontent.com/tangcent"))
        assertTrue(httpSettingsHelper.checkTrustUrl("https://raw.githubusercontent.com/tangcent/easy-yapi/master/third/swagger.config"))
        assertFalse(httpSettingsHelper.checkTrustUrl("https://raw.githubusercontent.com/itangcent"))
        assertFalse(httpSettingsHelper.checkTrustUrl("https://raw.githubusercontent.com/itangcent/easy-yapi/master/third/swagger.config"))
        assertFalse(httpSettingsHelper.checkTrustUrl("http://192.168.1.1"))
        assertFalse(httpSettingsHelper.checkTrustUrl("http://192.168.1.1/index"))
        assertFalse(httpSettingsHelper.checkTrustUrl("http://localhost"))
        assertFalse(httpSettingsHelper.checkTrustUrl("http://localhost/a"))
        assertTrue(httpSettingsHelper.checkTrustUrl("https://api.getpostman.com"))
        assertTrue(httpSettingsHelper.checkTrustUrl("https://api.getpostman.com/collections"))
    }

    @Test
    fun testCheckTrustHost() {
        settings.trustHosts = arrayOf(
                "https://raw.githubusercontent.com",
                "!https://raw.githubusercontent.com/itangcent",
                "!http://192.168.1.1",
                "!http://localhost",
                "https://api.getpostman.com")
        assertTrue(httpSettingsHelper.checkTrustHost("https://raw.githubusercontent.com"))
        assertFalse(httpSettingsHelper.checkTrustHost("https://raw.githubusercontent.com/tangcent"))
        assertFalse(httpSettingsHelper.checkTrustHost("https://raw.githubusercontent.com/itangcent"))
        assertFalse(httpSettingsHelper.checkTrustHost("http://192.168.1.1"))
        assertFalse(httpSettingsHelper.checkTrustHost("http://localhost"))
        assertTrue(httpSettingsHelper.checkTrustHost("https://api.getpostman.com"))
    }

    @Test
    fun testAddTrustHost() {
        settings.trustHosts = emptyArray()
        httpSettingsHelper.addTrustHost("https://raw.githubusercontent.com")
        assertArrayEquals(settings.trustHosts, arrayOf("https://raw.githubusercontent.com"))
        httpSettingsHelper.addTrustHost("https://raw.githubusercontent.com")
        assertArrayEquals(settings.trustHosts, arrayOf("https://raw.githubusercontent.com"))
        httpSettingsHelper.addTrustHost("!https://127.0.0.1")
        assertArrayEquals(settings.trustHosts,
                arrayOf("https://raw.githubusercontent.com", "!https://127.0.0.1"))
    }

    @Test
    fun testResolveHost() {
        assertEquals("https://raw.githubusercontent.com",
                httpSettingsHelper.resolveHost("https://raw.githubusercontent.com"))
        assertEquals("https://raw.githubusercontent.com/tangcent",
                httpSettingsHelper.resolveHost("https://raw.githubusercontent.com/tangcent"))
        assertEquals("https://raw.githubusercontent.com/tangcent",
                httpSettingsHelper.resolveHost("https://raw.githubusercontent.com/tangcent/easy-yapi/master/third/swagger.config"))
        assertEquals("https://api.getpostman.com",
                httpSettingsHelper.resolveHost("https://api.getpostman.com/collections"))
        assertEquals("http://127.0.0.1",
                httpSettingsHelper.resolveHost("http://127.0.0.1/a/b/c"))
        assertEquals("https://127.0.0.1",
                httpSettingsHelper.resolveHost("https://127.0.0.1/a/b/c"))
    }

    @Test
    fun testHttpTimeOut() {
        settings.httpTimeOut = 10
        assertEquals(10, httpSettingsHelper.httpTimeOut(TimeUnit.SECONDS))
        assertEquals(10000, httpSettingsHelper.httpTimeOut(TimeUnit.MILLISECONDS))
    }
}