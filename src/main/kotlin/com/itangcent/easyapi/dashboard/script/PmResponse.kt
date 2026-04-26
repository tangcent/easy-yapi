package com.itangcent.easyapi.dashboard.script

import groovy.json.JsonSlurper

/**
 * Represents an HTTP response received from the server, compatible with Postman's `pm.response` API.
 *
 * Provides access to the status code, headers, body (as text, JSON, or XML), timing, and size.
 * Also supports BDD-style assertions via the [to] and [be] properties.
 *
 * Groovy usage:
 * ```groovy
 * pm.test("Status is 200") {
 *     pm.response.to.be.ok
 * }
 * pm.test("Response is JSON") {
 *     pm.response.to.be.json
 * }
 * pm.test("Body contains user") {
 *     pm.expect(pm.response.json().name).to.equal("Alice")
 * }
 * ```
 *
 * @property code The HTTP status code
 * @property status The HTTP status message (e.g., "OK", "Not Found")
 * @property headers Response headers
 * @property responseTime Response time in milliseconds
 * @property responseSize Response body size in bytes
 */
class PmResponse(
    val code: Int,
    val status: String,
    val headers: PmHeaderList,
    val responseTime: Long,
    val responseSize: Long,
    private val rawBody: String
) {

    /** Returns the raw response body as a string. */
    fun text(): String = rawBody

    /**
     * Parses the response body as JSON using Groovy's [JsonSlurper].
     *
     * @return The parsed JSON object (Map, List, etc.), or null if parsing fails
     */
    fun json(): Any? {
        return try {
            JsonSlurper().parseText(rawBody)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses the response body as XML using Groovy's [XmlSlurper].
     *
     * @return The parsed XML GPathResult, or null if parsing fails
     */
    fun xml(): Any? {
        return try {
            groovy.xml.XmlSlurper().parseText(rawBody)
        } catch (_: Exception) {
            null
        }
    }

    /** Entry point for BDD-style assertions: `pm.response.to.be.ok` */
    val to: PmResponseBDD get() = PmResponseBDD(this)

    /** Alias for [to] — allows `pm.response.be.ok` */
    val be: PmResponseBDD get() = PmResponseBDD(this)
}

/**
 * BDD-style assertion builder for [PmResponse].
 *
 * Provides readable assertions like `pm.response.to.be.ok`, `pm.response.to.be.json`,
 * and `pm.response.to.have.status(200)`.
 *
 * Negation is available via [not]: `pm.response.to.not.be.ok`
 */
class PmResponseBDD(internal val response: PmResponse) {

    /** Chain word — returns this for readability: `pm.response.to.be.ok` */
    val be: PmResponseBDD get() = this

    /** Asserts the response status code is 2xx. */
    val ok: Unit
        get() {
            check(response.code in 200..299) { "Expected status 2xx but was ${response.code}" }
        }

    /** Asserts the Content-Type header contains "json". */
    val json: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(ct.contains("json", ignoreCase = true)) { "Expected Content-Type to contain 'json' but was '$ct'" }
        }

    /** Asserts the Content-Type header contains "html". */
    val html: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(ct.contains("html", ignoreCase = true)) { "Expected Content-Type to contain 'html' but was '$ct'" }
        }

    /** Asserts the Content-Type header contains "xml". */
    val xml: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(ct.contains("xml", ignoreCase = true)) { "Expected Content-Type to contain 'xml' but was '$ct'" }
        }

    /** Asserts the response status code is 4xx or 5xx. */
    val error: Unit
        get() {
            check(response.code >= 400) { "Expected error status (4xx/5xx) but was ${response.code}" }
        }

    val have: PmResponseHave get() = PmResponseHave(response)

    /** Returns a negated BDD builder: `pm.response.to.not.be.ok` */
    fun not(): PmResponseBDDNegated = PmResponseBDDNegated(response)
}

/**
 * Negated BDD-style assertion builder for [PmResponse].
 *
 * All assertions are inverted: `pm.response.to.not.be.ok` asserts the status is NOT 2xx.
 */
