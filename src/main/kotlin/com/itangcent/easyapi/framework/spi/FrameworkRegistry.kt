package com.itangcent.easyapi.framework.spi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.read

/**
 * Project-level service that resolves the effective enabled state of every
 * registered [ApiClassRecognizer] — the single chokepoint for framework
 * enablement (PR4).
 *
 * Mirrors [com.itangcent.easyapi.channel.spi.ChannelRegistry] structurally:
 * a pure [resolveEnabled] companion rule (testable without a [Project]),
 * a [isEnabled] primary entry point that overlays the stored user preference
 * on [ApiClassRecognizer.enabledByDefault], and a convenience overload that
 * takes a framework id string (used by exporters that don't hold a
 * recognizer instance).
 *
 * ## Why this lives in `framework/spi/`
 *
 * Per the broadened CO3 rule (Decision PR4 / Spec Reconciliation PR10), `core.*`
 * may import the `framework.spi.*` contract surface — mirroring the existing
 * allowance for `channel.spi.*` and `format.spi.*`. Concrete framework
 * implementations (`framework.<id>.*`) remain off-limits to `core.*`.
 *
 * ## Settings storage
 *
 * The user preference is stored as two arrays in [GeneralSettings]:
 * - [GeneralSettings.enabledFrameworks] — explicit-on overrides (wins on conflict)
 * - [GeneralSettings.disabledFrameworks] — explicit-off overrides
 *
 * A framework id is the recognizer's [ApiClassRecognizer.frameworkName]
 * (PR5 — reuse, no new SPI member).
 *
 * ## Fallback
 *
 * If [SettingBinder] cannot read [GeneralSettings] (e.g. storage not yet
 * initialized during startup), [isEnabled] falls back to
 * [ApiClassRecognizer.enabledByDefault] and does not throw (mirrors
 * [com.itangcent.easyapi.channel.spi.ChannelRegistry.isEnabled] Req 2.6).
 *
 * @see ApiClassRecognizer
 * @see CompositeApiClassRecognizer
 * @see com.itangcent.easyapi.channel.spi.ChannelRegistry
 */
@Service(Service.Level.PROJECT)
class FrameworkRegistry(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): FrameworkRegistry = project.service()

        /**
         * Pure resolution rule for a recognizer's effective enabled state,
         * extracted so unit tests can exercise the full truth table without
         * a [Project] (test: `FrameworkRegistryTest`).
         *
         * `true` if [recognizer]'s [ApiClassRecognizer.frameworkName] is
         * explicitly in [enabledIds] (explicit-on wins), OR [recognizer] is
         * [ApiClassRecognizer.enabledByDefault] and its frameworkName is not
         * explicitly in [disabledIds]. Absence in both arrays falls back to
         * [ApiClassRecognizer.enabledByDefault].
         *
         * Mirrors [com.itangcent.easyapi.channel.spi.ChannelRegistry.resolveEnabled].
         */
        internal fun resolveEnabled(
            recognizer: ApiClassRecognizer,
            enabledIds: Array<String>,
            disabledIds: Array<String>
        ): Boolean {
            val id = recognizer.frameworkName
            return id in enabledIds ||
                (recognizer.enabledByDefault && id !in disabledIds)
        }
    }

    /**
     * Resolves the effective enabled state of [recognizer]: `true` if the user
     * has explicitly enabled it (its frameworkName is in
     * [GeneralSettings.enabledFrameworks]), OR it is
     * [ApiClassRecognizer.enabledByDefault] and not explicitly disabled (its
     * frameworkName is not in [GeneralSettings.disabledFrameworks]). Reads the
     * stored preference via [SettingBinder] (cached ~10s by
     * [com.itangcent.easyapi.core.settings.DefaultSettingBinder]).
     *
     * If the settings storage cannot be read, falls back to
     * [ApiClassRecognizer.enabledByDefault] for the recognizer and does not
     * throw (Req 2.6 — mirrors ChannelRegistry fallback).
     */
    fun isEnabled(recognizer: ApiClassRecognizer): Boolean {
        val settings = try {
            SettingBinder.getInstance(project).read<GeneralSettings>()
        } catch (e: Exception) {
            LOG.warn(
                "Failed to read framework enablement settings, " +
                    "falling back to enabledByDefault for ${recognizer.frameworkName}",
                e
            )
            return recognizer.enabledByDefault
        }
        return resolveEnabled(recognizer, settings.enabledFrameworks, settings.disabledFrameworks)
    }

    /**
     * Convenience overload for callers (e.g. framework exporters) that know
     * the framework id but don't hold a recognizer instance.
     *
     * Looks up the recognizer via [CompositeApiClassRecognizer.recognizers]
     * (the EP-discovered, settings-filtered cache) and delegates to
     * [isEnabled]. Returns `false` if no recognizer with [frameworkId] is
     * registered (which is also the correct answer when the framework is
     * disabled — its recognizer is filtered out of the cache).
     *
     * PR5: [frameworkId] must match [ApiClassRecognizer.frameworkName].
     */
    fun isEnabled(frameworkId: String): Boolean {
        val recognizer = CompositeApiClassRecognizer.getInstance(project).recognizers()
            .firstOrNull { it.frameworkName == frameworkId } ?: return false
        return isEnabled(recognizer)
    }
}
