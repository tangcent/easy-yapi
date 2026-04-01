package com.itangcent.easyapi.ide.script

import javax.script.ScriptEngineManager

/**
 * Interface for script language support in the plugin.
 * 
 * Provides abstraction for different scripting languages (Groovy, etc.)
 * that can be used for custom rules and configurations.
 */
interface ScriptSupport {
    /**
     * Builds a complete script from the input.
     * May add language prefix if needed.
     * 
     * @param script The raw script content
     * @return The formatted script ready for execution
     */
    fun buildScript(script: String): String
    
    /**
     * Builds a property expression from the input.
     * Used for simple value access in rules.
     * 
     * @param script The property expression
     * @return The formatted property expression
     */
    fun buildProperty(script: String): String
    
    /**
     * Checks if this script type is supported in the current environment.
     * 
     * @return true if the script engine is available
     */
    fun checkSupport(): Boolean
    
    /**
     * Returns the file suffix for this script type.
     * 
     * @return File extension without dot (e.g., "groovy")
     */
    fun suffix(): String
    
    /**
     * Returns demo code for this script type.
     * Used in the script executor dialog.
     * 
     * @return Example script code
     */
    fun demoCode(): String
}

/**
 * General script support for plain text/annotations.
 * Does not use any scripting engine, just passes through the input.
 */
object GeneralScriptSupport : ScriptSupport {
    override fun demoCode(): String = "@org.springframework.web.bind.annotation.RequestMapping"

    override fun buildScript(script: String): String = script

    override fun buildProperty(script: String): String = script

    override fun checkSupport(): Boolean = true

    override fun suffix(): String = "txt"

    override fun toString(): String = "General"

    override fun equals(other: Any?): Boolean = this === other
}

/**
 * Abstract base class for script support implementations.
 * Provides common functionality for script engines.
 */
abstract class AbstractScriptSupport : ScriptSupport {
    /**
     * Builds a script with the language prefix.
     * Format: "prefix:script"
     */
    override fun buildScript(script: String): String = "${prefix()}:$script"

    /**
     * Builds a property expression wrapped in code block.
     * Format: "prefix:```\nscript\n```"
     */
    override fun buildProperty(script: String): String = "${prefix()}:```\n$script\n```"

    /**
     * Returns the prefix for this script type.
     * Default implementation uses the script type name.
     */
    open fun prefix(): String = scriptType()

    /**
     * Returns the script engine name.
     */
    abstract fun scriptType(): String

    /**
     * Checks if the script engine is available.
     */
    override fun checkSupport(): Boolean {
        val manager = ScriptEngineManager()
        return manager.getEngineByName(scriptType()) != null
    }
}

/**
 * Groovy script support implementation.
 * Provides Groovy language integration for custom rules.
 */
object GroovyScriptSupport : AbstractScriptSupport() {
    override fun suffix(): String = "groovy"

    override fun scriptType(): String = "groovy"

    override fun toString(): String = "Groovy"

    override fun equals(other: Any?): Boolean = this === other

    override fun demoCode(): String = """
def separator = tool.repeat("-", 35) + "\n\n"
def sb = ""
def variables = [
    tool        : tool, it: it, regex: regex,
    logger      : logger, helper: helper, httpClient: httpClient,
    session     : session, localStorage: localStorage, config: config, files: files
]
variables.each { key, value ->
    sb += "debug `" + key + "`:\n"
    sb += tool.debug(value)
    sb += separator
}
return sb
""".trimIndent()
}

/** List of all available script support implementations */
val scriptSupports: List<ScriptSupport> = listOf(
    GeneralScriptSupport,
    GroovyScriptSupport
)
