package com.itangcent.easyapi.core.util

import java.net.URI
import java.net.URL

/**
 * Helpers for the URL parsing cases that used to go through the JDK's
 * deprecated `URL(String)` constructor.
 *
 * `URL(String)` and its siblings are deprecated for removal since JDK 20
 * (JDK-8294241) — the supported replacement is to parse with [URI] and then
 * convert, i.e. `URI(spec).toURL()`. Never call a `URL(...)` constructor that
 * takes a spec string.
 */
object UrlUtils {

    /**
     * Parses [spec] into a [URL], returning `null` when it is not a valid
     * absolute URL. Never throws.
     */
    fun parseOrNull(spec: String): URL? =
        runCatching { URI(spec.trim()).toURL() }.getOrNull()

    /**
     * Extracts the host of [spec], returning `null` when [spec] has none.
     * Never throws.
     *
     * Beware the semantic difference from the deprecated `URL(String)`:
     * [URI.getHost] returns `null` for input without an authority instead of
     * throwing. A scheme-less `hoppscotch.io` therefore yields `null` where
     * `URL(String)` used to raise `MalformedURLException` — callers that relied
     * on catching that need to spell out their fallback.
     */
    fun hostOrNull(spec: String): String? =
        runCatching { URI(spec.trim()).host }.getOrNull()
}
