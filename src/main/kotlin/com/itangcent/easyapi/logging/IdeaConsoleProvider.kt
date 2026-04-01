package com.itangcent.easyapi.logging

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.SettingBinder

/**
 * Provides a project-level [IdeaConsole] instance for logging within the IDE.
 *
 * This is an IntelliJ project-level service that creates and manages a [ConfigurableIdeaConsole]
 * which wraps a [DefaultIdeaConsole] with settings from [DefaultSettingBinder].
 *
 * The console instance is lazily initialized on first access and cached for subsequent calls.
 *
 * ## Usage
 * ```kotlin
 * val console = IdeaConsoleProvider.getInstance(project).getConsole()
 * console.info("Message")
 * ```
 *
 * @see IdeaConsole for the console interface
 * @see ConfigurableIdeaConsole for settings-aware console wrapper
 * @see DefaultIdeaConsole for the default implementation
 */
@Service(Service.Level.PROJECT)
class IdeaConsoleProvider(private val project: Project) {

    private val console by lazy {
        val settings = SettingBinder.getInstance(project).read()
        val delegate = DefaultIdeaConsole(project)
        ConfigurableIdeaConsole(delegate, settings)
    }

    /**
     * Returns the project's console instance for logging.
     *
     * The console is lazily initialized on first call and reused for subsequent calls.
     *
     * @return The [IdeaConsole] instance for this project
     */
    fun getConsole(): IdeaConsole = console

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
