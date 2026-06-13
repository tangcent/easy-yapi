package com.itangcent.easyapi.script.pm

import org.junit.Test

class PmExpectationTest {

    @Test
    fun testEqlPasses() {
        PmExpectation(200).to.equal(200)
        PmExpectation("hello").to.eql("hello")
        PmExpectation(null).to.eql(null)
    }

    @Test(expected = IllegalStateException::class)
    fun testEqlFails() {
        PmExpectation(200).to.equal(404)
    }

    @Test
    fun testNotEqlPasses() {
        PmExpectation(200).not.to.equal(404)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotEqlFails() {
        PmExpectation(200).not.to.equal(200)
    }

    @Test
    fun testAbovePasses() {
        PmExpectation(10).to.be.above(5)
        PmExpectation(10L).to.be.above(5)
        PmExpectation(10.5).to.be.above(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testAboveFails() {
        PmExpectation(5).to.be.above(10)
    }

    @Test(expected = AssertionError::class)
    fun testAboveNonNumber() {
        PmExpectation("string").to.be.above(5)
    }

    @Test
    fun testBelowPasses() {
        PmExpectation(5).to.be.below(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testBelowFails() {
        PmExpectation(10).to.be.below(5)
    }

    @Test
    fun testAtLeastPasses() {
        PmExpectation(10).to.be.atLeast(10)
        PmExpectation(15).to.be.atLeast(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testAtLeastFails() {
        PmExpectation(5).to.be.atLeast(10)
    }

    @Test
    fun testAtMostPasses() {
        PmExpectation(10).to.be.atMost(10)
        PmExpectation(5).to.be.atMost(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testAtMostFails() {
        PmExpectation(15).to.be.atMost(10)
    }

    @Test
    fun testWithinPasses() {
        PmExpectation(5).to.be.within(1, 10)
        PmExpectation(1).to.be.within(1, 10)
        PmExpectation(10).to.be.within(1, 10)
    }

    @Test(expected = IllegalStateException::class)
    fun testWithinFails() {
        PmExpectation(15).to.be.within(1, 10)
    }

    @Test
    fun testNotWithinPasses() {
        PmExpectation(15).not.to.be.within(1, 10)
    }

    @Test
    fun testAnTypePasses() {
        PmExpectation("hello").to.be.an("string")
        PmExpectation(42).to.be.an("number")
        PmExpectation(true).to.be.an("boolean")
        PmExpectation(mapOf("a" to 1)).to.be.an("map")
        PmExpectation(listOf(1, 2)).to.be.an("list")
    }

    @Test
    fun testAnTypeNull() {
        PmExpectation(null).to.be.an("null")
    }

    @Test(expected = IllegalStateException::class)
    fun testAnTypeFails() {
        PmExpectation(42).to.be.an("string")
    }

    @Test
    fun testNotAnTypePasses() {
        PmExpectation(42).not.to.be.an("string")
    }

    @Test
    fun testATypeAlias() {
        PmExpectation("hello").to.be.a("string")
    }

    @Test
    fun testContainStringPasses() {
        PmExpectation("hello world").to.contain("world")
    }

    @Test(expected = IllegalStateException::class)
    fun testContainStringFails() {
        PmExpectation("hello").to.contain("world")
    }

    @Test
    fun testContainListPasses() {
        PmExpectation(listOf(1, 2, 3)).to.contain(2)
    }

    @Test(expected = IllegalStateException::class)
    fun testContainListFails() {
        PmExpectation(listOf(1, 2, 3)).to.contain(5)
    }

    @Test
    fun testNotContainPasses() {
        PmExpectation("hello").not.to.contain("world")
    }

    @Test
    fun testIncludeAlias() {
        PmExpectation("hello").to.include("ell")
    }

    @Test
    fun testMatchPasses() {
        PmExpectation("hello123").to.match(Regex("hello\\d+"))
    }

    @Test(expected = IllegalStateException::class)
    fun testMatchFails() {
        PmExpectation("hello").to.match(Regex("\\d+"))
    }

    @Test
    fun testNotMatchPasses() {
        PmExpectation("hello").not.to.match(Regex("\\d+"))
    }

    @Test
    fun testLengthOfString() {
        PmExpectation("hello").to.have.lengthOf(5)
    }

    @Test(expected = IllegalStateException::class)
    fun testLengthOfFails() {
        PmExpectation("hello").to.have.lengthOf(3)
    }

    @Test
    fun testLengthOfList() {
        PmExpectation(listOf(1, 2, 3)).to.have.lengthOf(3)
    }

    @Test
    fun testLengthOfMap() {
        PmExpectation(mapOf("a" to 1, "b" to 2)).to.have.lengthOf(2)
    }

    @Test
    fun testNotLengthOf() {
        PmExpectation("hello").not.to.have.lengthOf(10)
    }

    @Test
    fun testExistPasses() {
        PmExpectation("value").to.exist
    }

    @Test(expected = IllegalStateException::class)
    fun testExistFails() {
        PmExpectation(null).to.exist
    }

    @Test
    fun testNotExistPasses() {
        PmExpectation(null).not.to.exist
    }

    @Test
    fun testOkTruthy() {
        PmExpectation("value").to.be.ok
        PmExpectation(1).to.be.ok
        PmExpectation(true).to.be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testOkFalsy() {
        PmExpectation(null).to.be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testOkFalsyEmptyString() {
        PmExpectation("").to.be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testOkFalsyZero() {
        PmExpectation(0).to.be.ok
    }

    @Test
    fun testTrue_() {
        PmExpectation(true).to.be.true_
    }

    @Test(expected = IllegalStateException::class)
    fun testTrue_Fails() {
        PmExpectation(false).to.be.true_
    }

    @Test
    fun testFalse_() {
        PmExpectation(false).to.be.false_
    }

    @Test(expected = IllegalStateException::class)
    fun testFalse_Fails() {
        PmExpectation(true).to.be.false_
    }

    @Test
    fun testNull_() {
        PmExpectation(null).to.be.null_
    }

    @Test(expected = IllegalStateException::class)
    fun testNull_Fails() {
        PmExpectation("value").to.be.null_
    }

    @Test
    fun testNotNull_() {
        PmExpectation("value").not.to.be.null_
    }

    @Test
    fun testIsTrue() {
        PmExpectation(true).to.be.isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun testIsTrueFails() {
        PmExpectation(false).to.be.isTrue()
    }

    @Test
    fun testIsFalse() {
        PmExpectation(false).to.be.isFalse()
    }

    @Test(expected = IllegalStateException::class)
    fun testIsFalseFails() {
        PmExpectation(true).to.be.isFalse()
    }

    @Test
    fun testIsNull() {
        PmExpectation(null).to.be.isNull()
    }

    @Test(expected = IllegalStateException::class)
    fun testIsNullFails() {
        PmExpectation("value").to.be.isNull()
    }

    @Test
    fun testIsNotNull() {
        PmExpectation("value").not.to.be.isNull()
    }

    @Test
    fun testNotIsTrue() {
        PmExpectation(false).not.to.be.isTrue()
    }

    @Test
    fun testNotIsFalse() {
        PmExpectation(true).not.to.be.isFalse()
    }

    @Test
    fun testNotIsNull() {
        PmExpectation("value").not.to.be.isNull()
    }

    @Test
    fun testEmptyString() {
        PmExpectation("").to.be.empty
    }

    @Test
    fun testEmptyList() {
        PmExpectation(emptyList<Any>()).to.be.empty
    }

    @Test
    fun testEmptyMap() {
        PmExpectation(emptyMap<String, Any>()).to.be.empty
    }

    @Test
    fun testEmptyNull() {
        PmExpectation(null).to.be.empty
    }

    @Test(expected = IllegalStateException::class)
    fun testEmptyFails() {
        PmExpectation("hello").to.be.empty
    }

    @Test
    fun testNotEmpty() {
        PmExpectation("hello").not.to.be.empty
    }

    @Test
    fun testOneOfPasses() {
        PmExpectation("apple").to.be.oneOf(listOf("apple", "banana", "cherry"))
    }

    @Test(expected = IllegalStateException::class)
    fun testOneOfFails() {
        PmExpectation("grape").to.be.oneOf(listOf("apple", "banana", "cherry"))
    }

    @Test
    fun testNotOneOfPasses() {
        PmExpectation("grape").not.to.be.oneOf(listOf("apple", "banana", "cherry"))
    }

    @Test
    fun testChainWordsAreNoOps() {
        PmExpectation(42).to.be.been.is_.that.which.and.has.have.with.at.of.same.but.does.equal(42)
    }

    // --- Negated number assertions ---

    @Test
    fun testNotAbovePasses() {
        PmExpectation(5).not.to.be.above(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotAboveFails() {
        PmExpectation(10).not.to.be.above(5)
    }

    @Test
    fun testNotBelowPasses() {
        PmExpectation(10).not.to.be.below(5)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotBelowFails() {
        PmExpectation(5).not.to.be.below(10)
    }

    @Test
    fun testNotAtLeastPasses() {
        PmExpectation(5).not.to.be.atLeast(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotAtLeastFails() {
        PmExpectation(10).not.to.be.atLeast(5)
    }

    @Test
    fun testNotAtMostPasses() {
        PmExpectation(15).not.to.be.atMost(10)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotAtMostFails() {
        PmExpectation(5).not.to.be.atMost(10)
    }

    // --- Negated type assertions ---

    @Test(expected = IllegalStateException::class)
    fun testNotAnTypeFails() {
        PmExpectation("hello").not.to.be.an("string")
    }

    // --- Negated contain for list ---

    @Test(expected = IllegalStateException::class)
    fun testNotContainListFails() {
        PmExpectation(listOf(1, 2, 3)).not.to.contain(2)
    }

    // --- Negated match ---

    @Test(expected = IllegalStateException::class)
    fun testNotMatchFails() {
        PmExpectation("hello123").not.to.match(Regex("\\d+"))
    }

    // --- Negated lengthOf ---

    @Test(expected = IllegalStateException::class)
    fun testNotLengthOfFails() {
        PmExpectation("hello").not.to.have.lengthOf(5)
    }

    // --- Negated ok/true/false/empty/oneOf ---

    @Test(expected = IllegalStateException::class)
    fun testNotOkFails() {
        PmExpectation("value").not.to.be.ok
    }

    @Test
    fun testNotOkFalsy() {
        PmExpectation(null).not.to.be.ok
        PmExpectation(0).not.to.be.ok
        PmExpectation("").not.to.be.ok
        PmExpectation(false).not.to.be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testNotTrueFails() {
        PmExpectation(true).not.to.be.true_
    }

    @Test
    fun testNotTruePasses() {
        PmExpectation(false).not.to.be.true_
    }

    @Test(expected = IllegalStateException::class)
    fun testNotFalseFails() {
        PmExpectation(false).not.to.be.false_
    }

    @Test
    fun testNotFalsePasses() {
        PmExpectation(true).not.to.be.false_
    }

    @Test(expected = IllegalStateException::class)
    fun testNotEmptyFails() {
        PmExpectation("").not.to.be.empty
    }

    @Test
    fun testNotEmptyPasses() {
        PmExpectation("hello").not.to.be.empty
        PmExpectation(listOf(1)).not.to.be.empty
    }

    @Test(expected = IllegalStateException::class)
    fun testNotOneOfFails() {
        PmExpectation("apple").not.to.be.oneOf(listOf("apple", "banana"))
    }

    // --- LengthOf for Array ---

    @Test
    fun testLengthOfArray() {
        PmExpectation(arrayOf(1, 2, 3)).to.have.lengthOf(3)
    }

    @Test(expected = AssertionError::class)
    fun testLengthOfUnsupportedType() {
        PmExpectation(42).to.have.lengthOf(1)
    }

    // --- Contain for non-string/non-list ---

    @Test
    fun testContainForObject() {
        PmExpectation(mapOf("key" to "hello world")).to.contain("hello")
    }

    @Test(expected = IllegalStateException::class)
    fun testContainForObjectFails() {
        PmExpectation(mapOf("key" to "hello")).to.contain("xyz")
    }

    @Test
    fun testNotContainForObject() {
        PmExpectation(mapOf("key" to "hello")).not.to.contain("xyz")
    }

    // --- Negated exist ---

    @Test(expected = IllegalStateException::class)
    fun testNotExistFails() {
        PmExpectation("value").not.to.exist
    }

    // --- Negated null ---

    @Test(expected = IllegalStateException::class)
    fun testNotNullFails() {
        PmExpectation(null).not.to.be.null_
    }

    // --- Empty for Array ---

    @Test
    fun testEmptyArray() {
        PmExpectation(emptyArray<Any>()).to.be.empty
    }

    // --- an() type for Runnable (Closure) ---

    @Test
    fun testAnTypeRunnable() {
        PmExpectation(Runnable {}).to.be.an("Closure")
    }

    // --- a() alias ---

    @Test
    fun testATypeNumber() {
        PmExpectation(3.14).to.be.a("number")
    }

    // --- Float type as Number ---

    @Test
    fun testAnTypeFloat() {
        PmExpectation(3.14f).to.be.an("number")
    }

    // --- Long type as Number ---

    @Test
    fun testAnTypeLong() {
        PmExpectation(42L).to.be.an("number")
    }

    // --- Boolean type ---

    @Test
    fun testAnTypeBoolean() {
        PmExpectation(false).to.be.an("boolean")
    }

    // --- Unknown type falls back to class simpleName ---

    @Test
    fun testAnTypeUnknownObject() {
        val obj = object : Any() {}
        val expectation = PmExpectation(obj)
        // Should not crash — the type name will be the anonymous class simpleName
        try {
            expectation.to.be.an("UnknownType")
        } catch (_: IllegalStateException) {
            // expected for mismatched type
        }
    }
}
