package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class DirectiveParserTest {

    @Test
    fun testHandleSetResolveProperty() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        parser.handle("###set resolveProperty=false")
        assertFalse("resolveProperty should be false", state.resolveProperty)
    }

    @Test
    fun testHandleSetResolvePropertyTrue() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        parser.handle("###set resolveProperty=true")
        assertTrue("resolveProperty should be true", state.resolveProperty)
    }

    @Test
    fun testHandleSetIgnoreNotFoundFile() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        parser.handle("###set ignoreNotFoundFile=true")
        assertTrue("ignoreNotFoundFile should be true", state.ignoreNotFoundFile)
    }

    @Test
    fun testHandleSetIgnoreUnresolved() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        parser.handle("###set ignoreUnresolved=true")
        assertTrue("ignoreUnresolved should be true", state.ignoreUnresolved)
    }

    @Test
    fun testHandleIfConditionTrue() {
        val settings = Settings(httpClient = "APACHE")
        val state = DirectiveState()
        val parser = DirectiveParser(state, settings)
        parser.handle("###if httpClient==APACHE")
        assertTrue("Condition should be active", state.isActive())
    }

    @Test
    fun testHandleIfConditionFalse() {
        val settings = Settings(httpClient = "APACHE")
        val state = DirectiveState()
        val parser = DirectiveParser(state, settings)
        parser.handle("###if httpClient==URL_CONNECTION")
        assertFalse("Condition should not be active", state.isActive())
    }

    @Test
    fun testHandleIfNotEquals() {
        val settings = Settings(httpClient = "APACHE")
        val state = DirectiveState()
        val parser = DirectiveParser(state, settings)
        parser.handle("###if httpClient!=URL_CONNECTION")
        assertTrue("Not-equals condition should be active", state.isActive())
    }

    @Test
    fun testHandleEndIf() {
        val settings = Settings(httpClient = "APACHE")
        val state = DirectiveState()
        val parser = DirectiveParser(state, settings)
        parser.handle("###if httpClient==URL_CONNECTION")
        assertFalse("Should be inactive", state.isActive())
        parser.handle("###endif")
        assertTrue("Should be active after endif", state.isActive())
    }

    @Test
    fun testNonDirectiveReturnsFalse() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        assertFalse("Non-directive should return false", parser.handle("api.name=test"))
        assertFalse("Comment should return false", parser.handle("# comment"))
    }
}
