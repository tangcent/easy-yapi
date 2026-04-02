package com.itangcent.easyapi.exporter.yapi.model

import org.junit.Assert.*
import org.junit.Test

class TokenValidationResultTest {

    @Test
    fun `test Valid result has correct projectId`() {
        val valid = TokenValidationResult.Valid("project-123")
        assertEquals("project-123", valid.projectId)
    }

    @Test
    fun `test Valid isValid returns true`() {
        val valid = TokenValidationResult.Valid("project-456")
        assertTrue("Valid result should have isValid=true", valid.isValid)
    }

    @Test
    fun `test Valid is instance of TokenValidationResult`() {
        val valid: TokenValidationResult = TokenValidationResult.Valid("project-789")
        assertTrue(valid is TokenValidationResult.Valid)
        assertTrue(valid.isValid)
    }

    @Test
    fun `test Failed result has correct reason`() {
        val failed = TokenValidationResult.Failed("Token is expired")
        assertEquals("Token is expired", failed.reason)
    }

    @Test
    fun `test Failed isValid returns false`() {
        val failed = TokenValidationResult.Failed("Some error")
        assertFalse("Failed result should have isValid=false", failed.isValid)
    }

    @Test
    fun `test Failed is instance of TokenValidationResult`() {
        val failed: TokenValidationResult = TokenValidationResult.Failed("Server error")
        assertTrue(failed is TokenValidationResult.Failed)
        assertFalse(failed.isValid)
    }

    @Test
    fun `test Valid with empty projectId`() {
        val valid = TokenValidationResult.Valid("")
        assertEquals("", valid.projectId)
        assertTrue(valid.isValid)
    }

    @Test
    fun `test Failed with empty reason`() {
        val failed = TokenValidationResult.Failed("")
        assertEquals("", failed.reason)
        assertFalse(failed.isValid)
    }

    @Test
    fun `test Failed with detailed error message containing URL`() {
        val reason = "Token may be invalid: unauthorized (errcode: 40011). URL: http://localhost:3000/api/project/get?token=abc"
        val failed = TokenValidationResult.Failed(reason)
        assertTrue(failed.reason.contains("errcode"))
        assertTrue(failed.reason.contains("URL:"))
        assertFalse(failed.isValid)
    }

    @Test
    fun `test when clause distinguishes Valid from Failed`() {
        val results: List<TokenValidationResult> = listOf(
            TokenValidationResult.Valid("p1"),
            TokenValidationResult.Failed("error1"),
            TokenValidationResult.Valid("p2"),
            TokenValidationResult.Failed("error2")
        )

        val validIds = results.filterIsInstance<TokenValidationResult.Valid>().map { it.projectId }
        val failedReasons = results.filterIsInstance<TokenValidationResult.Failed>().map { it.reason }

        assertEquals(listOf("p1", "p2"), validIds)
        assertEquals(listOf("error1", "error2"), failedReasons)
    }

    @Test
    fun `test Valid equality`() {
        val a = TokenValidationResult.Valid("same-project")
        val b = TokenValidationResult.Valid("same-project")
        assertEquals(a, b)
    }

    @Test
    fun `test Valid inequality`() {
        val a = TokenValidationResult.Valid("project-a")
        val b = TokenValidationResult.Valid("project-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `test Failed equality`() {
        val a = TokenValidationResult.Failed("same error")
        val b = TokenValidationResult.Failed("same error")
        assertEquals(a, b)
    }

    @Test
    fun `test Failed inequality`() {
        val a = TokenValidationResult.Failed("error-a")
        val b = TokenValidationResult.Failed("error-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `test Valid and Failed are never equal`() {
        val valid = TokenValidationResult.Valid("p")
        val failed = TokenValidationResult.Failed("r")
        assertNotEquals(valid, failed)
    }
}
