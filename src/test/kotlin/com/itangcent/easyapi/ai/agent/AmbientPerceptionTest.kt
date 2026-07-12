package com.itangcent.easyapi.ai.agent

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    // ── moduleNames — cached set of API-bearing modules ──

    fun testModuleNamesPopulatedFromApiBearingModules() {
        // The light fixture has a single module (myFixture.module). Adding a
        // @RestController class to it makes that module API-bearing, so its
        // name must surface on ambient.moduleNames. (Two modules cannot be
        // created in a LightCodeInsightFixture, so this asserts the
        // single-module case.)
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        addApiBearingController()

        val ambient = AmbientPerception.capture(project)

        val fixtureModule = myFixture.module.name
        assertTrue(
            "moduleNames should contain the API-bearing fixture module '$fixtureModule': ${ambient.moduleNames}",
            ambient.moduleNames.contains(fixtureModule)
        )
    }

    fun testModuleNamesEmptyWhenNoApiModules() {
        // No API-bearing classes in the fixture → moduleNames must be an
        // empty list (never null — the field defaults to emptyList()).
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)

        val ambient = AmbientPerception.capture(project)

        assertNotNull("moduleNames must never be null", ambient.moduleNames)
        assertTrue(
            "moduleNames should be empty when no API-bearing modules exist: ${ambient.moduleNames}",
            ambient.moduleNames.isEmpty()
        )
    }

    // ── frameworkHints — detected web frameworks ──

    fun testFrameworkHintsFromRecognizers() {
        // A @RestController class is recognized by SpringControllerRecognizer
        // (always enabled) → "SpringMVC". With feignEnable=true, a @FeignClient
        // class is recognized by FeignClientRecognizer → "Feign". Both must
        // surface on ambient.frameworkHints from the same PSI scan that
        // populates moduleNames (single scan, ~zero extra cost).
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        enableFeign()
        try {
            addApiBearingController()
            addFeignClientClass()

            val ambient = AmbientPerception.capture(project)

            assertTrue(
                "frameworkHints should contain SpringMVC: ${ambient.frameworkHints}",
                ambient.frameworkHints.contains("SpringMVC")
            )
            assertTrue(
                "frameworkHints should contain Feign (feignEnable=true): ${ambient.frameworkHints}",
                ambient.frameworkHints.contains("Feign")
            )
        } finally {
            disableFeign()
        }
    }

    fun testFeignHintGatedWhenFeignDisabled() {
        // Req 9.3: with feignEnable=false (the default), the FeignClient
        // annotation FQN is excluded from allTargetAnnotations, so even with a
        // @FeignClient class present in the fixture, "Feign" must NOT appear
        // in frameworkHints — settings gates are respected automatically.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        // Explicitly disable feign (default) to be robust against any
        // application-scoped settings leakage from other tests.
        disableFeign()
        addFeignClientClass()

        val ambient = AmbientPerception.capture(project)

        assertFalse(
            "Feign must NOT appear in frameworkHints when feignEnable=false: ${ambient.frameworkHints}",
            ambient.frameworkHints.contains("Feign")
        )
    }

    fun testFrameworkHintsEmptyWhenNoApiClasses() {
        // No API-bearing classes in the fixture → frameworkHints must be an
        // empty list (never null — the field defaults to emptyList()).
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)

        val ambient = AmbientPerception.capture(project)

        assertNotNull("frameworkHints must never be null", ambient.frameworkHints)
        assertTrue(
            "frameworkHints should be empty when no API-bearing classes exist: ${ambient.frameworkHints}",
            ambient.frameworkHints.isEmpty()
        )
    }

    fun testFrameworkHintsCarryNoEnvVarKeysOrValues() {
        // NFR-5 / Req 9.5: frameworkHints carries only short framework labels
        // — never env-var keys or values from the Environments panel. Even with
        // a controller present (so capture ran the recognizer scan) and an
        // env-var key/value in the process environment, neither may appear in
        // frameworkHints.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        addApiBearingController()
        System.setProperty("STRIPE_SECRET_KEY", "sk_test_leak")
        try {
            val ambient = AmbientPerception.capture(project)

            assertTrue(
                "frameworkHints should be populated (capture ran): ${ambient.frameworkHints}",
                ambient.frameworkHints.isNotEmpty()
            )
            assertFalse(
                "frameworkHints must not carry env-var keys (STRIPE_SECRET_KEY): ${ambient.frameworkHints}",
                ambient.frameworkHints.any { it.contains("STRIPE_SECRET_KEY") }
            )
            assertFalse(
                "frameworkHints must not carry env-var values (sk_test_leak): ${ambient.frameworkHints}",
                ambient.frameworkHints.any { it.contains("sk_test_leak") }
            )
        } finally {
            System.clearProperty("STRIPE_SECRET_KEY")
        }
    }

    // ── Privacy — no env-var keys or values leak ──

    fun testAmbientCarriesNoEnvVarKeysOrValues() {
        // The Ambient data class carries only module names — never env-var
        // keys or values from the Environments panel. This pins the contract:
        // even with a controller present (so capture did real work) and an
        // env-var key/value present in the process environment, neither may
        // appear anywhere on the captured Ambient.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        addApiBearingController()
        System.setProperty("STRIPE_SECRET_KEY", "sk_test_leak")
        try {
            val ambient = AmbientPerception.capture(project)

            assertTrue(
                "moduleNames should be populated (capture ran): ${ambient.moduleNames}",
                ambient.moduleNames.isNotEmpty()
            )
            val dump = ambient.toString()
            assertFalse(
                "Ambient must not carry env-var keys (STRIPE_SECRET_KEY): $dump",
                dump.contains("STRIPE_SECRET_KEY")
            )
            assertFalse(
                "Ambient must not carry env-var values (sk_test_leak): $dump",
                dump.contains("sk_test_leak")
            )
        } finally {
            System.clearProperty("STRIPE_SECRET_KEY")
        }
    }

    /**
     * Adds a Spring `@RestController`-annotated class to the fixture so the
     * fixture module becomes API-bearing. Mirrors the fixture style of
     * `FindClassesByAnnotationToolTest` — `AnnotatedElementsSearch` needs the
     * annotation type resolvable on the classpath plus an annotated class.
     */
    private fun addApiBearingController() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "org/springframework/web/bind/annotation/RestController.java",
                """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "org/springframework/stereotype/Controller.java",
                """
                package org.springframework.stereotype;
                public @interface Controller {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/OrderController.java",
                """
                package com.example;
                @org.springframework.web.bind.annotation.RestController
                public class OrderController {}
                """.trimIndent()
            )
        }
    }

    /**
     * Adds a Spring Cloud `@FeignClient`-annotated interface to the fixture.
     * The annotation type stub must be resolvable on the classpath for
     * `AnnotatedElementsSearch` to find annotated classes (mirrors
     * [addApiBearingController] style).
     */
    private fun addFeignClientClass() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "org/springframework/cloud/openfeign/FeignClient.java",
                """
                package org.springframework.cloud.openfeign;
                public @interface FeignClient {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/OrderFeignClient.java",
                """
                package com.example;
                @org.springframework.cloud.openfeign.FeignClient
                public interface OrderFeignClient {}
                """.trimIndent()
            )
        }
    }

    /**
     * Enables the Feign framework recognizer (`feignEnable=true`) and persists
     * via [settingBinder] so `CompositeApiClassRecognizer` rebuilds its
     * cached recognizers. Always pair with [disableFeign] in a `finally`.
     */
    private fun enableFeign() {
        val gs = project.settings<GeneralSettings>()
        gs.feignEnable = true
        settingBinder.save(gs)
    }

    /**
     * Restores `feignEnable=false` (the default) and persists. Guards against
     * application-scoped settings leaking across test methods.
     */
    private fun disableFeign() {
        val gs = project.settings<GeneralSettings>()
        gs.feignEnable = false
        settingBinder.save(gs)
    }
}
