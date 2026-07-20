package com.itangcent.easyapi.framework.spi

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure unit tests for [FrameworkRegistry.resolveEnabled] — the resolution
 * rule that overlays a stored user preference on a recognizer's
 * [ApiClassRecognizer.enabledByDefault]. Mirrors the structural twin
 * [com.itangcent.easyapi.channel.spi.ChannelRegistry.resolveEnabled]
 * (test: `ChannelRegistryTest`).
 *
 * Cases per `requirements-framework-enablement.md` Req 7.6 (a)-(e):
 *  (a) default-on framework, absent from both arrays → `true`;
 *  (b) default-on framework, in `disabledFrameworks` → `false`;
 *  (c) default-off framework, absent from both arrays → `false`;
 *  (d) default-off framework, in `enabledFrameworks` → `true`;
 *  (e) framework id in BOTH arrays (explicit-on wins) → `true`.
 *
 * Decision PR5: the framework id is `recognizer.frameworkName`.
 *
 * This is a **test-first** test (written BEFORE task 19 implements
 * [FrameworkRegistry]). It MUST fail to compile against current code
 * because [FrameworkRegistry] does not exist yet.
 *
 * No `Project` is needed — [FrameworkRegistry.resolveEnabled] is the pure
 * `internal` companion rule extracted for testability.
 *
 * Requirements: Framework Enablement 3.1, 7.6; Decision: PR4, PR5
 */
class FrameworkRegistryTest {

    /**
     * Minimal [ApiClassRecognizer] stub — only [frameworkName] and
     * [enabledByDefault] are consulted by [FrameworkRegistry.resolveEnabled].
     */
    private class StubRecognizer(
        override val frameworkName: String,
        override val enabledByDefault: Boolean
    ) : ApiClassRecognizer {
        override val targetAnnotations: Set<String> = emptySet()
        override suspend fun isApiClass(psiClass: PsiClass): Boolean = false
        override fun matchesClass(psiClass: PsiClass): Boolean = false
    }

    private val defaultOn = StubRecognizer(frameworkName = "default-on", enabledByDefault = true)
    private val defaultOff = StubRecognizer(frameworkName = "default-off", enabledByDefault = false)
    private val empty = emptyArray<String>()

    // --- (a) default-on, absent from both arrays → true ---

    @Test
    fun testResolveEnabled_defaultOnNoPreference_isEnabled() {
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOn, empty, empty))
    }

    // --- (b) default-on, in disabledFrameworks → false ---

    @Test
    fun testResolveEnabled_defaultOnInDisabled_isDisabled() {
        assertFalse(FrameworkRegistry.resolveEnabled(defaultOn, empty, arrayOf("default-on")))
    }

    // --- (c) default-off, absent from both arrays → false ---

    @Test
    fun testResolveEnabled_defaultOffNoPreference_isDisabled() {
        assertFalse(FrameworkRegistry.resolveEnabled(defaultOff, empty, empty))
    }

    // --- (d) default-off, in enabledFrameworks → true ---

    @Test
    fun testResolveEnabled_defaultOffInEnabled_isEnabled() {
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), empty))
    }

    // --- (e) id in BOTH arrays → explicit-on wins ---

    @Test
    fun testResolveEnabled_idInBothEnabledAndDisabled_explicitOnWins() {
        // Mirror ChannelRegistry.resolveEnabled line 66: `channel.id in enabledIds`
        // short-circuits to true regardless of disabledIds.
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), arrayOf("default-on")))
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), arrayOf("default-off")))
    }

    // --- supplementary: default-on in enabledFrameworks is redundant but legal ---

    @Test
    fun testResolveEnabled_defaultOnInEnabled_isEnabled() {
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), empty))
    }

    // --- supplementary: default-off in disabledFrameworks is redundant but legal ---

    @Test
    fun testResolveEnabled_defaultOffInDisabled_isDisabled() {
        assertFalse(FrameworkRegistry.resolveEnabled(defaultOff, empty, arrayOf("default-off")))
    }

    // --- supplementary: arrays for OTHER framework ids do not affect this recognizer ---

    @Test
    fun testResolveEnabled_unrelatedEntriesInArrays_doNotAffectResolution() {
        // A default-on recognizer stays enabled even if other ids appear in disabledFrameworks.
        assertTrue(FrameworkRegistry.resolveEnabled(defaultOn, arrayOf("unrelated"), arrayOf("unrelated")))
        // A default-off recognizer stays disabled even if other ids appear in enabledFrameworks.
        assertFalse(FrameworkRegistry.resolveEnabled(defaultOff, arrayOf("unrelated"), arrayOf("unrelated")))
    }
}
