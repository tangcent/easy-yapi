package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.yapi.model.TokenValidationResult
import com.itangcent.easyapi.settings.SettingBinder
import java.io.ByteArrayOutputStream
import java.util.Properties

interface YapiSettingsHelper {

    suspend fun resolveServerUrl(dumb: Boolean = false): String?

    suspend fun resolveToken(module: String, validator: suspend (String) -> TokenValidationResult): String?

    fun resetPromptedModules()

    companion object {
        fun getInstance(project: Project): YapiSettingsHelper = project.service()
    }
}

@Service(Service.Level.PROJECT)
class DefaultYapiSettingsHelper(private val project: Project) : YapiSettingsHelper {

    private val promptedModules = mutableSetOf<String>()
    private val settingBinder: SettingBinder by lazy {
        SettingBinder.getInstance(project)
    }

    override suspend fun resolveServerUrl(dumb: Boolean): String? {
        val settings = settingBinder.read()
        val configuredServer = settings.yapiServer
            ?.let(YapiUrls::normalizeBaseUrl)
            ?.takeIf { it.isNotBlank() }

        if (configuredServer != null || dumb) {
            return configuredServer
        }

        val inputServer = swing {
            Messages.showInputDialog(
                project,
                "Input server of yapi",
                "Server Of Yapi",
                Messages.getInformationIcon(),
                null,
                null
            )
        }
            ?.let(YapiUrls::normalizeBaseUrl)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        settingBinder.save(settings.copy(yapiServer = inputServer))
        return inputServer
    }

    override suspend fun resolveToken(
        module: String,
        validator: suspend (String) -> TokenValidationResult
    ): String? {
        val token = getTokenForModule(module)

        if (token.isNullOrBlank()) return null

        val validationResult = validator(token)
        if (validationResult.isValid) return token

        val errorMessage = if (validationResult is TokenValidationResult.Failed) {
            "Token for module '$module' is invalid.\n\nReason: ${validationResult.reason}\n\nPlease input a new Private Token:"
        } else {
            "Token for module '$module' is invalid.\n\nPlease input a new Private Token:"
        }

        val newToken = swing {
            Messages.showInputDialog(
                project,
                errorMessage,
                "Yapi Private Token",
                Messages.getWarningIcon(),
                null,
                null
            )
        }
        if (newToken.isNullOrBlank()) return null

        val newValidationResult = validator(newToken)
        if (newValidationResult.isValid) {
            setToken(module, newToken)
            return newToken
        }

        // Show error for the new token as well
        if (newValidationResult is TokenValidationResult.Failed) {
            swing {
                Messages.showErrorDialog(
                    project,
                    "Token validation failed:\n\n${newValidationResult.reason}",
                    "Invalid Token"
                )
            }
        }

        return null
    }

    override fun resetPromptedModules() {
        promptedModules.clear()
    }

    private suspend fun getTokenForModule(module: String, prompt: Boolean = true): String? {
        val settings = settingBinder.read()

        val tokens = parseTokens(settings.yapiTokens)
        tokens[module]?.takeIf { it.isNotBlank() }?.let { return it }

        if (prompt && promptedModules.add(module)) {
            val token = swing {
                Messages.showInputDialog(
                    project,
                    "Input Private Token of module: $module",
                    "Yapi Private Token",
                    Messages.getInformationIcon(),
                    null,
                    null
                )
            }
            if (!token.isNullOrBlank()) {
                setToken(module, token)
                return token
            }
        }

        val raw = settings.yapiTokens
        if (!raw.isNullOrBlank() && !raw.contains('=')) {
            return raw
        }

        return null
    }

    private fun setToken(module: String, token: String) {
        val settings = settingBinder.read()
        val properties = Properties()
        settings.yapiTokens?.takeIf { it.isNotBlank() }?.let {
            runCatching { properties.load(it.byteInputStream()) }
        }
        properties[module] = token
        val newTokens = serializeTokens(properties)
        settingBinder.save(settings.copy(yapiTokens = newTokens))
    }

    private fun parseTokens(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()

        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val module = line.substringBefore("=").trim()
                val token = line.substringAfter("=").trim()
                module to token
            }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotBlank() }
    }

    private fun serializeTokens(properties: Properties): String {
        val raw = ByteArrayOutputStream().also { properties.store(it, null) }.toString()
        return raw.lineSequence()
            .filter { !it.startsWith('#') }
            .joinToString("\n")
            .trim()
    }
}
