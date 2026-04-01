package com.itangcent.easyapi.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions

/**
 * Util operations for rule scripts.
 * Mirrors the legacy `com.itangcent.idea.plugin.rule.RuleToolUtils`.
 */
object RuleToolUtils {

    // ── any ──────────────────────────────────────────────────────────

    fun isNullOrEmpty(any: Any?): Boolean {
        return when (any) {
            null -> true
            is String -> any.isEmpty()
            is Collection<*> -> any.isEmpty()
            is Map<*, *> -> any.isEmpty()
            is Array<*> -> any.isEmpty()
            else -> any.toString().isEmpty()
        }
    }

    fun notNullOrEmpty(any: Any?): Boolean = !isNullOrEmpty(any)

    fun asArray(any: Any?): Array<*>? = when (any) {
        null -> null
        is Array<*> -> any
        is Collection<*> -> any.toTypedArray()
        else -> arrayOf(any)
    }

    fun asList(any: Any?): List<*>? = when (any) {
        null -> null
        is Array<*> -> any.toMutableList()
        is List<*> -> any
        is Collection<*> -> any.toMutableList()
        else -> listOf(any)
    }

    fun intersect(any: Any?, other: Any?): Array<*>? {
        val list = asList(other) ?: return null
        return asList(any)?.filter { list.contains(it) }?.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    fun anyIntersect(any: Any?, other: Any?): Boolean {
        val list = asList(other) ?: return false
        return asList(any)?.any { list.contains(it) } ?: false
    }

    fun equalOrIntersect(any: Any?, other: Any?): Boolean {
        if (any == other) return true
        val list = asList(other) ?: return false
        return asList(any)?.any { list.contains(it) } ?: false
    }

    // ── collections ─────────────────────────────────────────────────

    fun newSet(vararg items: Any): Set<*> = hashSetOf(*items)

    fun newList(vararg items: Any): List<*> = arrayListOf(*items)

    fun newMap(): Map<*, *> = LinkedHashMap<Any, Any>()

    // ── json ─────────────────────────────────────────────────────────

    fun parseJson(json: String?): Any? {
        if (json.isNullOrEmpty()) return null
        return GsonUtils.fromJson(json, Any::class.java)
    }

    fun toJson(obj: Any?): String? {
        if (obj == null) return null
        return GsonUtils.toJson(obj)
    }

    fun prettyJson(obj: Any?): String? {
        if (obj == null) return null
        return GsonUtils.prettyJson(obj)
    }

    // ── string ───────────────────────────────────────────────────────

    fun nullOrEmpty(str: String?): Boolean = str.isNullOrEmpty()

    fun nullOrBlank(str: String?): Boolean = str.isNullOrBlank()

    fun notNullOrEmpty(str: String?): Boolean = !str.isNullOrEmpty()

    fun notNullOrBlank(str: String?): Boolean = !str.isNullOrBlank()

    fun headLine(str: String?): String? {
        if (str == null) return null
        val idx = str.indexOf('\n')
        val line = if (idx >= 0) str.substring(0, idx) else str
        return line.trimEnd('\r').ifEmpty { null }
    }

    fun capitalize(str: String?): String? = str?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    fun uncapitalize(str: String?): String? = str?.replaceFirstChar { it.lowercase(Locale.getDefault()) }

    fun swapCase(str: String?): String? = str?.map {
        when {
            it.isUpperCase() -> it.lowercaseChar()
            it.isLowerCase() -> it.uppercaseChar()
            else -> it
        }
    }?.joinToString("")

    fun upperCase(str: String?): String? = str?.uppercase()

    fun lowerCase(str: String?): String? = str?.lowercase()

    fun reverse(str: String?): String? = str?.reversed()

    fun repeat(str: String?, repeat: Int): String? {
        if (str == null || repeat <= 0) return str ?: ""
        return str.repeat(repeat)
    }

    fun repeat(str: String?, separator: String, repeat: Int): String? {
        if (str == null) return null
        if (repeat <= 0) return ""
        return (1..repeat).joinToString(separator) { str }
    }

    fun isNumeric(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        return str.all { Character.isDigit(it) }
    }

    fun isAlpha(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        return str.all { Character.isLetter(it) }
    }

    fun substringBefore(str: String?, separator: String?): String? {
        if (str == null) return null
        if (separator == null) return str
        return str.substringBefore(separator)
    }

    fun substringAfter(str: String?, separator: String?): String? {
        if (str == null) return null
        if (separator == null) return ""
        return str.substringAfter(separator, "")
    }

    fun substringBeforeLast(str: String?, separator: String?): String? {
        if (str == null) return null
        if (separator.isNullOrEmpty()) return str
        return str.substringBeforeLast(separator)
    }

    fun substringAfterLast(str: String?, separator: String?): String? {
        if (str == null) return null
        if (separator.isNullOrEmpty()) return ""
        return str.substringAfterLast(separator, "")
    }

    fun substringBetween(str: String?, tag: String?): String? = substringBetween(str, tag, tag)

    fun substringBetween(str: String?, open: String?, close: String?): String? {
        if (str == null || open == null || close == null) return null
        val start = str.indexOf(open)
        if (start < 0) return null
        val end = str.indexOf(close, start + open.length)
        if (end < 0) return null
        return str.substring(start + open.length, end)
    }

    fun substringsBetween(str: String?, open: String?, close: String?): Array<String>? {
        if (str == null || open.isNullOrEmpty() || close.isNullOrEmpty()) return null
        val result = mutableListOf<String>()
        var pos = 0
        while (true) {
            val start = str.indexOf(open, pos)
            if (start < 0) break
            val end = str.indexOf(close, start + open.length)
            if (end < 0) break
            result.add(str.substring(start + open.length, end))
            pos = end + close.length
        }
        return result.toTypedArray()
    }

    fun split(str: String?): Array<String>? {
        if (str == null) return null
        return str.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.toTypedArray()
    }

    fun split(str: String?, separatorChars: String?): Array<String>? {
        if (str == null) return null
        if (separatorChars == null) return split(str)
        return str.split(*separatorChars.toCharArray().map { it.toString() }.toTypedArray())
            .filter { it.isNotEmpty() }.toTypedArray()
    }

    fun toCamelCase(str: String?, capitalizeFirstLetter: Boolean, vararg delimiters: Char): String? {
        if (str.isNullOrBlank()) return str
        val strLow = str.lowercase()
        val delimiterSet = if (delimiters.isEmpty()) setOf(' '.code) else delimiters.map { it.code }.toSet() + ' '.code
        val result = StringBuilder()
        var capitalizeNext = capitalizeFirstLetter
        var i = 0
        while (i < strLow.length) {
            val cp = strLow.codePointAt(i)
            if (delimiterSet.contains(cp)) {
                capitalizeNext = result.isNotEmpty()
                i += Character.charCount(cp)
            } else if (capitalizeNext || (result.isEmpty() && capitalizeFirstLetter)) {
                result.appendCodePoint(Character.toTitleCase(cp))
                i += Character.charCount(cp)
                capitalizeNext = false
            } else {
                result.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        return if (result.isNotEmpty()) result.toString() else strLow
    }

    private val TO_LINE_PATTERN = Pattern.compile("[A-Z]+")

    fun camel2Underline(str: String?): String? {
        if (str.isNullOrBlank()) return str
        val matcher = TO_LINE_PATTERN.matcher(str)
        val buffer = StringBuffer()
        while (matcher.find()) {
            if (matcher.start() > 0) {
                matcher.appendReplacement(buffer, "_" + matcher.group(0).lowercase())
            } else {
                matcher.appendReplacement(buffer, matcher.group(0).lowercase())
            }
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    fun removePrefix(str: String?, prefix: String?): String? = prefix?.let { str?.removePrefix(it) }

    fun removeSuffix(str: String?, suffix: String?): String? = suffix?.let { str?.removeSuffix(it) }

    // ── time & date ──────────────────────────────────────────────────

    fun now(): String = now(null)

    fun now(pattern: String?): String {
        return SimpleDateFormat(pattern ?: "yyyy-MM-dd HH:mm:ss").format(Date())
    }

    fun today(): String = now("yyyy-MM-dd")

    fun format(time: Long, pattern: String?): String? {
        return SimpleDateFormat(pattern ?: "yyyy-MM-dd HH:mm:ss").format(Date(time))
    }

    // ── clipboard ────────────────────────────────────────────────────

    fun copy2Clipboard(str: String) {
        val selection = java.awt.datatransfer.StringSelection(str)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    // ── debug ─────────────────────────────────────────────────────────

    fun debug(any: Any?): String {
        if (any == null) {
            return "debug object is null"
        }

        val kClass: KClass<out Any> = any::class
        val qualifiedName = kClass.qualifiedName ?: return "debug error"

        val sb = StringBuilder()
        sb.append("type: ")
            .append(typeName(kClass))
            .appendLine()

        val functions = kClass.functions
        if (functions.isNotEmpty()) {
            sb.append("methods:").appendLine()
            val seen = HashSet<String>()
            for (function in functions) {
                if (function.visibility != KVisibility.PUBLIC
                    || excludedMethods.contains(function.name)
                ) {
                    continue
                }

                val functionSb = StringBuilder()
                functionSb.append(typeName(function.returnType))
                    .append(" ")
                    .append(function.name)
                    .append("(")
                var appended = false
                for (param in function.parameters) {
                    if (param.kind != KParameter.Kind.VALUE) {
                        continue
                    }

                    if (appended) {
                        functionSb.append(", ")
                    } else {
                        appended = true
                    }

                    if (param.isVararg) {
                        val type = param.type.arguments.firstOrNull()?.type
                        if (type == null) {
                            functionSb.append(typeName(param.type))
                        } else {
                            functionSb.append(typeName(type))
                                .append("...")
                        }
                    } else {
                        functionSb.append(typeName(param.type))
                    }
                }
                functionSb.append(")")
                val functionStr = functionSb.toString()
                if (seen.add(functionStr)) {
                    sb.append(functionStr).appendLine()
                }
            }
        }

        return sb.toString()
    }

    private fun typeName(kType: kotlin.reflect.KType): String {
        val arguments = kType.arguments
        val classifier = kType.classifier
        if (arguments.isEmpty()) {
            return if (classifier is KClass<*>) {
                typeName(classifier)
            } else {
                typeName(classifier.toString())
            }
        } else {
            val sb = StringBuilder()
            sb.append(
                if (classifier is KClass<*>) {
                    typeName(classifier)
                } else {
                    typeName(classifier.toString())
                }
            )
            sb.append("<")
            sb.append(arguments.joinToString(separator = ", ") { argument ->
                argument.type?.let { typeName(it) } ?: "object"
            })
            sb.append(">")
            return sb.toString()
        }
    }

    private fun typeName(kClass: KClass<*>): String {
        val qualifiedName = kClass.qualifiedName ?: return "object"
        return typeName(qualifiedName)
    }

    private fun typeName(qualifiedName: String): String {
        return typeMapper[qualifiedName] ?: qualifiedName.substringAfterLast('.')
    }

    private val excludedMethods = listOf(
        "hashCode",
        "toString",
        "equals",
        "getClass",
        "clone",
        "notify",
        "notifyAll",
        "wait",
        "finalize",
        "component1",
        "component2",
        "component3",
        "component4",
        "component5",
        "copy"
    )

    private val typeMapper = linkedMapOf(
        "java.lang.String" to "string",
        "kotlin.String" to "string",
        "java.lang.Long" to "long",
        "kotlin.Long" to "long",
        "java.lang.Integer" to "int",
        "kotlin.Int" to "int",
        "java.lang.Double" to "double",
        "kotlin.Double" to "double",
        "java.lang.Float" to "float",
        "kotlin.Float" to "float",
        "java.lang.Boolean" to "boolean",
        "kotlin.Boolean" to "bool",
        "java.lang.Short" to "short",
        "kotlin.Short" to "short",
        "java.lang.Byte" to "byte",
        "kotlin.Byte" to "byte",
        "java.lang.Character" to "char",
        "kotlin.Char" to "char",
        "java.lang.Object" to "object",
        "kotlin.Any" to "object",
        "java.util.List" to "array",
        "kotlin.collections.List" to "array",
        "java.util.Set" to "array",
        "kotlin.collections.Set" to "array",
        "java.util.Map" to "map",
        "kotlin.collections.Map" to "map",
        "kotlin.Unit" to "void",
        "kotlin.Array" to "array"
    )
}
