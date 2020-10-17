package com.itangcent.common.kit

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.joinToString
import com.itangcent.common.utils.notNullOrEmpty
import java.util.*
import kotlin.collections.ArrayList

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
        var desc = comment[field] as String?
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
        return options.stream()
                .map { concat(it["value"]?.toString(), it["desc"]?.toString()) }
                .filter { it != null }
                .joinToString("\n")
    }

    /**
     * get description of constants
     */
    fun getConstantDesc(constants: List<Map<String, Any?>>): String? {
        return constants.stream()
                .map { concat(it["name"]?.toString(), it["desc"]?.toString()) }
                .filter { it != null }
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
    fun addKeyComment(typedResponse: Any?, key: String, comment: String): Boolean {

        if (typedResponse is Collection<*>) {
            if (typedResponse.isEmpty()) {
                return false
            }
            return addKeyComment(typedResponse.first(), key, comment)

        }

        if (typedResponse is Array<*>) {
            if (typedResponse.isEmpty()) {
                return false
            }
            return addKeyComment(typedResponse.first(), key, comment)
        }

        if (typedResponse is Map<*, *>) {
            if (key.contains(".")) {
                val headerKey = key.substringBefore('.')
                val restKey = key.substringBefore('.')
                return addKeyComment(typedResponse[headerKey], restKey, comment)

            } else {
                addComment(typedResponse as (HashMap<Any, Any?>), key, comment)
                return true
            }
        }

        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun addComment(info: HashMap<Any, Any?>, field: Any, comment: String?) {
        var comments = info[Attrs.COMMENT_ATTR]
        if (comments == null) {
            comments = HashMap<String, String>()
            info[Attrs.COMMENT_ATTR] = comments
            (comments as HashMap<Any?, Any?>)[field] = comment
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
    fun addKeyOptions(typedResponse: Any?, key: String, options: ArrayList<HashMap<String, Any?>>): Boolean {

        if (typedResponse is Collection<*>) {
            if (typedResponse.isEmpty()) {
                return false
            }
            return addKeyOptions(typedResponse.first(), key, options)
        }

        if (typedResponse is Array<*>) {
            if (typedResponse.isEmpty()) {
                return false
            }
            return addKeyOptions(typedResponse.first(), key, options)
        }

        if (typedResponse is Map<*, *>) {
            if (key.contains(".")) {
                val headerKey = key.substringBefore('.')
                val restKey = key.substringBefore('.')
                return addKeyOptions(typedResponse[headerKey], restKey, options)
            } else {
                addOptions(typedResponse as HashMap<Any, Any?>, key, options)
                return true
            }
        }

        return false

    }

    @Suppress("UNCHECKED_CAST")
    fun addOptions(info: HashMap<Any, Any?>, field: String, options: ArrayList<HashMap<String, Any?>>) {
        var comments = info[Attrs.COMMENT_ATTR]
        if (comments == null) {
            comments = HashMap<String, String>()
            info[Attrs.COMMENT_ATTR] = comments
            (comments as HashMap<Any?, Any?>)["$field@options"] = options
        } else {
            val oldOptions = (comments as HashMap<Any?, Any?>)["$field@options"]
            if (oldOptions == null) {
                comments["$field@options"] = options
            } else {
                val mergeOptions: ArrayList<HashMap<String, Any?>> = ArrayList(oldOptions as ArrayList<HashMap<String, Any?>>)
                mergeOptions.addAll(options)
                comments["$field@options"] = mergeOptions
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.getAs(key: Any?): T? {
    return this[key] as? T
}

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.getAs(key: Any?, subKey: Any?): T? {
    return this.getAs<Map<*, *>>(key)?.getAs(subKey)
}

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.getAs(key: Any?, subKey: Any?, grandKey: Any?): T? {
    return this.getAs<Map<*, *>>(key)?.getAs<Map<*, *>>(subKey)?.getAs(grandKey)
}

@Suppress("UNCHECKED_CAST")
fun KV<String, Any?>.getAsKv(key: String): KV<String, Any?>? {
    return this[key] as KV<String, Any?>?
}

@Suppress("UNCHECKED_CAST")
fun KV<String, Any?>.sub(key: String): KV<String, Any?> {
    var subKV: KV<String, Any?>? = this[key] as KV<String, Any?>?
    if (subKV == null) {
        subKV = KV.create()
        this[key] = subKV
    }
    return subKV
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<out K, V>.mutable(copy: Boolean = false): MutableMap<K, V> {
    return when {
        copy -> LinkedHashMap(this)
        this is MutableMap -> this as MutableMap<K, V>
        else -> LinkedHashMap(this)
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.asKV(): KV<String, Any?> {
    if (this == null) {
        return KV.create()
    }
    if (this is KV<*, *>) {
        return this as KV<String, Any?>
    }
    if (this is Map<*, *>) {
        return KV.create<String, Any?>().set(this as Map<String, Any?>)
    }
    return KV.create()
}