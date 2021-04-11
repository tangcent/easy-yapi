package com.itangcent.idea.plugin.json

import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.intellij.util.forEachValid
import com.itangcent.intellij.util.validSize

@Singleton
class Json5Formatter : JsonFormatter {

    override fun format(obj: Any?, desc: String?): String {
        val sb = StringBuilder()
        format(obj, 0, true, desc, sb)
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun format(obj: Any?, deep: Int, end: Boolean, desc: String?, sb: StringBuilder) {
        when (obj) {
            null -> {
                sb.append("null")
                sb.appendEnd(end)
                sb.appendEndLineComment(desc)
            }
            is Array<*> -> {
                if (obj.isEmpty()) {
                    sb.append("[]")
                    sb.appendEnd(end)
                    sb.appendEndLineComment(desc)
                    return
                }
                sb.append("[")
                sb.appendEndLineComment(desc)
                val endCounter = EndCounter(obj.size)
                obj.forEach {
                    sb.nextLine(deep + 1)
                    format(it, deep + 1, endCounter.end(), null, sb)
                }
                sb.nextLine(deep)
                sb.append("]")
                sb.appendEnd(end)
            }
            is Collection<*> -> {
                if (obj.isEmpty()) {
                    sb.append("[]")
                    sb.appendEnd(end)
                    sb.appendEndLineComment(desc)
                    return
                }
                sb.append("[")
                sb.appendEndLineComment(desc)
                val endCounter = EndCounter(obj.size)
                obj.forEach {
                    sb.nextLine(deep + 1)
                    format(it, deep + 1, endCounter.end(), null, sb)
                }
                sb.nextLine(deep)
                sb.append("]")
                sb.appendEnd(end)
            }
            is Map<*, *> -> {
                if (obj.isEmpty()) {
                    sb.append("{}")
                    sb.appendEnd(end)
                    sb.appendEndLineComment(desc)
                    return
                }
                val comment = obj[Attrs.COMMENT_ATTR] as? Map<String, Any?>
                sb.append("{")
                sb.appendEndLineComment(desc)
                val endCounter = EndCounter(obj.validSize())
                obj.forEachValid { k, v ->
                    val propertyDesc: String? = KVUtils.getUltimateComment(comment, k)
                    sb.nextLine(deep + 1)
                    format(k.toString(), v, deep + 1, propertyDesc ?: "", endCounter.end(), sb)
                }
                sb.nextLine(deep)
                sb.append("}")
                sb.appendEnd(end)
            }
            is String -> {
                sb.appendString(obj)
                sb.appendEnd(end)
                sb.appendEndLineComment(desc)
            }
            else -> {
                sb.append(obj)
                sb.appendEnd(end)
                sb.appendEndLineComment(desc)
            }
        }
    }

    private fun format(name: String, obj: Any?, deep: Int, desc: String?, end: Boolean, sb: StringBuilder) {
        if (desc.isNullOrBlank()) {
            sb.appendString(name)
            sb.append(": ")
            format(obj, deep, end, desc, sb)
            return
        }
        val lines = desc.lines()
        if (lines.size == 1) {
            sb.appendString(name)
            sb.append(": ")
            format(obj, deep, end, desc, sb)
            return
        } else {
            sb.appendBlockComment(lines, deep)
            sb.appendString(name)
            sb.append(": ")
            format(obj, deep, end, null, sb)
            return
        }
    }

    private fun StringBuilder.appendString(key: String) {
        this.append('"')
        this.append(key)
        this.append('"')
    }

    private fun StringBuilder.appendEnd(end: Boolean) {
        if (!end) {
            this.append(',')
        }
    }

    private fun StringBuilder.appendBlockComment(descs: List<String>, deep: Int) {
        this.append("/**")
        this.appendln()
        descs.forEach {
            this.append(TAB.repeat(deep))
            this.append(" * ")
            this.appendln(it)
        }
        this.append(TAB.repeat(deep))
        this.append(" */")
        this.appendln()
        this.append(TAB.repeat(deep))
    }

    private fun StringBuilder.appendEndLineComment(desc: String?) {
        if (desc.notNullOrBlank()) {
            this.append(" //")
            this.append(desc)
        }
    }

    private fun StringBuilder.nextLine(deep: Int) {
        this.appendln()
        this.append(TAB.repeat(deep))
    }
}

private class EndCounter(val size: Int) {
    private var i = 0
    fun end(): Boolean {
        return ++i == size
    }
}

private const val TAB = "    "

private fun StringBuilder.appendln(): StringBuilder = append("\n")