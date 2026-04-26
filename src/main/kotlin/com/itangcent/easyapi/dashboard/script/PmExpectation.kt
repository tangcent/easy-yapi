package com.itangcent.easyapi.dashboard.script

/**
 * Chai-style assertion builder, compatible with Postman's `pm.expect()` API.
 *
 * Provides a fluent chainable API for writing assertions in scripts. Chain words
 * like `to`, `be`, `not`, `have`, `which`, `and`, etc. are no-ops that improve readability.
 * The [not] property toggles negation for the next assertion.
 *
 * Groovy usage:
 * ```groovy
 * pm.expect(200).to.equal(200)
 * pm.expect("hello").to.contain("ell")
 * pm.expect(42).to.be.above(10)
 * pm.expect("hello").not.to.equal("world")
 * pm.expect(null).to.be.null_
 * pm.expect([1, 2, 3]).to.have.lengthOf(3)
 * ```
 *
 * @property value The value being asserted against
 */
class PmExpectation(private val value: Any?) {

    internal var negated = false

    /** Toggles negation: `pm.expect(x).not.to.equal(y)` asserts x != y. */
    val not: PmExpectation
        get() = PmExpectation(value).also { it.negated = !this.negated }

    /** Chain word — no-op for readability. */ val to: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val be: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val been: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val is_: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val that: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val which: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val and: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val has: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val have: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val with: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val at: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val of: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val same: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val but: PmExpectation get() = this
    /** Chain word — no-op for readability. */ val does: PmExpectation get() = this

    /**
     * Asserts deep equality with the expected value.
     * When negated, asserts inequality.
     */
    fun eql(expected: Any?) {
        if (negated) {
            check(value != expected) { "Expected '$value' to not eql '$expected'" }
        } else {
            check(value == expected) { "Expected '$expected' but was '$value'" }
        }
    }

    /** Alias for [eql]. */
    fun equal(expected: Any?) = eql(expected)

    /** Asserts the value is greater than [n]. When negated, asserts <=. */
    fun above(n: Number) {
        val v = (value as? Number) ?: throw AssertionError("Expected a number but was ${value?.javaClass?.simpleName}")
        if (negated) {
            check(v.toDouble() <= n.toDouble()) { "Expected $v to not be above $n" }
        } else {
            check(v.toDouble() > n.toDouble()) { "Expected $v to be above $n" }
        }
    }

    /** Asserts the value is less than [n]. When negated, asserts >=. */
    fun below(n: Number) {
        val v = (value as? Number) ?: throw AssertionError("Expected a number but was ${value?.javaClass?.simpleName}")
        if (negated) {
            check(v.toDouble() >= n.toDouble()) { "Expected $v to not be below $n" }
        } else {
            check(v.toDouble() < n.toDouble()) { "Expected $v to be below $n" }
        }
    }

    /** Asserts the value is >= [n]. When negated, asserts <. */
    fun atLeast(n: Number) {
        val v = (value as? Number) ?: throw AssertionError("Expected a number but was ${value?.javaClass?.simpleName}")
        if (negated) {
            check(v.toDouble() < n.toDouble()) { "Expected $v to not be at least $n" }
        } else {
            check(v.toDouble() >= n.toDouble()) { "Expected $v to be at least $n" }
        }
    }

    /** Asserts the value is <= [n]. When negated, asserts >. */
    fun atMost(n: Number) {
        val v = (value as? Number) ?: throw AssertionError("Expected a number but was ${value?.javaClass?.simpleName}")
        if (negated) {
            check(v.toDouble() > n.toDouble()) { "Expected $v to not be at most $n" }
        } else {
            check(v.toDouble() <= n.toDouble()) { "Expected $v to be at most $n" }
        }
    }

    /** Asserts the value is within the inclusive range [a]..[b]. When negated, asserts outside. */
    fun within(a: Number, b: Number) {
        val v = (value as? Number) ?: throw AssertionError("Expected a number but was ${value?.javaClass?.simpleName}")
        if (negated) {
            check(v.toDouble() < a.toDouble() || v.toDouble() > b.toDouble()) { "Expected $v to not be within $a..$b" }
        } else {
            check(v.toDouble() >= a.toDouble() && v.toDouble() <= b.toDouble()) { "Expected $v to be within $a..$b" }
        }
    }

    /** Alias for [an]. */
    fun a(type: String) = an(type)

    /**
     * Asserts the value is of the given type name.
     *
     * Supported type names: "String", "Number", "Boolean", "Map", "List", "Closure", "null".
     * When negated, asserts the type does NOT match.
     */
    fun an(type: String) {
        val typeName = type.replaceFirstChar { it.uppercase() }
        val actualType = when (value) {
            null -> "Null"
            is String -> "String"
            is Int -> "Number"
            is Long -> "Number"
            is Double -> "Number"
            is Float -> "Number"
            is Boolean -> "Boolean"
            is Map<*, *> -> "Map"
            is List<*> -> "List"
            is Runnable -> "Closure"
            else -> value.javaClass.simpleName
        }
        if (negated) {
            check(actualType != typeName) { "Expected type to not be '$typeName' but was '$actualType'" }
        } else {
            check(actualType == typeName) { "Expected type '$typeName' but was '$actualType'" }
        }
    }

