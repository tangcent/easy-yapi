package com.itangcent.easyapi.core.http

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Tests for [HttpRequestBuilder] and the [HttpExecutor] entry points.
 *
 * Covers the fluent mapping onto [HttpRequest], the Kotlin `execute { }` block form, and the
 * **Groovy compatibility contract** that keeps the builder callable from JSR-223 rule scripts
 * (see [com.itangcent.easyapi.core.rule.parser.Jsr223ScriptParser]).
 */
class HttpRequestBuilderTest {

    // ------------------------------------------------------------- mapping

    @Test
    fun `verb shortcut sets method and url`() {
        val request = HttpRequestBuilder().url("https://example.com/refresh").post().build()
        assertEquals("https://example.com/refresh", request.url)
        assertEquals("POST", request.method)
        assertEquals("GET", HttpRequestBuilder().url("u").get().build().method)
        assertEquals("PUT", HttpRequestBuilder().url("u").put().build().method)
        assertEquals("DELETE", HttpRequestBuilder().url("u").delete().build().method)
        assertEquals("PATCH", HttpRequestBuilder().url("u").patch().build().method)
        assertEquals("HEAD", HttpRequestBuilder().url("u").head().build().method)
        assertEquals("OPTIONS", HttpRequestBuilder().url("u").options().build().method)
        assertEquals("CUSTOM", HttpRequestBuilder().url("u").method("CUSTOM").build().method)
    }

    @Test
    fun `headers append, upsert and remove case-insensitively`() {
        val request = HttpRequestBuilder()
            .header("Accept", "application/json")
            .header("X-Trace", "1")
            .setHeader("x-trace", "2")
            .removeHeader("ACCEPT")
            .build()

        // setHeader keeps the casing it was given (same contract as HttpRequestWrapper.setHeader)
        assertEquals(listOf(kv("x-trace", "2")), request.headers)
    }

    @Test
    fun `headers and query accept a map`() {
        val request = HttpRequestBuilder()
            .url("https://example.com")
            .headers(mapOf("A" to "1", "B" to "2"))
            .query(mapOf("page" to "1", "size" to "20"))
            .build()

        assertEquals(listOf(kv("A", "1"), kv("B", "2")), request.headers)
        assertEquals(listOf(kv("page", "1"), kv("size", "20")), request.query)
    }

    @Test
    fun `body leaves contentType untouched, json sets application json`() {
        assertEquals(null, HttpRequestBuilder().body("raw").build().contentType)
        assertEquals(
            HttpRequestBuilder.CONTENT_TYPE_JSON,
            HttpRequestBuilder().json("""{"a":1}""").build().contentType
        )
        assertEquals("""{"a":1}""", HttpRequestBuilder().json("""{"a":1}""").build().body)
        assertEquals("text/xml", HttpRequestBuilder().body("<a/>").contentType("text/xml").build().contentType)
    }

    @Test
    fun `contentType assignment upserts the header and a null assignment clears it`() {
        // A value replaces an existing header (case-insensitive, no duplicate is left behind).
        val replaced = HttpRequestBuilder()
            .header("content-type", "text/xml")
            .contentType("application/json")
            .build()
        assertEquals("application/json", replaced.contentType)
        assertEquals(listOf(kv("Content-Type", "application/json")), replaced.headers)

        // `null` is the explicit "clear the header" form.
        val cleared = HttpRequestBuilder()
            .header("Content-Type", "application/json")
            .contentType(null)
            .build()
        assertNull(cleared.contentType)
        assertTrue(cleared.headers.none { it.name.equals("Content-Type", ignoreCase = true) })
    }

