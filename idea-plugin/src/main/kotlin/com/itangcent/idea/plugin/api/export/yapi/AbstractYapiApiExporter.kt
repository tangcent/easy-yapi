package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.RequestHelper
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.traceError
import org.apache.commons.lang3.StringUtils


open class AbstractYapiApiExporter {

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val yapiApiHelper: YapiApiHelper? = null

    @Inject
    protected val actionContext: ActionContext? = null

    @Inject
    protected val classExporter: ClassExporter? = null

    @Inject
    protected val parseHandle: RequestHelper? = null

    @Inject
    protected val moduleHelper: ModuleHelper? = null

    @Inject
    protected val resourceHelper: ResourceHelper? = null

    @Inject
    protected val yapiFormatter: YapiFormatter? = null

    @Inject
    protected val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    protected val project: Project? = null

    protected open fun getTokenOfModule(module: String): String? {
        return yapiApiHelper!!.getPrivateToken(module)
    }

    protected open fun getCartForCls(psiClass: PsiClass): CartInfo? {

        val module = actionContext!!.callInReadUI { moduleHelper!!.findModule(psiClass) } ?: return null

        val privateToken = getTokenOfModule(module)
        if (privateToken == null) {
            logger!!.info("No token be found for $module")
            return null
        }
        return getCartForCls(psiClass, privateToken)
    }

    protected open fun getCartForCls(psiClass: PsiClass, privateToken: String): CartInfo? {

        var name: String? = null
        val desc: String?
        val attrOfCls = findAttrOfClass(psiClass)!!

        when {
            attrOfCls.contains("\n") -> {//multi line
                val lines = attrOfCls.lines()
                for (line in lines) {
                    if (line.isNotBlank()) {
                        name = line
                        break
                    }
                }
                desc = "[exported from:${psiClass.name}]\n$attrOfCls"
            }
            else -> {
                name = StringUtils.left(attrOfCls, 30)
                desc = when {
                    attrOfCls.length > 30 -> "[exported from:${psiClass.name}]\n$attrOfCls"
                    else -> "[exported from:${psiClass.name}]"
                }
            }
        }

        var cartId: String?
        try {
            cartId = yapiApiHelper!!.findCat(privateToken, name!!)
        } catch (e: Exception) {
            logger!!.error("error to find cart [$name]")
            logger.traceError(e)
            return null
        }
        if (cartId == null) {
            if (yapiApiHelper.addCart(privateToken, name, desc)) {
                cartId = yapiApiHelper.findCat(privateToken, name)
            } else {
                //failed
                return null
            }
        }

        val cartInfo = CartInfo()
        cartInfo.cartId = cartId
        cartInfo.cartName = name
        cartInfo.privateToken = privateToken

        return cartInfo
    }

    fun exportRequest(request: Request): Boolean {
        if (request.resource == null) return false
        val findResourceClass = resourceHelper!!.findResourceClass(request.resource!!) ?: return false
        val cartInfo = getCartForCls(findResourceClass) ?: return false
        return exportRequest(request, cartInfo.privateToken!!, cartInfo.cartId!!)
    }

    open fun exportRequest(request: Request, privateToken: String, cartId: String): Boolean {
        val request2Item = yapiFormatter!!.request2Item(request)
        request2Item["token"] = privateToken
        request2Item["catid"] = cartId
        return yapiApiHelper!!.saveApiInfo(request2Item)
    }

    protected fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls, parseHandle!!)
        }
    }

}