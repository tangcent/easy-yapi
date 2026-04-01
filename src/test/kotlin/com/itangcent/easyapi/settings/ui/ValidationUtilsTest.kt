package com.itangcent.easyapi.settings.ui

import org.junit.Assert.*
import org.junit.Test

class ValidationUtilsTest {
    
    @Test
    fun testValidateUrl() {
        assertTrue("Valid HTTP URL should pass", ValidationUtils.validateUrl("http://example.com"))
        assertTrue("Valid HTTPS URL should pass", ValidationUtils.validateUrl("https://example.com"))
        assertTrue("Empty URL should pass", ValidationUtils.validateUrl(""))
        assertTrue("Null URL should pass", ValidationUtils.validateUrl(null))
        assertFalse("FTP URL should fail", ValidationUtils.validateUrl("ftp://example.com"))
        assertFalse("Invalid URL should fail", ValidationUtils.validateUrl("not-a-url"))
        assertFalse("Malformed URL should fail", ValidationUtils.validateUrl("http://"))
    }
    
    @Test
    fun testValidateInteger() {
        assertTrue("Valid integer should pass", ValidationUtils.validateInteger("100"))
        assertTrue("Empty string should pass", ValidationUtils.validateInteger(""))
        assertTrue("Null should pass", ValidationUtils.validateInteger(null))
        assertTrue("Integer within range should pass", ValidationUtils.validateInteger("50", 0, 100))
        assertFalse("Integer below min should fail", ValidationUtils.validateInteger("-10", 0, 100))
        assertFalse("Integer above max should fail", ValidationUtils.validateInteger("150", 0, 100))
        assertFalse("Non-integer should fail", ValidationUtils.validateInteger("abc"))
        assertFalse("Float should fail", ValidationUtils.validateInteger("10.5"))
    }
    
    @Test
    fun testValidateToken() {
        assertTrue("Valid token should pass", ValidationUtils.validateToken("12345678"))
        assertTrue("Longer token should pass", ValidationUtils.validateToken("abcdefghijklmnop"))
        assertTrue("Empty token should pass", ValidationUtils.validateToken(""))
        assertTrue("Null token should pass", ValidationUtils.validateToken(null))
        assertFalse("Short token should fail", ValidationUtils.validateToken("1234567"))
        assertFalse("Very short token should fail", ValidationUtils.validateToken("abc"))
    }
}
