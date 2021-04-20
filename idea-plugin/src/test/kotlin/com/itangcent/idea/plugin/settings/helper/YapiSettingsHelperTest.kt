package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        val messagesHelper = Mockito.mock(MessagesHelper::class.java)
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
                Mockito.eq("Server Of Yapi"), Mockito.any()))
                .thenReturn("http://127.0.0.1:3000")
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
                Mockito.eq("Yapi ProjectId"), Mockito.any()))
                .thenReturn("66")
        Mockito.`when`(messagesHelper.showInputDialog(Mockito.anyString(),
                Mockito.eq("Yapi Private Token"), Mockito.any()))
                .thenReturn("123456789")
        builder.bindInstance(MessagesHelper::class, messagesHelper)
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
        assertNull(settings.postmanToken)
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer(false))
        assertEquals("http://127.0.0.1:3000", settings.yapiServer)
        assertEquals("http://127.0.0.1:3000", yapiSettingsHelper.getServer())
    }

    @Test
    fun testGetPrivateToken() {
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo", false))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))

        settings.loginMode = true
        assertNull(yapiSettingsHelper.getPrivateToken("login-demo"))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo", false))
        assertEquals("66", yapiSettingsHelper.getPrivateToken("login-demo"))
    }

    @Test
    fun testInputNewToken() {
        settings.loginMode = false
        assertEquals("123456789", yapiSettingsHelper.inputNewToken())
        settings.loginMode = true
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
        settings.loginMode = false
        assertNull(yapiSettingsHelper.getPrivateToken("demo"))
        yapiSettingsHelper.setToken("demo", "123456789")
        yapiSettingsHelper.setToken("demo2", "123456789")
        yapiSettingsHelper.setToken("demo3", "987654321")
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo"))
        assertEquals("123456789", yapiSettingsHelper.getPrivateToken("demo2"))
        assertEquals("987654321", yapiSettingsHelper.getPrivateToken("demo3"))
        assertEquals("{\"demo3\":\"987654321\",\"demo\":\"123456789\",\"demo2\":\"123456789\"}", yapiSettingsHelper.readTokens().toJson())

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