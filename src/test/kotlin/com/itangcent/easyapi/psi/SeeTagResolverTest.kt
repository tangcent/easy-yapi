package com.itangcent.easyapi.psi

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [LinkResolver.parseLinkReference] and [LinkResolver.getterToPropertyName].
 *
 * Each test corresponds to one row in the supported-patterns table in the class KDoc.
 */
class SeeTagResolverTest {

    private fun assertParsed(input: String, expectedClass: String, expectedProp: String?) {
        val r = LinkResolver.parseLinkReference(input)
        assertNotNull("parseLinkReference(\"$input\") should not return null", r)
        assertEquals("className for \"$input\"", expectedClass, r!!.className)
        assertEquals("memberName for \"$input\"", expectedProp, r.memberName)
    }

    // ================================================================
    //  Plain reference — class only
    // ================================================================

    @Test fun `Xxx`()                   = assertParsed("Xxx", "Xxx", null)
    @Test fun `xxx_xxx_Xxx`()           = assertParsed("xxx.xxx.Xxx", "xxx.xxx.Xxx", null)
    @Test fun `deep fqcn`()             = assertParsed(
        "com.itangcent.springboot.demo.common.constant.UserType",
        "com.itangcent.springboot.demo.common.constant.UserType", null)

    // ================================================================
    //  Plain reference — class + field (hash)
    // ================================================================

    @Test fun `Xxx#field`()             = assertParsed("Xxx#field", "Xxx", "field")
    @Test fun `xxx_xxx_Xxx#field`()     = assertParsed("xxx.xxx.Xxx#field", "xxx.xxx.Xxx", "field")
    @Test fun `deep fqcn#field`()       = assertParsed("com.example.UserType#type", "com.example.UserType", "type")

    // ================================================================
    //  Plain reference — class + field (dot, lowercase)
    // ================================================================

    @Test fun `Xxx_field`()             = assertParsed("Xxx.field", "Xxx", "field")
    @Test fun `xxx_xxx_Xxx_field`()     = assertParsed("xxx.xxx.Xxx.field", "xxx.xxx.Xxx", "field")
    @Test fun `deep fqcn_field`()       = assertParsed("com.example.UserType.type", "com.example.UserType", "type")

    // Uppercase last segment → class, not property
    @Test fun `com_example_UserType`()  = assertParsed("com.example.UserType", "com.example.UserType", null)

    // ================================================================
    //  Plain reference — class + getter method
    // ================================================================

    @Test fun `Xxx#getField()`()        = assertParsed("Xxx#getField()", "Xxx", "getField")
    @Test fun `fqcn#getField()`()       = assertParsed("xxx.xxx.Xxx#getField()", "xxx.xxx.Xxx", "getField")
    @Test fun `Xxx#getType`()           = assertParsed("Xxx#getType", "Xxx", "getType")
    @Test fun `Xxx#isActive()`()        = assertParsed("Xxx#isActive()", "Xxx", "isActive")

    // ================================================================
    //  {@link ...} — class only
    // ================================================================

    @Test fun `link Xxx`()              = assertParsed("{@link Xxx}", "Xxx", null)
    @Test fun `link xxx_xxx_Xxx`()      = assertParsed("{@link xxx.xxx.Xxx}", "xxx.xxx.Xxx", null)
    @Test fun `link deep fqcn`()        = assertParsed(
        "{@link com.itangcent.springboot.demo.common.constant.UserType}",
        "com.itangcent.springboot.demo.common.constant.UserType", null)

    // ================================================================
    //  {@link ...} — class + field (hash)
    // ================================================================

    @Test fun `link Xxx#field`()        = assertParsed("{@link Xxx#field}", "Xxx", "field")
    @Test fun `link fqcn#field`()       = assertParsed("{@link xxx.xxx.Xxx#field}", "xxx.xxx.Xxx", "field")
    @Test fun `link deep fqcn#field`()  = assertParsed("{@link com.example.UserType#type}", "com.example.UserType", "type")

    // ================================================================
    //  {@link ...} — class + field (dot)
    // ================================================================

    @Test fun `link Xxx_field`()        = assertParsed("{@link Xxx.field}", "Xxx", "field")

