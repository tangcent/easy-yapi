package com.itangcent.easyapi.channel.hoppscotch

import org.junit.Assert.*
import org.junit.Test

class HoppscotchMagicLinkLoginDialogTest {

    // --- Email validation tests ---

    @Test
    fun `isValidEmail accepts standard email`() {
        assertTrue(HoppscotchMagicLinkLoginDialog.isValidEmail("user@example.com"))
    }

    @Test
    fun `isValidEmail accepts email with subdomain`() {
        assertTrue(HoppscotchMagicLinkLoginDialog.isValidEmail("user@mail.example.com"))
    }

    @Test
    fun `isValidEmail accepts email with plus sign`() {
        assertTrue(HoppscotchMagicLinkLoginDialog.isValidEmail("user+tag@example.com"))
    }

    @Test
    fun `isValidEmail accepts email with dots in local part`() {
        assertTrue(HoppscotchMagicLinkLoginDialog.isValidEmail("first.last@example.com"))
    }

    @Test
    fun `isValidEmail rejects empty string`() {
        assertFalse(HoppscotchMagicLinkLoginDialog.isValidEmail(""))
    }

    @Test
    fun `isValidEmail rejects string without at sign`() {
        assertFalse(HoppscotchMagicLinkLoginDialog.isValidEmail("userexample.com"))
    }

    @Test
    fun `isValidEmail rejects string without domain`() {
        assertFalse(HoppscotchMagicLinkLoginDialog.isValidEmail("user@"))
    }

    @Test
    fun `isValidEmail rejects string without TLD`() {
        assertFalse(HoppscotchMagicLinkLoginDialog.isValidEmail("user@example"))
    }

    @Test
    fun `isValidEmail rejects string with spaces`() {
        assertFalse(HoppscotchMagicLinkLoginDialog.isValidEmail("user @example.com"))
    }

    // --- Token extraction from URL tests ---

    @Test
    fun `extractTokenFromUrl extracts token from standard magic link URL`() {
        val url = "https://hoppscotch.io/enter?token=abc123def456"
        assertEquals("abc123def456", HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl extracts token from self-hosted URL`() {
        val url = "https://custom.example.com/enter?token=xyz789"
        assertEquals("xyz789", HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl extracts token from URL with multiple query params`() {
        val url = "https://hoppscotch.io/enter?foo=bar&token=abc123&baz=qux"
        assertEquals("abc123", HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl returns null for URL without token param`() {
        val url = "https://hoppscotch.io/enter?foo=bar"
        assertNull(HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl returns null for URL without query string`() {
        val url = "https://hoppscotch.io/enter"
        assertNull(HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl returns null for empty string`() {
        assertNull(HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(""))
    }

    @Test
    fun `extractTokenFromUrl falls back to regex for partial URL strings`() {
        // The regex should find token= even in a partial URL string
        val url = "enter?token=abc123"
        assertEquals("abc123", HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl returns null for string without query marker`() {
        // No ? or & before token=, so it shouldn't match
        val url = "not a url but token=abc123 is here"
        assertNull(HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl returns null for URL with empty token value`() {
        val url = "https://hoppscotch.io/enter?token="
        assertNull(HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    @Test
    fun `extractTokenFromUrl handles token with special characters`() {
        val url = "https://hoppscotch.io/enter?token=abc-123_xyz.ABC"
        assertEquals("abc-123_xyz.ABC", HoppscotchMagicLinkLoginDialog.extractTokenFromUrl(url))
    }

    // --- Magic link API URL construction tests ---

    @Test
    fun `cloud instance constructs correct signin URL`() {
        val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl("https://hoppscotch.io")
        val signinUrl = "$apiBaseUrl/auth/signin?origin=app"
        assertEquals("https://api.hoppscotch.io/v1/auth/signin?origin=app", signinUrl)
    }

    @Test
    fun `cloud instance constructs correct verify URL`() {
        val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl("https://hoppscotch.io")
        val verifyUrl = "$apiBaseUrl/auth/verify"
        assertEquals("https://api.hoppscotch.io/v1/auth/verify", verifyUrl)
    }

    @Test
    fun `self-hosted instance constructs correct signin URL`() {
        val customUrl = "https://custom.hoppscotch.example"
        val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(customUrl)
        val signinUrl = "$apiBaseUrl/auth/signin?origin=app"
        assertEquals("https://custom.hoppscotch.example/v1/auth/signin?origin=app", signinUrl)
    }

    @Test
    fun `self-hosted with backend URL constructs correct signin URL`() {
        val customUrl = "https://custom.hoppscotch.example"
        val backendUrl = "http://localhost:3170/v1"
        val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(customUrl, backendUrl)
        val signinUrl = "$apiBaseUrl/auth/signin?origin=app"
        assertEquals("http://localhost:3170/v1/auth/signin?origin=app", signinUrl)
    }

    // --- MagicLinkSendResult tests ---

    @Test
    fun `MagicLinkSendResult success case`() {
        val result = MagicLinkSendResult(success = true, deviceIdentifier = "abc123")
        assertTrue(result.success)
        assertEquals("abc123", result.deviceIdentifier)
        assertNull(result.errorMessage)
    }

    @Test
    fun `MagicLinkSendResult failure case`() {
        val result = MagicLinkSendResult(success = false, errorMessage = "Network error")
        assertFalse(result.success)
        assertNull(result.deviceIdentifier)
        assertEquals("Network error", result.errorMessage)
    }

    // --- MagicLinkVerifyResult tests ---

    @Test
    fun `MagicLinkVerifyResult success case`() {
        val result = MagicLinkVerifyResult(
            success = true,
            accessToken = "access123",
            refreshToken = "refresh456"
        )
        assertTrue(result.success)
        assertEquals("access123", result.accessToken)
        assertEquals("refresh456", result.refreshToken)
        assertNull(result.errorMessage)
    }

    @Test
    fun `MagicLinkVerifyResult failure with expired link`() {
        val result = MagicLinkVerifyResult(
            success = false,
            errorMessage = "This magic link has expired. Please request a new one."
        )
        assertFalse(result.success)
        assertNull(result.accessToken)
        assertNull(result.refreshToken)
        assertEquals("This magic link has expired. Please request a new one.", result.errorMessage)
    }

    // --- HoppscotchLoginMethodDialog.Method tests ---

    @Test
    fun `Method enum has all three values`() {
        val methods = HoppscotchLoginMethodDialog.Method.values()
        assertEquals(3, methods.size)
        assertTrue(methods.contains(HoppscotchLoginMethodDialog.Method.MAGIC_LINK))
        assertTrue(methods.contains(HoppscotchLoginMethodDialog.Method.BROWSER))
        assertTrue(methods.contains(HoppscotchLoginMethodDialog.Method.MANUAL_TOKEN))
    }
}
