package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.kit.headLine
import com.itangcent.common.utils.append
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.intellij.jvm.element.ExplicitMethod

@Singleton
open class ApiHelper {

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected val docParseHelper: DocParseHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    fun nameOfApi(psiMethod: PsiMethod): String {

        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, psiMethod)
        if (nameByRule.notNullOrEmpty()) {
            return nameByRule!!
        }

        val attrOfDocComment = docHelper!!.getAttrOfDocComment(psiMethod)
        var headLine = attrOfDocComment?.headLine()
        if (headLine.notNullOrEmpty()) return headLine!!

        val docByRule = ruleComputer.computer(ClassExportRuleKeys.METHOD_DOC, psiMethod)
        headLine = docByRule?.headLine()
        if (headLine.notNullOrEmpty()) return headLine!!

        return psiMethod.name
    }

    protected open fun findAttrOfMethod(method: ExplicitMethod): String? {
        val attrOfDocComment = docHelper!!.getAttrOfDocComment(method.psi())

        val docByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)

        return attrOfDocComment.append(docByRule, "\n")
    }

    protected open fun findAttrOfMethod(method: PsiMethod): String? {
        val attrOfDocComment = docHelper!!.getAttrOfDocComment(method)

        val docByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)

        return attrOfDocComment.append(docByRule, "\n")
    }

    fun nameAndAttrOfApi(explicitMethod: ExplicitMethod): Pair<String?, String?> {
        var name: String? = null
        var attr: String? = null
        nameAndAttrOfApi(explicitMethod, {
            name = it
        }, {
            attr = attr.append(it, "\n")
        })
        return name to attr
    }

    fun nameAndAttrOfApi(
        explicitMethod: ExplicitMethod, nameHandle: (String) -> Unit,
        attrHandle: (String) -> Unit
    ) {
        var named = false
        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, explicitMethod)
        if (nameByRule.notNullOrEmpty()) {
            nameHandle(nameByRule!!)
            named = true
        }

        var attrOfMethod = findAttrOfMethod(explicitMethod)

        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, explicitMethod.psi())?.trim()

        if (attrOfMethod.notNullOrEmpty()) {
            attrOfMethod!!
            if (named) {
                attrHandle(attrOfMethod)
            } else {
                val headLine = attrOfMethod.headLine()
                nameHandle(headLine!!)
                named = true
                attrHandle(attrOfMethod.removePrefix(headLine).trimStart())
            }
        }

        if (!named) {
            nameHandle(explicitMethod.name())
        }
    }

    fun nameAndAttrOfApi(
        explicitMethod: PsiMethod, nameHandle: (String) -> Unit,
        attrHandle: (String) -> Unit
    ) {
        var named = false
        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, explicitMethod)
        if (nameByRule.notNullOrEmpty()) {
            nameHandle(nameByRule!!)
            named = true
        }

        var attrOfMethod = findAttrOfMethod(explicitMethod)

        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, explicitMethod)

        if (attrOfMethod.notNullOrEmpty()) {
            attrOfMethod!!
            if (named) {
                attrHandle(attrOfMethod)
            } else {
                val headLine = attrOfMethod.headLine()
                nameHandle(headLine!!)
                named = true
                attrHandle(attrOfMethod.removePrefix(headLine).trimStart())
            }
        }

        if (!named) {
            nameHandle(explicitMethod.name)
        }
    }
}
