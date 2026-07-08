package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.Locale

/**
 * Pins the locale-detection contract of [AmbientPerception.capture] .
 *
 * The detection priority chain :
 *
 * ```
 * 1. IDE locale (Locale.getDefault(), non-English)
 * 2. null (no suggestion — English or undetermined)
 * ```
 *
 * `detectUserLanguage` is a generic user-language hint, NOT a markdown-specific
 * resolution. Markdown-specific rules (e.g. `markdown.template.language`) are
 * intentionally NOT consulted here — those belong to the Markdown resolver,
 * and the agent proposes them when the ambient language hint is non-English.
 *
 * The capture is pure-ish: no PSI reads, no network, no config access — it
 * reads only `Locale.getDefault()` (JVM-global). Tests set/restore the default
 * locale in `tearDown` to avoid cross-test contamination.
 *
 * Env-var keys are intentionally NOT captured (source-of-truth is the existing
 * rule files + source code, resolved via `get_existing_rules_for_key`; the
 * Environments panel is runtime state, not a rule-authoring source).
 */
class AmbientPerceptionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private var savedLocale: Locale? = null

    override fun tearDown() {
        savedLocale?.let { Locale.setDefault(it) }
        super.tearDown()
    }

    // ── IDE locale fallback ──

    fun testUserLanguageFromIdeLocaleWhenNoRule() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE) // toLanguageTag() → "zh-CN"

        val ambient = AmbientPerception.capture(project)

        assertNotNull("userLanguage should be detected from IDE locale", ambient.userLanguage)
        assertEquals("zh-CN", ambient.userLanguage)
    }

    fun testUserLanguageFromIdeLocaleChina() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.CHINA) // language=zh, country=CN → "zh-CN"

        val ambient = AmbientPerception.capture(project)

        assertEquals("zh-CN", ambient.userLanguage)
    }

    fun testUserLanguageFromIdeLocaleJapanese() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.JAPAN) // language=ja, country=JP → "ja-JP"

        val ambient = AmbientPerception.capture(project)

        assertEquals("ja-JP", ambient.userLanguage)
    }

    // ── English / undetermined → null (no suggestion) ──

    fun testUserLanguageNullWhenEnglishLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)

        val ambient = AmbientPerception.capture(project)

        assertNull("English locale should not trigger a suggestion", ambient.userLanguage)
    }

    fun testUserLanguageNullWhenUsLocale() {
        // en-US is English → no suggestion (default template is English).
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        val ambient = AmbientPerception.capture(project)

        assertNull("en-US should not trigger a suggestion", ambient.userLanguage)
    }

    // ── BCP-47 tag format ──

    fun testUserLanguageIsBcp47Tag() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

        val ambient = AmbientPerception.capture(project)

        assertNotNull(ambient.userLanguage)
        assertTrue(
            "userLanguage should be a BCP-47 tag (e.g. zh-CN, en, ja): ${ambient.userLanguage}",
            ambient.userLanguage!!.matches(Regex("[a-z]{2,3}(-[A-Z]{2})?"))
        )
    }

    // ── Markdown-specific rules are NOT consulted ──

    fun testUserLanguageFromIdeLocaleEvenWhenMarkdownLanguageRuleExists() {
        // The markdown.template.language rule is a markdown-specific business
        // rule and must NOT feed into the ambient language hint. Detection is
        // driven solely by the IDE locale.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.KOREA) // language=ko, country=KR → "ko-KR"

        val ambient = AmbientPerception.capture(project)

        assertEquals("ko-KR", ambient.userLanguage)
    }
}
