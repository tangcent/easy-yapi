package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.logging.IdeaLog
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of bundled locale→classpath-resource Markdown templates .
 *
 * The Default Template (English, byte-identical per Req 2) is **not** in this registry —
 * it is handled by [DefaultMarkdownTemplate] and serves as the floor of the precedence
 * chain. `en` therefore appears in [availableLocales] but [templateFor]("en") returns `null`
 * so the resolver falls through to the default tier .
 *
 * ## Language fallback
 *
 * [templateFor] resolves a locale using BCP-47 fallback (see RFC 4647 §3.4):
 *
 * 1. **Exact match** — the full tag (e.g. `"zh-TW"`, `"pt-BR"`).
 * 2. **Language-only** — the language subtag (e.g. `"zh"`). This catches
 *    `zh.md.tpl` if it exists.
 * 3. **First `lang-*` sibling** — any other registered locale that starts with
 *    `"<lang>-"` (e.g. for `"zh-TW"`, fall back to `"zh-CN"` if no `"zh"` template
 *    is bundled). Siblings are tried in sorted order for deterministic resolution.
 *
 * So a Traditional Chinese user (`zh-TW`) resolves to the Simplified Chinese template
 * (`zh-CN`) when no `zh-TW` or `zh` template is bundled — same script family, far
 * better than falling through to English.
 *
 * ## Adding a locale
 *
 * Adding a bundled language template requires **only**:
 * 1. Dropping a `<locale>.md.tpl` resource under `src/main/resources/markdown/templates/`.
 * 2. Adding one entry to [LOCALE_TO_RESOURCE] below.
 *
 * No renderer or resolver code change is needed — [templateFor] and [availableLocales]
 * are pure lookups over the map.
 *
 * @see DefaultMarkdownTemplate
 * @see MarkdownTemplateResolver
 */
object BundledLanguageTemplates : IdeaLog {

    /**
     * BCP-47 locale tag → classpath resource path.
     *
     * `en` is intentionally absent — it uses the default template .
     *
     * The bundled set covers the languages IntelliJ IDEA is localized into
     * (minus `en`, which uses the default template). Adding a new locale
     * requires only a new `.md.tpl` resource + one entry here.
     */
    private val LOCALE_TO_RESOURCE: MutableMap<String, String> = mutableMapOf(
        "zh-CN" to "/markdown/templates/zh-CN.md.tpl",
        "zh-TW" to "/markdown/templates/zh-TW.md.tpl",
        "zh" to "/markdown/templates/zh.md.tpl",
        "ja" to "/markdown/templates/ja.md.tpl",
        "ko" to "/markdown/templates/ko.md.tpl",
        "es" to "/markdown/templates/es.md.tpl",
        "fr" to "/markdown/templates/fr.md.tpl",
        "de" to "/markdown/templates/de.md.tpl",
        "ru" to "/markdown/templates/ru.md.tpl",
        "pt-BR" to "/markdown/templates/pt-BR.md.tpl",
        "pt" to "/markdown/templates/pt.md.tpl",
        "it" to "/markdown/templates/it.md.tpl",
        "ar" to "/markdown/templates/ar.md.tpl",
        "hi" to "/markdown/templates/hi.md.tpl",
        "vi" to "/markdown/templates/vi.md.tpl",
        "id" to "/markdown/templates/id.md.tpl",
        "tr" to "/markdown/templates/tr.md.tpl",
        "pl" to "/markdown/templates/pl.md.tpl",
        "uk" to "/markdown/templates/uk.md.tpl",
        "nl" to "/markdown/templates/nl.md.tpl",
        "th" to "/markdown/templates/th.md.tpl",
    )

