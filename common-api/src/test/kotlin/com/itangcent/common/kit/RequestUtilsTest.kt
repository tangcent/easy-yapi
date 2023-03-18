package com.itangcent.common.kit

import com.itangcent.common.utils.asArrayList
import com.itangcent.common.utils.asHashMap
import com.itangcent.http.RequestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Test case for [KitUtils]
 */
class RequestUtilsTest {

    private fun options(): List<Map<String, Any?>> = listOf(
        mapOf("value" to 1, "desc" to "ONE"),
        mapOf("value" to 2, "desc" to "TWO"),
        mapOf("value" to 3, "desc" to "THREE")
    )

    @Test
    fun testToRawBody() {
        assertNull(RequestUtils.toRawBody(null, false))

        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "z" to listOf("zzz"))
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        val subInfo: HashMap<Any, Any?> = hashMapOf("a" to 1, "b" to 2, "c" to arrayOf("ccc"))
        KVUtils.addComment(subInfo, "a", "The value of the x axis")
        KVUtils.addKeyOptions(subInfo, "a", options().map { it.asHashMap() }.asArrayList())
        info["sub"] = subInfo
        assertEquals(
            "{\"x\":1,\"y\":2,\"sub\":{\"a\":1,\"b\":2,\"c\":[\"ccc\"]},\"z\":[\"zzz\"]}",
            RequestUtils.toRawBody(info, false).toJson()
        )
    }

    @Test
    fun testParseRawBody() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(
            "{\n" +
                    "  \"x\": 1,\n" +
                    "  \"y\": 2\n" +
                    "}", RequestUtils.parseRawBody(info)
        )
        assertEquals(
            "{\n" +
                    "  \"x\": 1,\n" +
                    "  \"y\": 2\n" +
                    "}", RequestUtils.parseRawBody(info, false)
        )
        assertEquals("abc", RequestUtils.parseRawBody("abc"))
        assertEquals("abc", RequestUtils.parseRawBody("abc", false))
    }

    @ParameterizedTest
    @CsvSource("a,b", "a/,b", "a,/b", "a/,/b")
    fun testConcatPathToAB(pre: String, after: String) {
        assertEquals("a/b", RequestUtils.concatPath(pre, after))
    }

    @ParameterizedTest
    @CsvSource(
        value = ["a,null", "null,a"],
        nullValues = ["null"]
    )
    fun testConcatPathToA(
        pre: String?,
        after: String?,
    ) {
        assertEquals("a", RequestUtils.concatPath(pre, after))
    }

    @Test
    fun testAddQuery() {
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls",
            RequestUtils.addQuery("https://github.com/tangcent/easy-yapi/pulls", null)
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls?q=is%3Apr+label%3Aenhancement+",
            RequestUtils.addQuery("https://github.com/tangcent/easy-yapi/pulls", "q=is%3Apr+label%3Aenhancement+")
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls?q=is%3Apr+label%3Aenhancement+",
            RequestUtils.addQuery("https://github.com/tangcent/easy-yapi/pulls", "?q=is%3Apr+label%3Aenhancement+")
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls?q=is%3Apr+label%3Aenhancement+",
            RequestUtils.addQuery("https://github.com/tangcent/easy-yapi/pulls?", "q=is%3Apr+label%3Aenhancement+")
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls?q=is%3Apr+label%3Aenhancement+",
            RequestUtils.addQuery("https://github.com/tangcent/easy-yapi/pulls?", "?q=is%3Apr+label%3Aenhancement+")
        )
    }

    @Test
    fun testAddProtocolIfNeed() {
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls",
            RequestUtils.addProtocolIfNeed("https://github.com/tangcent/easy-yapi/pulls", "https:")
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls",
            RequestUtils.addProtocolIfNeed("github.com/tangcent/easy-yapi/pulls", "https")
        )
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls",
            RequestUtils.addProtocolIfNeed("https://github.com/tangcent/easy-yapi/pulls", "ftp:")
        )
    }

    @Test
    fun testUrlBuild() {
        assertEquals(
            "http://www.itangcent.com",
            RequestUtils.UrlBuild()
                .host("http://www.itangcent.com")
                .url()
        )

        assertThrows<NullPointerException> { RequestUtils.UrlBuild().url() }

        val build = RequestUtils.UrlBuild()
        build.host("github.com")
        assertEquals("http://github.com", build.url())
        build.protocol("https")
        assertEquals("https://github.com", build.url())
        build.path("tangcent/easy-yapi/pulls")
        assertEquals("https://github.com/tangcent/easy-yapi/pulls", build.url())
        build.query("token=123")
        assertEquals("https://github.com/tangcent/easy-yapi/pulls?token=123", build.url())
        build.query("q", "is%3Apr+label%3Aenhancement+")
        assertEquals(
            "https://github.com/tangcent/easy-yapi/pulls?token=123&q=is%3Apr+label%3Aenhancement+",
            build.url()
        )
    }
}