    @Test
    fun `a metadata content type is only a hint and never overwrites a declared header`() {
        // Callers holding only a hint (endpoint metadata) guard the assignment themselves —
        // assigning `null` would clear the header. Mirrors RequestExecutor's call sites.
        fun HttpRequestBuilder.applyHint(hint: String?) = apply {
            if (contentType == null) hint?.takeIf { it.isNotBlank() }?.let { contentType = it }
        }

        val declared = HttpRequestBuilder()
            .header("Content-Type", "text/xml")
            .applyHint("application/json")
            .build()
        assertEquals("text/xml", declared.contentType)
        assertEquals(listOf(kv("Content-Type", "text/xml")), declared.headers)

        val filled = HttpRequestBuilder()
            .body("{}")
            .applyHint("application/json")
            .build()
        assertEquals("application/json", filled.contentType)
        assertEquals(listOf(kv("Content-Type", "application/json")), filled.headers)

        // A null / blank hint must not clear or blank out an existing header.
        val kept = HttpRequestBuilder()
            .header("Content-Type", "application/json")
            .applyHint(null)
            .applyHint("   ")
            .build()
        assertEquals("application/json", kept.contentType)
        assertEquals(listOf(kv("Content-Type", "application/json")), kept.headers)

        // No header and no usable hint → still no Content-Type (auto-detected from form params).
        assertNull(HttpRequestBuilder().applyHint(null).build().contentType)
        assertNull(HttpRequestBuilder().applyHint("   ").build().contentType)
    }

    @Test
    fun `form produces urlencoded params and stays non-multipart`() {
        val request = HttpRequestBuilder()
            .url("https://example.com/refresh")
            .post()
            .form("grant_type", "refresh_token")
            .form(mapOf("client_id" to "abc"))
            .build()

        assertEquals(
            mapOf("grant_type" to "refresh_token", "client_id" to "abc"),
            request.textFormParams()
        )
        assertEquals(false, request.isMultipart())
        // No explicit type — ApacheHttpClient encodes formParams as x-www-form-urlencoded.
        assertNull(request.contentType)
    }

    @Test
    fun `file part switches the request to multipart`() {
        val request = HttpRequestBuilder()
            .url("https://example.com/upload")
            .post()
            .form("note", "hi")
            .file("file", "a.txt", byteArrayOf(1, 2, 3), "text/plain")
            .build()

        assertTrue(request.isMultipart())
        assertEquals(2, request.formParams.size)
    }

    @Test
    fun `cookies are collected`() {
        val request = HttpRequestBuilder()
            .cookie("sid", "1")
            .cookie("tid", "2", "example.com", "/")
            .build()

        assertEquals(
            listOf(HttpCookie("sid", "1", null, null), HttpCookie("tid", "2", "example.com", "/")),
            request.cookies
        )
    }

    // -------------------------------------------------- Kotlin block form

    @Test
    fun `execute block assigns properties and sends through the executor`() = runBlocking {
        val expected = HttpResponse(code = 200, body = "OK")
        val executor = RecordingHttpClient(expected)

        val response = executor.execute {
            url = "https://example.com/refresh"
            method = "POST"
            body = "grant_type=refresh_token"
            contentType = "application/x-www-form-urlencoded"
            header("Accept", "application/json")
        }

        assertSame(expected, response)
        val sent = executor.sent.single()
        assertEquals("https://example.com/refresh", sent.url)
        assertEquals("POST", sent.method)
        assertEquals("grant_type=refresh_token", sent.body)
        assertEquals("application/x-www-form-urlencoded", sent.contentType)
        assertEquals(
            listOf(kv("Content-Type", "application/x-www-form-urlencoded"), kv("Accept", "application/json")),
            sent.headers
        )
    }

    @Test
    fun `execute block mixes property assignment with verb shortcuts`() = runBlocking {
        val executor = RecordingHttpClient(HttpResponse(code = 201))

        executor.execute {
            url = "https://example.com/refresh"
            post()
            form("grant_type", "refresh_token")
        }

        val sent = executor.sent.single()
        assertEquals("POST", sent.method)
        assertEquals(mapOf("grant_type" to "refresh_token"), sent.textFormParams())
        assertNull(sent.body)
    }

    @Test
    fun `get block presets the method and applies the config`() = runBlocking {
        val executor = RecordingHttpClient(HttpResponse(code = 200))

        executor.get {
            url = "https://example.com/users"
            header("Accept", "application/json")
        }

        val sent = executor.sent.single()
        assertEquals("GET", sent.method)
        assertEquals("https://example.com/users", sent.url)
        assertEquals(listOf(kv("Accept", "application/json")), sent.headers)
        assertNull(sent.body)
    }

