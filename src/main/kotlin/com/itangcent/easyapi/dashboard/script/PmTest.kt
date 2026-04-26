package com.itangcent.easyapi.dashboard.script

import groovy.lang.Closure

/**
 * Represents the outcome of a single test case executed in a post-response script.
 *
 * @property name The human-readable test name (e.g., "Status is 200")
 * @property passed Whether the test assertion passed
 * @property error An error message if the test failed, or a marker like "[SKIPPED]" for skipped tests
 */
data class TestResult(
    val name: String,
    val passed: Boolean,
    val error: String? = null
)

/**
 * Collects [TestResult] instances produced by [PmTest] during script execution.
 *
 * The collector maintains an ordered list of results and a test index counter.
 * After script execution completes, the collected results are displayed in the
 * test results panel of the dashboard.
 */
class PmTestCollector {

    private val _results = mutableListOf<TestResult>()

    /** An immutable snapshot of all test results collected so far. */
    val results: List<TestResult> get() = _results.toList()

    private var testIndex = 0

    /**
     * Records a test result.
     *
     * @param name The test name
     * @param passed Whether the test passed
     * @param error An optional error message for failed tests
     */
    fun addResult(name: String, passed: Boolean, error: String? = null) {
        _results.add(TestResult(name, passed, error))
    }

    /** Returns the current test index (number of tests that have been executed). */
    fun index(): Int = testIndex

    /** Increments the test index and returns the previous value. */
    fun incrementIndex(): Int = testIndex++
}

/**
 * Postman-compatible test function handler, accessible as `pm.test` in scripts.
 *
 * Each call to `pm.test("name") { }` executes the closure and records a pass/fail result
 * in the [PmTestCollector]. If the closure throws an [AssertionError] or any other exception,
 * the test is marked as failed with the exception message.
 *
 * Groovy usage:
 * ```groovy
 * pm.test("Status is 200") {
 *     assert pm.response.code == 200
 * }
 * pm.test("Body has name") {
 *     pm.expect(pm.response.json().name).to.eql("Alice")
 * }
 * pm.skip("Not ready") { /* will not run */ }
 * ```
 */
class PmTest(private val collector: PmTestCollector) {

    /**
     * Defines and immediately executes a named test case.
     *
     * @param name A human-readable test name
     * @param closure The test body; if it throws, the test is recorded as failed
     */
    operator fun invoke(name: String, closure: Runnable) {
        try {
            closure.run()
            collector.addResult(name, true)
        } catch (e: AssertionError) {
            collector.addResult(name, false, e.message)
        } catch (e: Exception) {
            collector.addResult(name, false, e.message)
        }
        collector.incrementIndex()
    }

    /**
     * Groovy-compatible overload accepting a [Closure].
     *
     * Groovy scripts pass `groovy.lang.Closure` objects directly.
     * This overload ensures `pm.test("name") { }` works correctly in Groovy.
     */
    operator fun invoke(name: String, closure: Closure<*>) {
        invoke(name, Runnable { closure.call() })
    }

    /**
     * Groovy-compatible alias for [invoke].
     *
     * Groovy's callable convention uses `call()` rather than Kotlin's `invoke()`,
     * so `pm.test("name") { }` in Groovy resolves to this method.
     */
    fun call(name: String, closure: Runnable) {
        invoke(name, closure)
    }

    /**
     * Groovy-compatible alias for [invoke] accepting a [Closure].
     */
    fun call(name: String, closure: Closure<*>) {
        invoke(name, closure)
    }

    /**
     * Marks a test as skipped. The closure is not executed.
     *
     * @param name The test name
     * @param closure Ignored; present for API compatibility with Postman
     */
    fun skip(name: String, @Suppress("UNUSED_PARAMETER") closure: Runnable) {
        collector.addResult(name, true, "[SKIPPED]")
        collector.incrementIndex()
    }

    /**
     * Groovy-compatible skip accepting a [Closure].
     */
    fun skip(name: String, @Suppress("UNUSED_PARAMETER") closure: Closure<*>) {
        skip(name, Runnable {})
    }
}
