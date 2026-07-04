package com.itangcent.easyapi.logging

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.settings.onSettingsChanged

/**
 * Provides a project-level [IdeaConsole] instance for logging within the IDE.
 *
 * This is an IntelliJ project-level service that resolves the appropriate [IdeaConsole]
 * implementation based on the current [Settings.logLevel]:
 *
 * - **`logLevel > ERROR` (e.g. SILENT=100, the default):** returns [IdeaLogConsole] —
 *   the tool window is bypassed and console output is redirected to `idea.log`.
 *   All non-error levels (`trace`/`debug`/`info`) are floored to `LOG.info` so they
 *   remain visible (IntelliJ filters `debug`/`trace` out of `idea.log` by default);
 *   `warn`/`error` are routed to `LOG.warn` (always captured).
 * - **`logLevel <= ERROR`:** returns a [ConfigurableIdeaConsole] wrapping a
 *   [DefaultIdeaConsole] — output appears in the EasyAPI tool window, filtered by the
 *   configured log level, with `warn`/`error` mirrored to `idea.log`.
 *
 * ## Usage
 *
 * Prefer the `Project.console` extension property over calling this service directly:
 * ```kotlin
 * project.console.warn("Message", throwable)
 * ```
 *
 * @see IdeaConsole for the console interface
 * @see ConfigurableIdeaConsole for settings-aware console wrapper
 * @see IdeaLogConsole for the idea.log-only fallback
 * @see DefaultIdeaConsole for the default tool-window implementation
 */
@Service(Service.Level.PROJECT)
class IdeaConsoleProvider(private val project: Project) {

    private val ideaConsole: IdeaConsole by lazy {
        val delegate = DefaultIdeaConsole(project)
        ConfigurableIdeaConsole(delegate, project.settings<GeneralSettings>().logLevel)
    }

    /**
     * Returns the project's console instance for logging.
     *
     * The implementation is chosen dynamically based on [Settings.logLevel]:
     * - `logLevel > ERROR.threshold` → [IdeaLogConsole] (idea.log only, no tool window)
     * - otherwise → [ConfigurableIdeaConsole] (tool window + mirror)
     *
     * @return The [IdeaConsole] instance for this project
     */
    fun getConsole(): IdeaConsole {
        return if (project.settings<GeneralSettings>().logLevel > LogLevel.ERROR.threshold) {
            IdeaLogConsole
        } else {
            ideaConsole
        }
    }

    companion object {
        /**
         * Returns the [IdeaConsoleProvider] service instance for the given project.
         *
         * @param project The IntelliJ project
         * @return The [IdeaConsoleProvider] instance
         */
        fun getInstance(project: Project): IdeaConsoleProvider = project.service()
    }
}

/**
 * Returns the project's [IdeaConsole] for logging.
 *
 * Resolves the console implementation based on the current `logLevel` setting.
 * Use this instead of `IdeaConsoleProvider.getInstance(project).getConsole()`.
 *
 * ```kotlin
 * project.console.warn("Export failed", e)
 * ```
 */
val Project.console
    get() = IdeaConsoleProvider.getInstance(this).getConsole()
