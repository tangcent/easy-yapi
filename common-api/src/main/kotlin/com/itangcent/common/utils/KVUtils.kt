package com.itangcent.common.utils

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
            if (!optionDesc.isNullOrBlank()) {
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
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
    }

    /**
     * get description of constants
     */
    fun getConstantDesc(constants: List<Map<String, Any?>>): String? {
        return constants.stream()
                .map { concat(it["name"]?.toString(), it["desc"]?.toString()) }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
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
}