    /** Alias for [contain]. */
    fun include(expected: Any?) = contain(expected)

    /**
     * Asserts the value contains the expected element.
     * Works for Strings (substring), Lists (element), and other types (string contains).
     * When negated, asserts the value does NOT contain the expected element.
     */
    fun contain(expected: Any?) {
        if (negated) {
            when (value) {
                is String -> check(!value.contains(expected?.toString() ?: "")) { "Expected '$value' to not contain '$expected'" }
                is List<*> -> check(!value.contains(expected)) { "Expected list to not contain '$expected'" }
                else -> check(value?.toString()?.contains(expected?.toString() ?: "") != true) { "Expected to not contain '$expected'" }
            }
        } else {
            when (value) {
                is String -> check(value.contains(expected?.toString() ?: "")) { "Expected '$value' to contain '$expected'" }
                is List<*> -> check(value.contains(expected)) { "Expected list to contain '$expected'" }
                else -> check(value?.toString()?.contains(expected?.toString() ?: "") == true) { "Expected to contain '$expected'" }
            }
        }
    }

    /**
     * Asserts the value matches the given regular expression pattern.
     * When negated, asserts the value does NOT match.
     */
    fun match(pattern: Regex) {
        if (negated) {
            check(!pattern.containsMatchIn(value?.toString() ?: "")) { "Expected '${value}' to not match /$pattern/" }
        } else {
            check(pattern.containsMatchIn(value?.toString() ?: "")) { "Expected '${value}' to match /$pattern/" }
        }
    }

    /**
     * Asserts the value has the given length.
     * Works for String, List, Map, and Array.
     * When negated, asserts the length is NOT equal.
     */
    fun lengthOf(n: Int) {
        val len = when (value) {
            is String -> value.length
            is List<*> -> value.size
            is Map<*, *> -> value.size
            is Array<*> -> value.size
            else -> throw AssertionError("Cannot check length of ${value?.javaClass?.simpleName}")
        }
        if (negated) {
            check(len != n) { "Expected length to not be $n but was $len" }
        } else {
            check(len == n) { "Expected length $n but was $len" }
        }
    }

    /** Asserts the value is not null. When negated, asserts the value IS null. */
    val exist: Unit
        get() {
            if (negated) {
                check(value == null) { "Expected value to not exist but was '$value'" }
            } else {
                check(value != null) { "Expected value to exist but was null" }
            }
        }

    /** Asserts the value is truthy (not null, not false, not 0, not ""). When negated, asserts falsy. */
    val ok: Unit
        get() {
            val truthy = value != null && value != false && value != 0 && value != ""
            if (negated) {
                check(!truthy) { "Expected value to not be ok but was '$value'" }
            } else {
                check(truthy) { "Expected value to be ok (truthy) but was '$value'" }
            }
        }

    /** Asserts the value is exactly `true`. When negated, asserts NOT true. */
    val true_: Unit
        get() {
            if (negated) {
                check(value != true) { "Expected value to not be true" }
            } else {
                check(value == true) { "Expected true but was '$value'" }
            }
        }

    fun isTrue() { true_ }

    val false_: Unit
        get() {
            if (negated) {
                check(value != false) { "Expected value to not be false" }
            } else {
                check(value == false) { "Expected false but was '$value'" }
            }
        }

    fun isFalse() { false_ }

    val null_: Unit
        get() {
            if (negated) {
                check(value != null) { "Expected value to not be null" }
            } else {
                check(value == null) { "Expected null but was '$value'" }
            }
        }

    fun isNull() { null_ }

    /** Asserts the value is empty (null, empty string/list/map/array). When negated, asserts NOT empty. */
    val empty: Unit
        get() {
            val isEmpty = when (value) {
                null -> true
                is String -> value.isEmpty()
                is List<*> -> value.isEmpty()
                is Map<*, *> -> value.isEmpty()
                is Array<*> -> value.isEmpty()
                else -> value.toString().isEmpty()
            }
            if (negated) {
                check(!isEmpty) { "Expected value to not be empty" }
            } else {
                check(isEmpty) { "Expected value to be empty but was '$value'" }
            }
        }

    /** Asserts the value is one of the elements in the given list. When negated, asserts NOT in the list. */
    fun oneOf(list: List<Any?>) {
        if (negated) {
            check(value !in list) { "Expected '$value' to not be one of $list" }
        } else {
            check(value in list) { "Expected '$value' to be one of $list" }
        }
    }
}
