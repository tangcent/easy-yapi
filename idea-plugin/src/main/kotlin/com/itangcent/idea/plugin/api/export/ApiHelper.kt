package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.utils.concat
import com.itangcent.common.utils.headLine
import com.itangcent.common.utils.notEmpty
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.jvm.DocHelper

@Singleton
class ApiHelper {

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

    open protected fun findAttrOfMethod(method: PsiMethod): String? {
        val attrOfDocComment = docHelper!!.getAttrOfDocComment(method)

        val docByRule = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)

        return attrOfDocComment.concat(docByRule)
    }

    fun nameAndAttrOfApi(psiMethod: PsiMethod): Pair<String?, String?> {
        var name: String? = null
        var attr: String? = null
        nameAndAttrOfApi(psiMethod, {
            name = it
        }, {
            attr = attr.concat(it)
        })
        return name to attr
    }

    fun nameAndAttrOfApi(psiMethod: PsiMethod, nameHandle: (String) -> Unit,
                         attrHandle: (String) -> Unit) {


        var named = false
        val nameByRule = ruleComputer!!.computer(ClassExportRuleKeys.API_NAME, psiMethod)
        if (nameByRule.notEmpty()) {
            nameHandle(nameByRule!!)
            named = true
        }

        var attrOfMethod = findAttrOfMethod(psiMethod)

        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, psiMethod)

        if (attrOfMethod.notEmpty()) {
            attrHandle(attrOfMethod!!)

            if (!named) {
                nameHandle(attrOfMethod.headLine() ?: psiMethod.name)
                named = true
            }
        }

        if (!named) {
            nameHandle(psiMethod.name)
        }
    }
}
