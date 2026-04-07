package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DirectiveParserUnitTest {

    private lateinit var state: DirectiveState

    @Before
    fun setUp() {
        state = DirectiveState()
    }

    @Test
    fun testHandle_nonDirective() {
        val parser = DirectiveParser(state, null)
        assertFalse(parser.handle("some.key=value"))
    }

    @Test
    fun testHandle_setResolveProperty() {
        val parser = DirectiveParser(state, null)
        assertTrue(parser.handle("###set resolveProperty=false"))
        assertFalse(state.resolveProperty)
    }

    @Test
    fun testHandle_setResolveMulti() {
        val parser = DirectiveParser(state, null)
        assertTrue(parser.handle("###set resolveMulti=LONGEST"))
        assertEquals(ResolveMultiMode.LONGEST, state.resolveMulti)
    }

    @Test
    fun testHandle_setResolveMulti_invalid() {
        val parser = DirectiveParser(state, null)
        parser.handle("###set resolveMulti=INVALID")
        assertEquals(ResolveMultiMode.FIRST, state.resolveMulti)
    }

    @Test
    fun testHandle_setIgnoreNotFoundFile() {
        val parser = DirectiveParser(state, null)
        parser.handle("###set ignoreNotFoundFile=true")
        assertTrue(state.ignoreNotFoundFile)
    }

    @Test
    fun testHandle_setIgnoreUnresolved() {
        val parser = DirectiveParser(state, null)
        parser.handle("###set ignoreUnresolved=true")
        assertTrue(state.ignoreUnresolved)
    }

    @Test
    fun testHandle_ifTrue() {
        val settings = Settings(feignEnable = true)
        val parser = DirectiveParser(state, settings)
        parser.handle("###if feignEnable==true")
        assertTrue(state.isActive())
    }

    @Test
    fun testHandle_ifFalse() {
        val settings = Settings(feignEnable = false)
        val parser = DirectiveParser(state, settings)
        parser.handle("###if feignEnable==true")
        assertFalse(state.isActive())
    }

    @Test
    fun testHandle_ifNotEquals() {
        val settings = Settings(httpClient = "apache")
        val parser = DirectiveParser(state, settings)
        parser.handle("###if httpClient!=curl")
        assertTrue(state.isActive())
    }

    @Test
    fun testHandle_endif() {
        val settings = Settings(feignEnable = false)
        val parser = DirectiveParser(state, settings)
        parser.handle("###if feignEnable==true")
        assertFalse(state.isActive())
        parser.handle("###endif")
        assertTrue(state.isActive())
    }

    @Test
    fun testHandle_ifWithNullSettings() {
        val parser = DirectiveParser(state, null)
        parser.handle("###if feignEnable==true")
        assertFalse(state.isActive())
    }

    @Test
    fun testHandle_ifEmptyExpression() {
        val parser = DirectiveParser(state, Settings())
        parser.handle("###if ")
        assertFalse(state.isActive())
    }

    @Test
    fun testHandle_settingKeys() {
        val settings = Settings(
            httpTimeOut = 30,
            unsafeSsl = true,
            postmanToken = "tok-123",
            jaxrsEnable = true,
            actuatorEnable = true
        )
        val parser = DirectiveParser(state, settings)

        parser.handle("###if httpTimeOut==30")
        assertTrue(state.isActive())
        state.popCondition()

        parser.handle("###if unsafeSsl==true")
        assertTrue(state.isActive())
        state.popCondition()

        parser.handle("###if postmanToken==tok-123")
        assertTrue(state.isActive())
        state.popCondition()

        parser.handle("###if jaxrsEnable==true")
        assertTrue(state.isActive())
        state.popCondition()

        parser.handle("###if actuatorEnable==true")
        assertTrue(state.isActive())
    }

    @Test
    fun testHandle_whitespace() {
        val parser = DirectiveParser(state, null)
        assertTrue(parser.handle("  ###set resolveProperty=false  "))
        assertFalse(state.resolveProperty)
    }
}
