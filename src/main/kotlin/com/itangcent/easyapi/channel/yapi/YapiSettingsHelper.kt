package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.channel.yapi.YapiSettings
import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import java.io.ByteArrayOutputStream
import java.util.Properties

interface YapiSettingsHelper {

    suspend fun resolveServerUrl(dumb: Boolean = false): String?

    /**
     * Resolves the token for [module], prompting the user for a replacement when the server
     * rejects it. Returns null without prompting when no token is configured for the module.
     *
     * @param validator checks a candidate token against the server. A failure result must carry the
     *   server's own rejection text, because that is what tells the user what to go and fetch;
     *   [YapiResponse.failure] guarantees that message is present on every well-formed failure.
     * @return the configured token or an accepted replacement; null if no token is configured,
     *   the user cancels the prompt, or the replacement is also rejected
     */
    suspend fun resolveToken(module: String, validator: suspend (String) -> YapiResponse<*>): String?

    fun resetPromptedModules()

    companion object {
        fun getInstance(project: Project): YapiSettingsHelper = project.service()
    }
}

@Service(Service.Level.PROJECT)
class DefaultYapiSettingsHelper(private val project: Project) : YapiSettingsHelper {

    private val promptedModules = mutableSetOf<String>()

    override suspend fun resolveServerUrl(dumb: Boolean): String? {
        val configuredServer = project.settings<YapiSettings>().yapiServer
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

        SettingBinder.getInstance(project).update(YapiSettings::class) {
            yapiServer = inputServer
        }
        return inputServer
    }

    override suspend fun resolveToken(
        module: String,
        validator: suspend (String) -> YapiResponse<*>
    ): String? {
        val token = getTokenForModule(module)

        if (token.isNullOrBlank()) return null

        val probe = validator(token)
        if (probe.isSuccess) return token

        // failure() guarantees a message; the fallback only covers a hand-built failure.
        val rejection = probe.errorMessage() ?: "invalid token"

        project.console.warn("YApi: token rejected for module '$module': $rejection")
        val newToken = swing {
            Messages.showInputDialog(
                project,
                "Token for module '$module' is invalid:\n" +
                    "$rejection\n" +
                    "\nPlease input a new Private Token:",
                "Yapi Private Token",
                Messages.getWarningIcon(),
                null,
                null
            )
        }
        if (newToken.isNullOrBlank()) return null

        val newProbe = validator(newToken)
        if (newProbe.isSuccess) {
            setToken(module, newToken)
            return newToken
        }
        val newRejection = newProbe.errorMessage() ?: "invalid token"
        project.console.warn("YApi: replacement token rejected for module '$module': $newRejection")
        return null
    }

    override fun resetPromptedModules() {
        promptedModules.clear()
    }

    private suspend fun getTokenForModule(module: String, prompt: Boolean = true): String? {
        val tokens = parseTokens(project.settings<YapiSettings>().yapiTokens)
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

        val raw = project.settings<YapiSettings>().yapiTokens
        if (!raw.isNullOrBlank() && !raw.contains('=')) {
            return raw
        }

        return null
    }

    private fun setToken(module: String, token: String) {
        SettingBinder.getInstance(project).update(YapiSettings::class) {
            val properties = Properties()
            yapiTokens?.takeIf { it.isNotBlank() }?.let {
                runCatching { properties.load(it.byteInputStream()) }
            }
            properties[module] = token
            yapiTokens = serializeTokens(properties)
        }
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
