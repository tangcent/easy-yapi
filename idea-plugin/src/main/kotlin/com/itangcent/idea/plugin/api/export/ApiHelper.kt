package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.utils.concat
import com.itangcent.common.utils.headLine
import com.itangcent.common.utils.notEmpty
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.config.rule.RuleComputer
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
        if (nameByRule.notEmpty()) {
            return nameByRule!!
        }

        val attrOfDocComment = docHelper!!.getAttrOfDocComment(psiMethod)
        var headLine = attrOfDocComment?.headLine()
        if (headLine.notEmpty()) return headLine!!

        val docByRule = ruleComputer.computer(ClassExportRuleKeys.METHOD_DOC, psiMethod)
        headLine = docByRule?.headLine()
        if (headLine.notEmpty()) return headLine!!

        return psiMethod.name
    }

    protected open fun findAttrOfMethod(method: ExplicitMethod): String? {
        val attrOfDocComment = docHelper!!.getAttrOfDocComment(method.psi())

        val docByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)

        return attrOfDocComment.concat(docByRule)
    }

    protected open fun findAttrOfMethod(method: PsiMethod): String? {
        val attrOfDocComment = docHelper!!.getAttrOfDocComment(method)

        val docByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)

        return attrOfDocComment.concat(docByRule)
    }

    fun nameAndAttrOfApi(explicitMethod: ExplicitMethod): Pair<String?, String?> {
        var name: String? = null
        var attr: String? = null
        nameAndAttrOfApi(explicitMethod, {
            name = it
        }, {
            attr = attr.concat(it)
        })
        return name to attr
    }

    fun nameAndAttrOfApi(explicitMethod: ExplicitMethod, nameHandle: (String) -> Unit,
                         attrHandle: (String) -> Unit) {


        var named = false
        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, explicitMethod)
        if (nameByRule.notEmpty()) {
            nameHandle(nameByRule!!)
            named = true
        }

        var attrOfMethod = findAttrOfMethod(explicitMethod)

        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, explicitMethod.psi())

        if (attrOfMethod.notEmpty()) {
            attrHandle(attrOfMethod!!)

            if (!named) {
                nameHandle(attrOfMethod.headLine() ?: explicitMethod.name())
                named = true
            }
        }

        if (!named) {
            nameHandle(explicitMethod.name())
        }
    }

    fun nameAndAttrOfApi(explicitMethod: PsiMethod, nameHandle: (String) -> Unit,
                         attrHandle: (String) -> Unit) {


        var named = false
        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, explicitMethod)
        if (nameByRule.notEmpty()) {
            nameHandle(nameByRule!!)
            named = true
        }

        var attrOfMethod = findAttrOfMethod(explicitMethod)

        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, explicitMethod)

        if (attrOfMethod.notEmpty()) {
            attrHandle(attrOfMethod!!)

            if (!named) {
                nameHandle(attrOfMethod.headLine() ?: explicitMethod.name)
                named = true
            }
        }

        if (!named) {
            nameHandle(explicitMethod.name)
        }
    }
}
