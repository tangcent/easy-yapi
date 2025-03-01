package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [YapiSettingsHelper]
 */
internal class YapiSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var yapiSettingsHelper: YapiSettingsHelper

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }

        val messagesHelper = Mockito.mock(MessagesHelper::class.java)
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
            Mockito.eq("Server Of Yapi"), Mockito.any()))
            .thenReturn(null, "http://127.0.0.1:3000")
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
            Mockito.eq("Yapi ProjectId"), Mockito.any()))
            .thenReturn(null, "66")
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
            Mockito.eq("Yapi Private Token"), Mockito.any()))
            .thenReturn(null, "123456789")
        builder.bindInstance(MessagesHelper::class, messagesHelper)

        val yapiTokenChecker = object : YapiTokenChecker {
            override fun checkToken(token: String): Boolean {
                return !token.startsWith("abcd")
            }
        }
        builder.bindInstance(YapiTokenChecker::class, yapiTokenChecker)
    }

    @Test
    fun testHasServer() {
        assertFalse(yapiSettingsHelper.hasServer())
        settings.yapiServer = "http://127.0.0.1:3000"
        assertTrue(yapiSettingsHelper.hasServer())
    }

    @Test
    fun testGetServer() {
        assertNull(yapiSettingsHelper.getServer())
        assertNull(settings.yapiServer)
        assertNull(yapiSettingsHelper.getServer(false))
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer(false))
        assertEquals("http://127.0.0.1:3000", settings.yapiServer)
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer())
    }

    @Test
    fun testGetServerWithExistingSetting() {
        settings.yapiServer = "http://127.0.0.1:3000"
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer())
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer(false))
    }

    @Test
    fun testGetPrivateToken() {
        //test normal mode
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        assertNull(yapiSettingsHelper.getPrivateToken("demo", false))
        assertNull(yapiSettingsHelper.getPrivateToken("demo", false))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo2", false))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo2"))

        //test login mode
        settings.loginMode = true
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo"))
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo", false))
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo2", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo2"))

        //test normal mode & illegal token
        settings.loginMode = false
        yapiSettingsHelper.setToken("demo-illegal", "abcdefghijklmnopqrst")
        assertNull(yapiSettingsHelper.getPrivateToken("demo-illegal"))
        LoggerCollector.getLog().let { log ->
            assertTrue(log.contains("Please switch to loginModel if the version of yapi is before 1.6.0"))
            assertTrue(log.contains("For more details see: http://easyyapi.com/documents/login_mode_yapi.html"))
        }
        yapiSettingsHelper.setToken("demo-illegal2", "dcbaefghijklmnopqrst")
        assertEquals("dcbaefghijklmnopqrst", yapiSettingsHelper.getPrivateToken("demo-illegal2"))
        LoggerCollector.getLog().let { log ->
            assertFalse(log.contains("Please switch to loginModel if the version of yapi is before 1.6.0"))
            assertFalse(log.contains("For more details see: http://easyyapi.com/documents/login_mode_yapi.html"))
        }
    }

    @Test
    fun testGetPrivateTokenWithExistingTokens() {
        val properties = Properties()
        properties["demo"] = "123456789"
        properties["login-demo"] = "66"

        settings.yapiTokens = ByteArrayOutputStream().also { properties.store(it, "") }.toString()

        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo"))
    }

    @Test
    fun testInputNewToken() {
        settings.loginMode = false
        assertEquals(null, yapiSettingsHelper.inputNewToken())
        assertEquals("123456789", yapiSettingsHelper.inputNewToken())
        settings.loginMode = true
        assertEquals(null, yapiSettingsHelper.inputNewToken())
        assertEquals("66", yapiSettingsHelper.inputNewToken())
    }

    @Test
    fun testDisableTemp() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.disableTemp("123456789")
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
    }

    @Test
    fun testSetToken() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))

        settings.loginMode = true
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo"))
        yapiSettingsHelper.setToken("login-demo", "66")
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo"))
    }

    @Test
    fun testRemoveTokenByModule() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.removeTokenByModule("demo")
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))

        settings.loginMode = true
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo"))
        yapiSettingsHelper.setToken("login-demo", "66")
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo"))
        yapiSettingsHelper.removeTokenByModule("login-demo")
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo"))
    }

    @Test
    fun testRemoveToken() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        yapiSettingsHelper.setToken("demo2", "123456789")
        yapiSettingsHelper.setToken("demo3", "987654321")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo2"))
        assertEquals("987654321", yapiSettingsHelper.getPrivateToken("demo3"))
        yapiSettingsHelper.removeToken("123456789")
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        assertNull(yapiSettingsHelper.getPrivateToken("demo2"))
        assertEquals("987654321", yapiSettingsHelper.getPrivateToken("demo3"))
    }

    @Test
    fun testReadTokens() {
        val properties = Properties()
        properties["demo"] = "123456789"
        properties["demo2"] = "123456789"
        properties["demo3"] = "987654321"

        settings.yapiTokens = ByteArrayOutputStream().also { properties.store(it, "") }.toString()

        assertEquals("{\"demo3\":\"987654321\",\"demo\":\"123456789\",\"demo2\":\"123456789\"}",
            yapiSettingsHelper.readTokens().toJson())
    }

    @Test
    fun testReadTokensWithExistingTokens() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        yapiSettingsHelper.setToken("demo2", "123456789")
        yapiSettingsHelper.setToken("demo3", "987654321")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo2"))
        assertEquals("987654321", yapiSettingsHelper.getPrivateToken("demo3"))
        assertEquals("{\"demo3\":\"987654321\",\"demo\":\"123456789\",\"demo2\":\"123456789\"}",
            yapiSettingsHelper.readTokens().toJson())
    }

    @Test
    fun testRawToken() {
        settings.loginMode = false
        assertEquals("123456789", yapiSettingsHelper.rawToken("123456789"))

        settings.loginMode = true
        assertEquals("", yapiSettingsHelper.rawToken("66"))
    }

    @Test
    fun testEnableUrlTemplating() {
        settings.enableUrlTemplating = false
        assertFalse(yapiSettingsHelper.enableUrlTemplating())
        settings.enableUrlTemplating = true
        assertTrue(yapiSettingsHelper.enableUrlTemplating())
    }

    @Test
    fun testLoginMode() {
        settings.loginMode = false
        assertFalse(yapiSettingsHelper.loginMode())
        settings.loginMode = true
        assertTrue(yapiSettingsHelper.loginMode())
    }

    @Test
    fun testExportMode() {
        assertEquals(YapiExportMode.ALWAYS_UPDATE, yapiSettingsHelper.exportMode())
        settings.yapiExportMode = YapiExportMode.ALWAYS_ASK.name
        assertEquals(YapiExportMode.ALWAYS_ASK, yapiSettingsHelper.exportMode())
        settings.yapiExportMode = YapiExportMode.NEVER_UPDATE.name
        assertEquals(YapiExportMode.NEVER_UPDATE, yapiSettingsHelper.exportMode())
        settings.yapiExportMode = YapiExportMode.ALWAYS_UPDATE.name
        assertEquals(YapiExportMode.ALWAYS_UPDATE, yapiSettingsHelper.exportMode())
    }

    @Test
    fun testSwitchNotice() {
        settings.switchNotice = false
        assertFalse(yapiSettingsHelper.switchNotice())
        settings.switchNotice = true
        assertTrue(yapiSettingsHelper.switchNotice())
    }

    @Test
    fun testYapiReqBodyJson5() {
        settings.yapiReqBodyJson5 = false
        assertFalse(yapiSettingsHelper.yapiReqBodyJson5())
        settings.yapiReqBodyJson5 = true
        assertTrue(yapiSettingsHelper.yapiReqBodyJson5())
    }

    @Test
    fun testYapiResBodyJson5() {
        settings.yapiResBodyJson5 = false
        assertFalse(yapiSettingsHelper.yapiResBodyJson5())
        settings.yapiResBodyJson5 = true
        assertTrue(yapiSettingsHelper.yapiResBodyJson5())
    }
}


internal class YapiSettingsHelperWithConfigTest : SettingsHelperTest() {

    @Inject
    private lateinit var yapiSettingsHelper: YapiSettingsHelper

    override fun customConfig(): String {
        return "yapi.server=http://127.0.0.1:3000\n" +
                "yapi.token.demo=123456789\n" +
                "yapi.token.login-demo=66"
    }

    @Test
    fun testGetServerFromConfig() {
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer())
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer(false))
    }

    @Test
    fun testGetPrivateTokenFromConfig() {
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo"))
    }
}