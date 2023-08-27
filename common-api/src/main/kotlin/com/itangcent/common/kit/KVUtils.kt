package com.itangcent.common.kit

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.*
import com.itangcent.utils.isCollections
import com.itangcent.utils.subMutable

object KVUtils {

    /**
     * tay get all comment info
     * 1.common comment
     * 2.options
     * 3....
     */
    @Suppress("UNCHECKED_CAST")
    fun getUltimateComment(comment: Map<*, *>?, field: Any?): String {
        if (comment == null || field == null) return ""
        var desc = comment[field] as? String
        val options = comment["$field@options"]
        if (options != null) {
            val optionList = options as List<Map<String, Any?>>

            val optionDesc = getOptionDesc(optionList)
            if (optionDesc.notNullOrEmpty()) {
                desc = if (desc.isNullOrBlank()) {
                    optionDesc
                } else {
                    desc + "\n" + optionDesc
                }
            }

        }
        return desc ?: ""
    }

    /**
     * get description of options
     */
    fun getOptionDesc(options: List<Map<String, Any?>>): String? {
        return options.asSequence()
            .mapNotNull { concat(it["value"]?.toString(), it["desc"]?.toString()) }
            .joinToString("\n")
    }

    /**
     * get description of constants
     */
    fun getConstantDesc(constants: List<Map<String, Any?>>): String? {
        return constants.asSequence()
            .mapNotNull { concat(it["name"]?.toString(), it["desc"]?.toString()) }
            .joinToString("\n")
    }

    private fun concat(name: String?, desc: String?): String? {
        if (name.isNullOrEmpty()) {
            return null
        }
        if (desc.isNullOrEmpty()) {
            return name
        }
        return "$name :$desc"
    }

    @Suppress("UNCHECKED_CAST")
    fun addKeyComment(model: Any?, key: String, comment: String): Boolean {

        if (model is Collection<*>) {
            if (model.isEmpty()) {
                return false
            }
            return addKeyComment(model.first(), key, comment)

        }

        if (model is Array<*>) {
            if (model.isEmpty()) {
                return false
            }
            return addKeyComment(model.first(), key, comment)
        }

        if (model is Map<*, *>) {
            return if (key.contains(".")) {
                val headerKey = key.substringBefore('.')
                val restKey = key.substringAfter('.')
                addKeyComment(model[headerKey], restKey, comment)
            } else {
                addComment(model as (HashMap<Any, Any?>), key, comment)
                true
            }
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun addComment(info: HashMap<Any, Any?>, field: String, comment: String?) {
        val comments = info[Attrs.COMMENT_ATTR]
        if (comments == null) {
            info[Attrs.COMMENT_ATTR] = linkedMapOf(field to comment)
        } else {
            val oldComment = (comments as HashMap<Any?, Any?>)[field]
            if (oldComment == null) {
                comments[field] = comment
            } else {
                comments[field] = "$oldComment\n$comment"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun addKeyOptions(model: Any?, key: String, options: ArrayList<HashMap<String, Any?>>): Boolean {

        if (model is Collection<*>) {
            if (model.isEmpty()) {
                return false
            }
            return addKeyOptions(model.first(), key, options)
        }

        if (model is Array<*>) {
            if (model.isEmpty()) {
                return false
            }
            return addKeyOptions(model.first(), key, options)
        }

        if (model is Map<*, *>) {
            return if (key.contains(".")) {
                val headerKey = key.substringBefore('.')
                val restKey = key.substringAfter('.')
                addKeyOptions(model[headerKey], restKey, options)
            } else {
                addOptions(model as HashMap<Any, Any?>, key, options)
                true
            }
        }

        return false

    }

    @Suppress("UNCHECKED_CAST")
    fun addOptions(info: HashMap<Any, Any?>, field: String, options: ArrayList<HashMap<String, Any?>>) {
        val comments = info[Attrs.COMMENT_ATTR]
        if (comments == null) {
            info[Attrs.COMMENT_ATTR] = linkedMapOf("$field@options" to options)
        } else {
            val oldOptions = (comments as HashMap<Any?, Any?>)["$field@options"]
            if (oldOptions == null) {
                comments["$field@options"] = options
            } else {
                comments["$field@options"] = (oldOptions as ArrayList<*>) + options
            }
        }
    }

    fun useFieldAsAttr(model: Any?, attr: String) {
        when (model) {
            null -> {
                return
            }

            is Collection<*> -> {
                if (model.isEmpty()) {
                    return
                }
                return useFieldAsAttr(model.first(), attr)
            }

            is Array<*> -> {
                if (model.isEmpty()) {
                    return
                }
                return useFieldAsAttr(model.first(), attr)
            }

            is Map<*, *> -> {
                val keys = model.keys.toList()
                keys.forEach { key ->
                    if (key !is String) {
                        return@forEach
                    }
                    val value = model[key]
                    if (!value.isCollections()) {
                        model.subMutable(attr)?.set(key, value)
                    }
                    useFieldAsAttr(value, attr)
                }
            }

            is Extensible -> {
                model.getPropertyValue("value")?.let {
                    model.setExt(attr, it)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun useAttrAsValue(model: Any?, vararg attrs: String) {
        when {
            model == null -> {
                return
            }

            model is Collection<*> -> {
                if (model.isEmpty()) {
                    return
                }
                return useAttrAsValue(model.first(), *attrs)
            }

            model is Array<*> -> {
                if (model.isEmpty()) {
                    return
                }
                return useAttrAsValue(model.first(), *attrs)
            }

            model is Map<*, *> && model.isMutableMap() -> {
                model as MutableMap<Any?, Any?>
                val keys = model.keys.toList()
                val attrMaps = attrs.map { model[it] }
                    .mapNotNull { it as? Map<Any?, Any?> }
                for (key in keys) {
                    if (key !is String || key.startsWith(Attrs.PREFIX)) {
                        continue
                    }
                    val value = model[key]
                    if (!value.isCollections()) {
                        attrMaps.firstNotNullOfOrNull { it[key] }
                            ?.let {
                                model[key] = it
                            }
                    }
                    useAttrAsValue(value, *attrs)
                }
            }

            model is Extensible -> {
                attrs.firstNotNullOfOrNull { model.getExt<Any>(it) }
                    ?.let {
                        model.changePropertyValue("value", it)
                    }
            }
        }
    }
}