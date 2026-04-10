package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test
import javax.script.ScriptEngineManager

class EnginePoolTest {

    private fun hasGroovyEngine(): Boolean {
        return ScriptEngineManager().getEngineByName("groovy") != null
    }

    @Test
    fun `engineName is accessible`() {
        val pool = EnginePool("groovy")
        assertEquals("groovy", pool.engineName)
    }

    @Test
    fun `poolSize starts at zero`() {
        val pool = EnginePool("groovy")
        assertEquals(0, pool.poolSize())
    }

    @Test
    fun `withEngine returns null for unavailable engine`() {
        val pool = EnginePool("NonExistentEngine")
        val result = pool.withEngine { engine ->
            engine.eval("1 + 1")
        }
        assertNull(result)
    }

    @Test
    fun `withEngine returns evaluation result when groovy available`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy")
        val result = pool.withEngine { engine -> engine.eval("1 + 2") }
        assertEquals(3, result)
    }

    @Test
    fun `withEngine returns engine to pool after use`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy", maxPoolSize = 4)
        assertEquals(0, pool.poolSize())
        pool.withEngine { engine -> engine.eval("1 + 1") }
        assertEquals(1, pool.poolSize())
    }

    @Test
    fun `withEngine reuses pooled engines`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy", maxPoolSize = 4)
        pool.withEngine { engine -> engine.eval("10 + 20") }
        assertEquals(1, pool.poolSize())
        pool.withEngine { engine -> engine.eval("30 + 40") }
        assertEquals(1, pool.poolSize())
    }

    @Test
    fun `withEngine does not exceed max pool size`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy", maxPoolSize = 2)
        for (i in 0 until 5) {
            pool.withEngine { engine -> engine.eval("1") }
        }
        assertTrue(pool.poolSize() <= 2)
    }

    @Test
    fun `withEngine returns engine to pool even on exception`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy", maxPoolSize = 4)
        try {
            pool.withEngine { throw RuntimeException("test error") }
        } catch (_: Exception) {}
        assertEquals(1, pool.poolSize())
    }

    @Test
    fun `multiple sequential withEngine calls maintain pool size`() {
        if (!hasGroovyEngine()) return
        val pool = EnginePool("groovy", maxPoolSize = 4)
        for (i in 1..10) {
            pool.withEngine { engine -> engine.eval("$i + 1") }
        }
        assertTrue(pool.poolSize() <= 4)
        assertTrue(pool.poolSize() > 0)
    }
}
