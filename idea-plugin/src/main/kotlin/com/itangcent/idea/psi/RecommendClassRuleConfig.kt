package com.itangcent.idea.psi

import com.itangcent.intellij.psi.DefaultClassRuleConfig

class RecommendClassRuleConfig : DefaultClassRuleConfig() {

    override fun findConvertRule(): Map<String, String> {
        val convertRules = super.findConvertRule()

        val mutableConvertRules = convertRules.toMutableMap()

        //some rules
        mutableConvertRules.putIfAbsent("java.util.Date", "java.lang.String")
        mutableConvertRules.putIfAbsent("org.bson.types.ObjectId", "java.lang.String")

        return when {
            mutableConvertRules.size == convertRules.size -> convertRules
            else -> mutableConvertRules
        }
    }
}