package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.idea.plugin.api.export.IdeaParseHandle
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.DocCommentUtils
import org.apache.commons.lang3.StringUtils

class YapiIdeaParseHandle : IdeaParseHandle() {

    @Inject
    private val yapiApiHelper: YapiApiHelper? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val logger: Logger? = null

    override fun linkToClass(linkClass: Any): String? {
        if (linkClass !is PsiClass) {
            return "[$linkClass]"
        }
        try {
            val module = moduleHelper!!.findModule(linkClass)

            if (module != null) {
                val attrOfCls = findAttrOfClass(linkClass)!!
                var apiDirName: String? = null
                if (attrOfCls.contains("\n")) {//multi line
                    val lines = attrOfCls.lines()
                    for (line in lines) {
                        if (line.isNotBlank()) {
                            apiDirName = line
                            break
                        }
                    }
                } else {
                    apiDirName = StringUtils.left(attrOfCls, 30)
                }

                val cartWeb = yapiApiHelper!!.getCartWeb(module, apiDirName!!)
                if (cartWeb != null) {
                    return "[<a href=\"$cartWeb\">$apiDirName</a>]"
                }
            }
        } catch (e: Exception) {
            logger!!.warn("error to linkToClass:" + linkClass.qualifiedName)
        }
        return super.linkToClass(linkClass)
    }

    override fun linkToMethod(linkMethod: Any): String? {
        if (linkMethod !is PsiMethod) {
            return "[$linkMethod]"
        }

        try {
            val linkClass = linkMethod.containingClass!!

            val module = moduleHelper!!.findModule(linkMethod)

            if (module != null) {

                val attrOfCls = findAttrOfClass(linkClass)!!
                var apiDirName: String? = null
                if (attrOfCls.contains("\n")) {//multi line
                    val lines = attrOfCls.lines()
                    for (line in lines) {
                        if (line.isNotBlank()) {
                            apiDirName = line
                            break
                        }
                    }
                } else {
                    apiDirName = StringUtils.left(attrOfCls, 30)
                }
                var apiName: String? = null
                val attrOfMethod = findAttrOfMethod(linkMethod)!!
                if (attrOfMethod.contains("\n")) {//multi line
                    val lines = attrOfMethod.lines()
                    for (line in lines) {
                        if (line.isNotBlank()) {
                            apiName = line
                            break
                        }
                    }
                } else {
                    apiName = attrOfMethod
                }


                val apiWeb = yapiApiHelper!!.getApiWeb(module, apiDirName!!, apiName!!)
                if (apiWeb != null) {
                    return "[<a href=\"$apiWeb\">$apiName</a>]"
                }
            }
        } catch (e: Exception) {
            logger!!.warn("error to linkToMethod:" + PsiClassUtils.fullNameOfMethod(linkMethod))
        }
        return super.linkToMethod(linkMethod)
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docText
        }
    }

    private fun findAttrOfMethod(method: PsiMethod): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> docText
        }
    }
}