package com.itangcent.easyapi.dashboard.script

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
}
