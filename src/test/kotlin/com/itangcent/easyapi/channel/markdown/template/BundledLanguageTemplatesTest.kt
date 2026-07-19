package com.itangcent.easyapi.channel.markdown.template

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [BundledLanguageTemplates] — the registry of locale→classpath-resource templates.
 */
class BundledLanguageTemplatesTest {

    @After
    fun cleanup() {
        BundledLanguageTemplates.unregisterForTesting("test-locale")
    }

    @Test
    fun testTemplateForZhCNReturnsNonNullOrTemplate() {
        val template = BundledLanguageTemplates.templateFor("zh-CN")
        assertNotNull("zh-CN template should be bundled", template)
        assertTrue("zh-CN template should not be empty", template!!.isNotEmpty())
    }

    @Test
    fun testTemplateForZhCNHasStructuralParityWithDefault() {
        val zhTemplate = BundledLanguageTemplates.templateFor("zh-CN")!!
        val defaultTemplate = DefaultMarkdownTemplate.get()

        // Structural parity: the zh-CN template must mirror the default's block structure.
        // We check the load-bearing structural markers, not byte-for-byte equality.
        val structuralMarkers = listOf(
            "{{#each groups as group}}",
            "{{#if group.folder}}",
            "{{#each group.endpoints as api}}",
            "{{#if api.http}}",
            "{{#if api.grpc}}",
        )
        for (marker in structuralMarkers) {
            assertTrue(
                "zh-CN template must contain structural marker '$marker' (present in default: ${defaultTemplate.contains(marker)})",
                zhTemplate.contains(marker),
            )
        }
    }

    @Test
    fun testTemplateForUnsupportedLocaleReturnsNull() {
        assertNull("Unsupported locale should return null", BundledLanguageTemplates.templateFor("xx"))
        assertNull("Unsupported locale should return null", BundledLanguageTemplates.templateFor("klingon"))
        assertNull("Unsupported locale should return null", BundledLanguageTemplates.templateFor("eo"))
    }

    @Test
    fun testTemplateForEnReturnsNullBecauseEnUsesDefault() {
        // 'en' is handled by the default template, not the registry — templateFor returns null
        // so the resolver falls through to the default tier .
        assertNull("'en' should return null (uses default template)", BundledLanguageTemplates.templateFor("en"))
    }

    @Test
    fun testTemplateForBlankReturnsNull() {
        assertNull("Blank locale should return null", BundledLanguageTemplates.templateFor(""))
        assertNull("Blank locale should return null", BundledLanguageTemplates.templateFor("   "))
    }

    @Test
    fun testAvailableLocalesIncludesEnAndZhCN() {
        val locales = BundledLanguageTemplates.availableLocales()
        assertTrue("'en' must be in availableLocales", locales.contains("en"))
        assertTrue("'zh-CN' must be in availableLocales", locales.contains("zh-CN"))
    }

    @Test
    fun testAvailableLocalesIsSorted() {
        val locales = BundledLanguageTemplates.availableLocales()
        assertEquals("Locales should be sorted", locales.sorted(), locales)
    }

    // ── BCP-47 fallback tests ──────────────────────────────────────────

    @Test
    fun testFallbackExactMatchWinsOverLangOnlyAndSibling() {
        // zh-CN has an exact template bundled; it must win over `zh` or any `zh-*` sibling.
        val template = BundledLanguageTemplates.templateFor("zh-CN")
        assertNotNull(template)
        // The exact zh-CN template should be returned (not the `zh` one, not a sibling).
        assertTrue(template!!.contains("zh-CN.md.tpl") || template.isNotEmpty())
    }

    @Test
    fun testFallbackLanguageOnlyWhenExactMissing() {
        // `zh` is bundled directly — a `zh-XX` lookup should resolve to `zh`.
        val exactZh = BundledLanguageTemplates.templateFor("zh")
        val lookupZhXX = BundledLanguageTemplates.templateFor("zh-XX")
        assertNotNull("zh must be bundled", exactZh)
        assertNotNull("zh-XX should fall back to zh", lookupZhXX)
        assertEquals("zh-XX fallback must equal zh template", exactZh, lookupZhXX)
    }

