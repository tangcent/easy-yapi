package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.logging.IdeaLog
import java.util.concurrent.ConcurrentLinkedQueue
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Pool of reusable [ScriptEngine] instances.
 *
 * Creating a [ScriptEngine] is expensive (classloading, initialization).
 * This pool borrows and returns engines to avoid repeated creation overhead.
 *
 * ## Thread Safety
 * [ConcurrentLinkedQueue] ensures safe concurrent access.
 * The pool has a maximum size; excess engines are simply discarded.
 *
 * ## Usage
 * ```kotlin
 * val pool = EnginePool("groovy", maxPoolSize = 4)
 * val result = pool.withEngine { engine ->
 *     engine.eval("1 + 2")
 * }
 * ```
 *
 * @param engineName the JSR-223 engine name (e.g. "groovy")
 * @param maxPoolSize maximum number of idle engines to keep in the pool
 * @param engineFactory optional factory for creating engines (defaults to JSR-223 lookup)
 */
class EnginePool(
    val engineName: String,
    private val maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    private val engineFactory: (() -> ScriptEngine?)? = null
) : IdeaLog {

    private val pool = ConcurrentLinkedQueue<ScriptEngine>()

    /**
     * Borrows an engine from the pool (or creates a new one if the pool is empty),
     * executes [block], then returns the engine to the pool.
     *
     * If engine creation fails, logs a warning and returns null.
     *
     * @param block the operation to perform with the borrowed engine
     * @return the result of [block], or null if no engine could be created
     */
    fun <T> withEngine(block: (ScriptEngine) -> T): T? {
        val engine = borrowEngine() ?: run {
            LOG.warn("EnginePool: Failed to create script engine for $engineName")
            return null
        }
        return try {
            block(engine)
        } finally {
            returnEngine(engine)
        }
    }

    /**
     * Current number of idle engines in the pool.
     * Visible for testing.
     */
    fun poolSize(): Int = pool.size

    private fun borrowEngine(): ScriptEngine? {
        val cached = pool.poll()
        if (cached != null) return cached
        return engineFactory?.invoke()
            ?: runCatching { ScriptEngineManager().getEngineByName(engineName) }.getOrNull()
    }

    private fun returnEngine(engine: ScriptEngine) {
        if (pool.size < maxPoolSize) {
            pool.offer(engine)
        }
    }

    companion object : IdeaLog {
        const val DEFAULT_MAX_POOL_SIZE = 4
    }
}