    /** Per-locale memoized template text. Cleared on test registration changes. */
    private val cache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    /**
     * Returns the bundled template text for [locale], or `null` if no bundled template
     * exists for that locale after applying the BCP-47 fallback chain.
     *
     * Resolution order (first non-null wins):
     *
     * 1. **Exact match** — `[locale]` (e.g. `"zh-TW"`).
     * 2. **Language-only** — the language subtag (e.g. `"zh"`).
     * 3. **First `lang-*` sibling** — any registered locale starting with `"<lang>-"`,
     *    tried in sorted order.
     *
     * - `en` and blank → `null` (the default template handles English; the resolver's
     *   default tier is the floor).
     * - Unsupported locale (no match at any tier) → `null` (caller logs `warn` +
     *   `notifyWarning`, then falls through to the default tier).
     *
     * @param locale A BCP-47 tag (e.g. `"zh-CN"`, `"ja"`, `"en"`).
     * @return The template text, or `null`.
     */
    fun templateFor(locale: String): String? {
        if (locale.isBlank() || locale == "en") return null

        // (1) Exact match.
        LOCALE_TO_RESOURCE[locale]?.let { path ->
            return cache.computeIfAbsent(locale) { loadResource(path) ?: "" }
                .takeIf { it.isNotEmpty() }
        }

        // (2) Language-only fallback (e.g. "zh-TW" → "zh").
        val lang = languageSubtag(locale)
        if (lang != null && lang != locale) {
            LOCALE_TO_RESOURCE[lang]?.let { path ->
                val text = cache.computeIfAbsent(lang) { loadResource(path) ?: "" }
                if (text.isNotEmpty()) {
                    return text
                }
            }
        }

        // (3) First lang-* sibling fallback (e.g. "zh-TW" → "zh-CN").
        //     Sorted for deterministic resolution; the registry is small so a linear
        //     scan is fine.
        if (lang != null) {
            val siblingPrefix = "$lang-"
            val sibling = LOCALE_TO_RESOURCE.keys
                .filter { it.startsWith(siblingPrefix) }
                .sorted()
                .firstOrNull()
            if (sibling != null) {
                val path = LOCALE_TO_RESOURCE.getValue(sibling)
                val text = cache.computeIfAbsent(sibling) { loadResource(path) ?: "" }
                if (text.isNotEmpty()) {
                    return text
                }
            }
        }

        return null
    }

    /**
     * The set of locales the user can select, including `en` (handled by the default
     * template) and every locale in the registry. Sorted for stable UI display.
     */
    fun availableLocales(): List<String> {
        return (listOf("en") + LOCALE_TO_RESOURCE.keys).distinct().sorted()
    }

    // ── Test-only: additivity proof ─────────────────────────────
    // Adding a locale = one resource + one map entry. These methods let tests verify
    // that no renderer/resolver code change is needed.

    /**
     * @suppress Test-only — registers a locale pointing to an existing classpath resource
     * to prove additivity.
     */
    internal fun registerForTesting(locale: String, resourcePath: String) {
        LOCALE_TO_RESOURCE[locale] = resourcePath
        cache.remove(locale)
    }

    /**
     * @suppress Test-only — removes a locale registered via [registerForTesting].
     */
    internal fun unregisterForTesting(locale: String) {
        LOCALE_TO_RESOURCE.remove(locale)
        cache.remove(locale)
    }

    /**
     * Extracts the language subtag from a BCP-47 tag.
     *
     * - `"zh-CN"` → `"zh"`
     * - `"zh"` → `"zh"`
     * - `"pt-BR"` → `"pt"`
     * - `""` → `null`
     *
     * Uses [Locale.forLanguageTag] to parse robustly; falls back to a manual split
     * if the JDK parser yields an empty language (it does for some malformed inputs).
     */
    private fun languageSubtag(locale: String): String? {
        if (locale.isBlank()) return null
        // Try the JDK parser first — handles BCP-47 edge cases (extlangs, variants).
        val parsed = Locale.forLanguageTag(locale).language
        if (parsed.isNotEmpty()) return parsed
        // Manual fallback for inputs the JDK normalizes to empty (e.g. "zh-TW" → "zh"
        // is fine, but defensive: split on '-' and take the head).
        return locale.substringBefore('-').takeIf { it.isNotBlank() && it.all { c -> c.isLetter() } }
    }

    private fun loadResource(path: String): String? {
        return try {
            javaClass.getResourceAsStream(path)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            LOG.warn("Failed to load bundled language template: $path", t)
            null
        }
    }
}