    @Test
    fun `post block presets the method and applies the config`() = runBlocking {
        val executor = RecordingHttpClient(HttpResponse(code = 201))

        executor.post {
            url = "https://example.com/refresh"
            json("""{"a":1}""")
        }

        val sent = executor.sent.single()
        assertEquals("POST", sent.method)
        assertEquals("https://example.com/refresh", sent.url)
        assertEquals(HttpRequestBuilder.CONTENT_TYPE_JSON, sent.contentType)
    }

    @Test
    fun `explicit method inside a verb block wins over the preset`() = runBlocking {
        val executor = RecordingHttpClient(HttpResponse(code = 200))

        executor.post {
            url = "https://example.com/override"
            method = "PUT"
        }

        assertEquals("PUT", executor.sent.single().method)
    }

    @Test
    fun `HttpClient is an HttpExecutor`() {
        assertTrue(HttpExecutor::class.java.isAssignableFrom(HttpClient::class.java))
    }

    // ------------------------------------------------------- fluent form

    @Test
    fun `newRequest binds the executor and execute sends the built request`() {
        val executor = RecordingHttpClient(HttpResponse(code = 200, body = "OK"))

        val response = executor.newRequest("https://example.com/refresh")
            .post()
            .form("grant_type", "refresh_token")
            .execute()

        assertEquals("OK", response.body)
        val sent = executor.sent.single()
        assertEquals("https://example.com/refresh", sent.url)
        assertEquals("POST", sent.method)
        assertEquals(mapOf("grant_type" to "refresh_token"), sent.textFormParams())
    }

    @Test
    fun `newRequest without url still executes`() {
        val executor = RecordingHttpClient(HttpResponse(code = 204))

        val response = executor.newRequest()
            .url("https://example.com/ping")
            .get()
            .execute()

        assertEquals(204, response.code)
        assertEquals("GET", executor.sent.single().method)
    }

    @Test
    fun `ScriptHttpClient newRequest chains and executes through the delegate`() {
        val expected = HttpResponse(code = 200, body = """{"access_token":"newTok"}""")
        val delegate = RecordingHttpClient(expected)
        val client = ScriptHttpClient(delegate)

        val response = client.newRequest("https://example.com/refresh")
            .post()
            .form("grant_type", "refresh_token")
            .execute()

        assertSame(expected, response)
        val sent = delegate.sent.single()
        assertEquals("https://example.com/refresh", sent.url)
        assertEquals("POST", sent.method)
        assertEquals(mapOf("grant_type" to "refresh_token"), sent.textFormParams())
    }

    @Test
    fun `execute without a bound executor fails fast`() {
        try {
            HttpRequestBuilder().url("https://example.com").execute()
            fail("execute() should require a bound executor")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("no bound HttpExecutor"))
        }
    }

    // ------------------------------------------------- Groovy compatibility

    /**
     * The builder is called from Groovy, which cannot use Kotlin default arguments, suspend
     * functions, or lambda-with-receiver blocks. Guard the contract so a future edit cannot
     * silently break scripts.
     */
    @Test
    fun `public methods stay Groovy-callable`() {
        val forbiddenParams = setOf(
            "kotlin.coroutines.Continuation", // suspend — Groovy cannot await
            "kotlin.jvm.internal.DefaultConstructorMarker" // Kotlin default args — invisible to Groovy
        )
        val terminals = setOf("build", "execute")

        HttpRequestBuilder::class.java.methods
            .filter { it.declaringClass == HttpRequestBuilder::class.java }
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            // public vars (url/method/body/contentType) expose getX/setX — accessors are fine
            .filterNot { it.name.startsWith("get") || it.name.startsWith("set") }
            .forEach { method ->
                method.parameterTypes.forEach { p ->
                    check(p.name !in forbiddenParams) {
                        "${method.name} must not use suspend or default arguments — " +
                            "Groovy scripts cannot call it (found ${p.name})"
                    }
                }
                if (method.name !in terminals) {
                    assertEquals(
                        "${method.name} must return HttpRequestBuilder for chaining",
                        HttpRequestBuilder::class.java,
                        method.returnType
                    )
                }
            }
    }

    /** Minimal [HttpClient] (hence [HttpExecutor]) that records what was sent. */
    private class RecordingHttpClient(private val response: HttpResponse) : HttpClient {
        val sent = ArrayList<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            sent.add(request)
            return response
        }

        override fun close() {}
    }
}
