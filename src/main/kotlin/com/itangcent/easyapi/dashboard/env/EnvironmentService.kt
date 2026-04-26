package com.itangcent.easyapi.dashboard.env

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.GsonUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Project-level service that manages [Environment] instances and their lifecycle.
 *
 * Environments are loaded from two persistence locations:
 * - **Project-scoped** environments are stored in project-level settings
 * - **Global-scoped** environments are stored in application-level settings
 *
 * Both are merged at load time, with global environments appearing first in the list.
 * The service exposes a [StateFlow] so UI components can reactively observe changes.
 *
 * Usage:
 * ```kotlin
 * val envService = EnvironmentService.getInstance(project)
 * envService.setActiveEnvironment("staging")
 * val baseUrl = envService.resolveVariable("base_url")
 * ```
 */
@Service(Service.Level.PROJECT)
class EnvironmentService(private val project: Project) {

    private val _environmentData = MutableStateFlow(loadEnvironmentData())

    /** Reactive state flow of the current environment data. UI components can collect this. */
    val environmentData: StateFlow<EnvironmentData> = _environmentData

    /** Returns all environments (global + project). */
    fun getEnvironments(): List<Environment> = _environmentData.value.environments

    /** Returns the currently active environment, or null if none is selected. */
    fun getActiveEnvironment(): Environment? = _environmentData.value.activeEnvironment()

    /** Returns the name of the currently active environment, or null. */
    fun getActiveEnvironmentName(): String? = _environmentData.value.activeEnvironmentName

    /**
     * Sets the active environment by name.
     *
     * @param name The environment name to activate, or null to deselect
     */
    fun setActiveEnvironment(name: String?) {
        val data = _environmentData.value
        val newData = data.copy(activeEnvironmentName = name)
        _environmentData.value = newData
        persistEnvironmentData(newData)
    }

    /**
     * Adds or replaces an environment. If an environment with the same name already exists,
     * it is replaced; otherwise the new environment is appended.
     *
     * @param environment The environment to add or update
     */
    fun addEnvironment(environment: Environment) {
        val data = _environmentData.value
        val existing = data.environments.toMutableList()
        val idx = existing.indexOfFirst { it.name == environment.name }
        if (idx >= 0) {
            existing[idx] = environment
        } else {
            existing.add(environment)
        }
        val newData = data.copy(environments = existing)
        _environmentData.value = newData
        persistEnvironmentData(newData)
    }

    /**
     * Removes an environment by name. If the removed environment was active,
     * the active selection is cleared.
     *
     * @param name The name of the environment to remove
     */
    fun removeEnvironment(name: String) {
        val data = _environmentData.value
        val existing = data.environments.filter { it.name != name }
        val activeName = if (data.activeEnvironmentName == name) null else data.activeEnvironmentName
        val newData = data.copy(environments = existing, activeEnvironmentName = activeName)
        _environmentData.value = newData
        persistEnvironmentData(newData)
    }

    /**
     * Updates an existing environment identified by [name] with new data.
     * If the environment's name changes, the active selection follows the rename.
     *
     * @param name The current name of the environment to update
     * @param environment The new environment data
     */
    fun updateEnvironment(name: String, environment: Environment) {
        val data = _environmentData.value
        val existing = data.environments.toMutableList()
        val idx = existing.indexOfFirst { it.name == name }
        if (idx >= 0) {
            existing[idx] = environment
        }
        val activeName = if (data.activeEnvironmentName == name) environment.name else data.activeEnvironmentName
        val newData = data.copy(environments = existing, activeEnvironmentName = activeName)
        _environmentData.value = newData
        persistEnvironmentData(newData)
    }

    /**
     * Resolves a single variable from the active environment.
     *
     * @param name The variable key
     * @return The variable value, or null if not found
     */
    fun resolveVariable(name: String): String? {
        return _environmentData.value.resolveVariable(name)
    }

    /**
     * Returns all variables from the active environment.
     *
     * @return A map of variable key-value pairs, or empty map if no environment is active
     */
    fun resolveAllVariables(): Map<String, String> {
        return _environmentData.value.resolveAllVariables()
    }

    /**
     * Sets a single variable in the active environment.
     *
     * If the active environment exists, the variable is added or updated and the change
     * is persisted immediately. If no environment is active, this is a no-op.
     *
     * @param name The variable key
     * @param value The variable value
     */
    fun setVariable(name: String, value: String) {
        val data = _environmentData.value
        val activeEnv = data.activeEnvironment() ?: return
        val updatedVars = activeEnv.variables.toMutableMap()
        updatedVars[name] = value
        val updatedEnv = activeEnv.copy(variables = updatedVars)
        updateEnvironment(activeEnv.name, updatedEnv)
    }

    /**
     * Removes a single variable from the active environment.
     *
     * If the active environment exists and contains the variable, it is removed and the
     * change is persisted immediately. If no environment is active or the variable doesn't
     * exist, this is a no-op.
     *
     * @param name The variable key to remove
     */
    fun unsetVariable(name: String) {
        val data = _environmentData.value
        val activeEnv = data.activeEnvironment() ?: return
        if (!activeEnv.variables.containsKey(name)) return
        val updatedVars = activeEnv.variables.toMutableMap()
        updatedVars.remove(name)
        val updatedEnv = activeEnv.copy(variables = updatedVars)
        updateEnvironment(activeEnv.name, updatedEnv)
    }

    /**
     * Loads environment data from settings, merging global and project-scoped environments.
     * Global environments are listed first, followed by project environments.
     */
    private fun loadEnvironmentData(): EnvironmentData {
        val settings = SettingBinder.getInstance(project).read()
        val projectEnvJson = settings.projectEnvironments
        val projectData = if (projectEnvJson.isNotBlank()) {
            runCatching { GsonUtils.fromJson<EnvironmentData>(projectEnvJson) }.getOrNull()
        } else null

        val globalEnvJson = settings.globalEnvironments
        val globalData = if (globalEnvJson.isNotBlank()) {
            runCatching { GsonUtils.fromJson<EnvironmentData>(globalEnvJson) }.getOrNull()
        } else null

        val projectEnvs = projectData?.environments ?: emptyList()
        val globalEnvs = globalData?.environments ?: emptyList()

        val allEnvs = globalEnvs + projectEnvs

        val activeName = projectData?.activeEnvironmentName
            ?: globalData?.activeEnvironmentName
            ?: allEnvs.firstOrNull()?.name

        return EnvironmentData(
            environments = allEnvs,
            activeEnvironmentName = activeName
        )
    }

    /**
     * Persists the current environment data back to settings, splitting by scope.
     * Project-scoped environments go to project settings; global-scoped go to application settings.
     */
    private fun persistEnvironmentData(data: EnvironmentData) {
        val settings = SettingBinder.getInstance(project).read()
        val projectEnvs = data.environments.filter { it.scope == EnvironmentScope.PROJECT }
        val globalEnvs = data.environments.filter { it.scope == EnvironmentScope.GLOBAL }

        settings.projectEnvironments = if (projectEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = projectEnvs,
                activeEnvironmentName = if (data.activeEnvironmentName in projectEnvs.map { it.name }) data.activeEnvironmentName else null
            ))
        } else ""

        settings.globalEnvironments = if (globalEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = globalEnvs,
                activeEnvironmentName = if (data.activeEnvironmentName in globalEnvs.map { it.name }) data.activeEnvironmentName else null
            ))
        } else ""

        SettingBinder.getInstance(project).save(settings)
    }

    companion object {
        /** Returns the [EnvironmentService] instance for the given project. */
        fun getInstance(project: Project): EnvironmentService = project.service()
    }
}
