package com.itangcent.idea.plugin.rule

import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.intellij.config.rule.SimpleRuleParser
import java.util.regex.Pattern

object FieldPathMatcher {

    private val regexParseCache: HashMap<String, (String?) -> Boolean> = HashMap()

    private val alwaysTrue: (String?) -> Boolean = { true }

    fun parseRegexOrConstant(str: String): (String?) -> Boolean {
        return regexParseCache.safeComputeIfAbsent(str) {
            if (str.isBlank()) {
                return@safeComputeIfAbsent alwaysTrue
            }
            val tinyStr = str.trim()
            if (tinyStr == "*") {
                return@safeComputeIfAbsent alwaysTrue
            }

            if (tinyStr.contains("*")) {
                val pattern = Pattern.compile(
                    "^${
                        tinyStr.replace("*.", SimpleRuleParser.STAR_DOT)
                            .replace("*", SimpleRuleParser.STAR)
                            .replace(SimpleRuleParser.STAR_DOT, ".*?(?<=^|\\.)")
                            .replace(SimpleRuleParser.STAR, ".*?")
                            .replace("[", "\\[")
                            .replace("]", "\\]")

                    }$"
                )

                return@safeComputeIfAbsent {
                    pattern.matcher(it ?: "").matches()
                }
            }

            return@safeComputeIfAbsent {
                str == it
            }
        }!!
    }

}