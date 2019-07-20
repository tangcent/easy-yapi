package com.itangcent.common.utils

object KVUtils {

    /**
     * tay get all comment info
     * 1.common comment
     * 2.options
     * 3....
     */
    fun getUltimateComment(comment: Map<*, *>?, field: Any?): String {
        if (comment == null || field == null) return ""
        var desc = comment[field] as String?
        val options = comment["$field@options"]
        if (options != null) {
            val optionList = options as List<Map<String, Any?>>

            val optionDesc = getOptionDesc(optionList)
            if (!optionDesc.isNullOrBlank()) {
                if (desc.isNullOrBlank()) {
                    desc = optionDesc
                } else {
                    desc = desc + "\n" + optionDesc
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
                .map { it["value"].toString() + " :" + it["desc"] }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
    }
}