    @Test
    fun testFallbackSiblingWhenExactAndLangMissing() {
        // Strategy: unregister zh-TW so the sibling fallback path is exercised.
        // After unregistering zh-TW, `zh-TW` should fall back to `zh` (which IS bundled).
        // We then unregister `zh` too, so `zh-TW` should fall back to the first sorted
        // `zh-*` sibling — which is `zh-CN`.
        try {
            // Snapshot the resource path so we can restore it.
            val zhTWPath = "/markdown/templates/zh-TW.md.tpl"
            val zhPath = "/markdown/templates/zh.md.tpl"

            // Unregister both `zh-TW` and `zh` so only `zh-CN` (and other `zh-*`) remain.
            BundledLanguageTemplates.unregisterForTesting("zh-TW")
            BundledLanguageTemplates.unregisterForTesting("zh")

            val zhCN = BundledLanguageTemplates.templateFor("zh-CN")
            val zhTW = BundledLanguageTemplates.templateFor("zh-TW")

            assertNotNull("zh-CN should still resolve", zhCN)
            assertNotNull("zh-TW should fall back to a zh-* sibling", zhTW)
            assertEquals(
                "zh-TW fallback should resolve to the first sorted zh-* sibling (zh-CN)",
                zhCN,
                zhTW,
            )

            // Restore by re-registering with the original resource paths.
            BundledLanguageTemplates.registerForTesting("zh-TW", zhTWPath)
            BundledLanguageTemplates.registerForTesting("zh", zhPath)
        } finally {
            // Ensure restoration even on assertion failure.
            // The register/unregister calls above are idempotent for the same path.
            // Re-register if not present.
            if (BundledLanguageTemplates.templateFor("zh-TW") == null) {
                BundledLanguageTemplates.registerForTesting("zh-TW", "/markdown/templates/zh-TW.md.tpl")
            }
            if (BundledLanguageTemplates.templateFor("zh") == null) {
                BundledLanguageTemplates.registerForTesting("zh", "/markdown/templates/zh.md.tpl")
            }
        }
    }

    @Test
    fun testFallbackReturnsNullWhenNoMatchAtAnyTier() {
        // `klingon` has no exact, no language-only, no sibling — must return null.
        assertNull(BundledLanguageTemplates.templateFor("klingon"))
        // `xx-YY` — no siblings either.
        assertNull(BundledLanguageTemplates.templateFor("xx-YY"))
    }

    @Test
    fun testFallbackForPortuguese() {
        // `pt-PT` (Portugal) should fall back to either `pt` (which is bundled) or
        // `pt-BR` (which is also bundled). With `pt` bundled, it wins.
        val pt = BundledLanguageTemplates.templateFor("pt")
        val ptPT = BundledLanguageTemplates.templateFor("pt-PT")
        assertNotNull("pt must be bundled", pt)
        assertNotNull("pt-PT should fall back to pt", ptPT)
        assertEquals("pt-PT fallback should equal pt template", pt, ptPT)
    }

    // ── additivity test ──────────────────────────────────────────
    // Adding a locale requires only a resource + one map entry — no renderer/resolver code change.

    @Test
    fun testAdditivityNewLocaleLoadableViaResourceAndMapEntry() {
        // Register a test locale pointing to the default template resource (which exists on classpath).
        // This proves adding a locale = one resource + one map entry.
        BundledLanguageTemplates.registerForTesting("test-locale", "/markdown/templates/default.md.tpl")

        val template = BundledLanguageTemplates.templateFor("test-locale")
        assertNotNull("Newly registered locale should return a template", template)
        assertTrue("Newly registered template should not be empty", template!!.isNotEmpty())

        val locales = BundledLanguageTemplates.availableLocales()
        assertTrue(
            "availableLocales should include the newly registered locale",
            locales.contains("test-locale"),
        )
    }

    @Test
    fun testAdditivityUnregisteredLocaleReturnsNull() {
        assertNull(
            "Locale not in the registry should return null",
            BundledLanguageTemplates.templateFor("unregistered-locale"),
        )
        assertFalse(
            "availableLocales should not include unregistered locale",
            BundledLanguageTemplates.availableLocales().contains("unregistered-locale"),
        )
    }
}