    // ================================================================
    //  {@link ...} — class + getter method
    // ================================================================

    @Test fun `link Xxx#getField()`()   = assertParsed("{@link Xxx#getField()}", "Xxx", "getField")
    @Test fun `link fqcn#getField()`()  = assertParsed("{@link xxx.xxx.Xxx#getField()}", "xxx.xxx.Xxx", "getField")
    @Test fun `link Xxx#getType()`()    = assertParsed("{@link UserType#getType()}", "UserType", "getType")
    @Test fun `link fqcn#getType()`()   = assertParsed("{@link com.example.UserType#getType()}", "com.example.UserType", "getType")
    @Test fun `link Xxx#isActive()`()   = assertParsed("{@link Xxx#isActive()}", "Xxx", "isActive")

    // ================================================================
    //  [...] (Kotlin KDoc) — class only
    // ================================================================

    @Test fun `kdoc Xxx`()              = assertParsed("[Xxx]", "Xxx", null)
    @Test fun `kdoc xxx_xxx_Xxx`()      = assertParsed("[xxx.xxx.Xxx]", "xxx.xxx.Xxx", null)
    @Test fun `kdoc deep fqcn`()        = assertParsed(
        "[com.itangcent.springboot.demo.common.constant.UserType]",
        "com.itangcent.springboot.demo.common.constant.UserType", null)

    // ================================================================
    //  [...] (Kotlin KDoc) — class + field (dot)
    // ================================================================

    @Test fun `kdoc Xxx_field`()        = assertParsed("[Xxx.field]", "Xxx", "field")
    @Test fun `kdoc fqcn_field`()       = assertParsed("[xxx.xxx.Xxx.field]", "xxx.xxx.Xxx", "field")
    @Test fun `kdoc deep fqcn_field`()  = assertParsed("[com.example.UserType.type]", "com.example.UserType", "type")

    // ================================================================
    //  Whitespace tolerance
    // ================================================================

    @Test fun `plain with whitespace`()  = assertParsed("  Xxx  ", "Xxx", null)
    @Test fun `link with whitespace`()   = assertParsed("{@link  com.example.UserType  }", "com.example.UserType", null)
    @Test fun `kdoc with whitespace`()   = assertParsed("[ Xxx ]", "Xxx", null)
    @Test fun `link without closing`()   = assertParsed("{@link Xxx", "Xxx", null)

    // ================================================================
    //  getterToPropertyName
    // ================================================================

    @Test fun `getType to type`()        = assertEquals("type", LinkResolver.getterToPropertyName("getType"))
    @Test fun `getValue to value`()      = assertEquals("value", LinkResolver.getterToPropertyName("getValue"))
    @Test fun `isActive to active`()     = assertEquals("active", LinkResolver.getterToPropertyName("isActive"))
    @Test fun `isEnabled to enabled`()   = assertEquals("enabled", LinkResolver.getterToPropertyName("isEnabled"))
    @Test fun `plain field returns null`()= assertNull(LinkResolver.getterToPropertyName("type"))
    @Test fun `get alone returns null`() = assertNull(LinkResolver.getterToPropertyName("get"))
    @Test fun `is alone returns null`()  = assertNull(LinkResolver.getterToPropertyName("is"))
    @Test fun `getlowercase returns null`()= assertNull(LinkResolver.getterToPropertyName("gettype"))
    @Test fun `islowercase returns null`()= assertNull(LinkResolver.getterToPropertyName("isactive"))

    // ================================================================
    //  Null / blank / invalid → null
    // ================================================================

    @Test fun `blank returns null`() {
        assertNull(LinkResolver.parseLinkReference(""))
        assertNull(LinkResolver.parseLinkReference("   "))
    }

    @Test fun `empty link returns null`() {
        assertNull(LinkResolver.parseLinkReference("{@link }"))
        assertNull(LinkResolver.parseLinkReference("{@link}"))
    }

    @Test fun `empty kdoc returns null`() {
        assertNull(LinkResolver.parseLinkReference("[]"))
        assertNull(LinkResolver.parseLinkReference("[ ]"))
    }

    @Test fun `hash only returns null`() {
        assertNull(LinkResolver.parseLinkReference("#field"))
    }
}
