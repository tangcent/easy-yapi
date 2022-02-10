package com.itangcent.idea.plugin.format

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.trimToNull
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.forEachValid

/**
 * Implementation of [com.itangcent.idea.plugin.format.MessageFormatter]
 * that can write the object as properties string.
 *
 * @author tangcent
 */
@Singleton
class PropertiesFormatter : MessageFormatter {

    @Inject
    private lateinit var actionContext: ActionContext

    override fun format(obj: Any?, desc: String?): String {
        var prefix = actionContext.getCache<String>(ClassExportRuleKeys.PROPERTIES_PREFIX.name())
        if (prefix != null) {
            prefix = prefix.trimToNull()?.removeSuffix(".")
        }
        return format(obj, prefix, desc)
    }

    fun format(obj: Any?, path: String?, desc: String?): String {
        val sb = StringBuilder()
        format(obj, path, desc, sb)
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun format(obj: Any?, path: String?, desc: String?, sb: StringBuilder) {
        when {
            obj == null || obj.javaClass == java.lang.Object::class.java -> {
                sb.appendComment(desc)
                if (path != null) {
                    appendKV(sb, path, null)
                }
            }
            obj is Array<*> -> {
                sb.appendComment(desc)
                if (obj.isEmpty()) {
                    return
                }
                obj.forEach {
                    format(it, path, null, sb)
                }
            }
            obj is Collection<*> -> {
                sb.appendComment(desc)
                if (obj.isEmpty()) {
                    return
                }
                obj.forEach {
                    sb.appendLineIfNeed()
                    format(it, path, null, sb)
                }
            }
            obj is Map<*, *> -> {
                sb.appendComment(desc)
                if (obj.isEmpty()) {
                    return
                }
                val comment = obj[Attrs.COMMENT_ATTR] as? Map<String, Any?>
                obj.forEachValid { k, v ->
                    val propertyDesc: String = KVUtils.getUltimateComment(comment, k)
                    if (path == null) {
                        format(v, k.toString(), propertyDesc, sb)
                    } else {
                        format(v, "$path.${k.toString().trim()}", propertyDesc, sb)
                    }
                }
            }
            else -> {
                sb.appendComment(desc)
                if (path != null) {
                    appendKV(sb, path, obj)
                } else {
                    sb.appendLineIfNeed()
                    sb.append("#!! unknown properties !!")
                }
            }
        }
    }

    private fun appendKV(sb: StringBuilder, path: String?, obj: Any?) {
        sb.appendLineIfNeed()
        sb.append(path)
        sb.append("=")
        if (obj != null) {
            sb.append(obj.toString())
        }
    }

    /**
     *  Appends a line feed character (`\n`) to this StringBuilder if this StringBuilder is not empty.
     */
    private fun StringBuilder.appendLineIfNeed(): StringBuilder {
        if (this.isEmpty()) {
            return this
        }
        return appendLine()
    }

    /**
     *  Appends comments to this StringBuilder.
     *  Each line will be add the prefix '#'
     */
    private fun StringBuilder.appendComment(desc: String?) {
        if (desc.notNullOrBlank()) {
            for (line in desc!!.lines()) {
                if (line.isBlank()) {
                    continue
                }
                this.appendLineIfNeed()
                this.append("#")
                this.append(line)
            }
        }
    }
}