class PmResponseBDDNegated(internal val response: PmResponse) {

    /** Chain word — returns this for readability: `pm.response.to.not.be.ok` */
    val be: PmResponseBDDNegated get() = this

    /** Asserts the response status code is NOT 2xx. */
    val ok: Unit
        get() {
            check(response.code !in 200..299) { "Expected status to not be 2xx but was ${response.code}" }
        }

    /** Asserts the Content-Type header does NOT contain "json". */
    val json: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(!ct.contains("json", ignoreCase = true)) { "Expected Content-Type to not contain 'json' but was '$ct'" }
        }

    /** Asserts the Content-Type header does NOT contain "html". */
    val html: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(!ct.contains("html", ignoreCase = true)) { "Expected Content-Type to not contain 'html' but was '$ct'" }
        }

    /** Asserts the Content-Type header does NOT contain "xml". */
    val xml: Unit
        get() {
            val ct = response.headers.get("Content-Type") ?: ""
            check(!ct.contains("xml", ignoreCase = true)) { "Expected Content-Type to not contain 'xml' but was '$ct'" }
        }

    /** Asserts the response status code is NOT 4xx/5xx. */
    val error: Unit
        get() {
            check(response.code < 400) { "Expected status to not be error (4xx/5xx) but was ${response.code}" }
        }

    val have: PmResponseHaveNot get() = PmResponseHaveNot(response)
}

/**
 * Provides `have` assertion methods for [PmResponseBDD].
 *
 * Usage: `pm.response.to.have.status(200)`, `pm.response.to.have.header("Content-Type")`
 */
class PmResponseHave(private val response: PmResponse) {

    /** Asserts the response has the exact given status code. */
    fun status(code: Int) {
        check(response.code == code) { "Expected status $code but was ${response.code}" }
    }

    /** Asserts the response body equals the given text exactly. */
    fun body(text: String) {
        check(response.text() == text) { "Expected body to equal '$text' but was '${response.text().take(100)}'" }
    }

    /** Asserts the parsed JSON body contains the given key. */
    fun jsonBody(key: String) {
        val json = response.json() as? Map<*, *> ?: throw AssertionError("Response is not a JSON object")
        check(json.containsKey(key)) { "Expected JSON body to contain key '$key'" }
    }

    /** Asserts the parsed JSON body has the given key with the expected value. */
    fun jsonBody(key: String, value: Any?) {
        val json = response.json() as? Map<*, *> ?: throw AssertionError("Response is not a JSON object")
        val actual = json[key]
        check(actual == value) { "Expected JSON body '$key' to be '$value' but was '$actual'" }
    }

    /** Asserts the response contains the given header. */
    fun header(name: String) {
        check(response.headers.has(name)) { "Expected header '$name' to be present" }
    }

    /**
     * Validates the response JSON body against a simplified JSON Schema.
     *
     * @param schema A map representing the JSON Schema (supports "type", "properties", "required", "items")
     */
    fun jsonSchema(schema: Map<String, Any?>) {
        val json = response.json()
        check(json != null) { "Response body is not valid JSON" }
        JsonSchemaValidator.validate(json, schema)
    }
}

/**
 * Provides negated `have` assertion methods for [PmResponseBDDNegated].
 *
 * Usage: `pm.response.to.not.have.status(500)`, `pm.response.to.not.have.header("X-Error")`
 */
class PmResponseHaveNot(private val response: PmResponse) {

    /** Asserts the response does NOT have the given status code. */
    fun status(code: Int) {
        check(response.code != code) { "Expected status to not be $code" }
    }

    /** Asserts the response body does NOT equal the given text. */
    fun body(text: String) {
        check(response.text() != text) { "Expected body to not equal '$text'" }
    }

    fun jsonBody(key: String) {
        val json = response.json() as? Map<*, *> ?: return
        check(!json.containsKey(key)) { "Expected JSON body to not contain key '$key'" }
    }

    fun header(name: String) {
        check(!response.headers.has(name)) { "Expected header '$name' to not be present" }
    }
}
