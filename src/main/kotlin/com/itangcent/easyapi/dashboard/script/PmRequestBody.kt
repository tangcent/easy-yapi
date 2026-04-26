package com.itangcent.easyapi.dashboard.script

/**
 * Represents the HTTP request body, compatible with Postman's `pm.request.body` API.
 *
 * Supports three body modes:
 * - **raw**: A raw string body (JSON, XML, plain text, etc.)
 * - **urlencoded**: URL-encoded form data via [urlencoded]
 * - **formdata**: Multipart form data via [formdata]
 *
 * Groovy usage:
 * ```groovy
 * pm.request.body.mode = "raw"
 * pm.request.body.raw = '{"name": "Alice"}'
 * pm.request.body.urlencoded.add("grant_type", "password")
 * pm.request.body.formdata.add("file", "@/path/to/file", "file")
 * ```
 *
 * @property raw The raw string body content, or null if not set
 * @property mode The body mode: "raw", "urlencoded", or "formdata"
 */
class PmRequestBody(
    var raw: String? = null,
    var mode: String = "raw"
) {

    /** URL-encoded form parameters for `application/x-www-form-urlencoded` bodies. */
    val urlencoded: PmPropertyList = PmPropertyList()

    /** Multipart form data entries for `multipart/form-data` bodies. */
    val formdata: PmPropertyList = PmPropertyList()
}
