package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.intellij.logger.Logger
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
    protected val moduleHelper: ModuleHelper? = null

    @Inject
    protected val resourceHelper: ResourceHelper? = null

    @Inject
    protected val yapiFormatter: YapiFormatter? = null

    @Inject
    protected val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    protected val docHelper: DocHelper? = null

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

        val clsName = actionContext!!.callInReadUI { psiClass.name }
        when {
            attrOfCls.contains("\n") -> {//multi line
                val lines = attrOfCls.lines()
                for (line in lines) {
                    if (line.isNotBlank()) {
                        name = line
                        break
                    }
                }
                desc = "[exported from:$clsName]\n$attrOfCls"
            }
            else -> {
                name = StringUtils.left(attrOfCls, 30)
                desc = when {
                    attrOfCls.length > 30 -> "[exported from:$clsName]\n$attrOfCls"
                    else -> "[exported from:$clsName]"
                }
            }
        }

        var cartId: String?
        try {
            cartId = yapiApiHelper!!.findCat(privateToken, name!!)
        } catch (e: Exception) {
            logger!!.traceError("error to find cart [$name]", e)
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

    fun exportDoc(doc: Doc): Boolean {
        if (doc.resource == null) return false
        val findResourceClass = resourceHelper!!.findResourceClass(doc.resource!!) ?: return false
        val cartInfo = getCartForCls(findResourceClass) ?: return false
        return exportDoc(doc, cartInfo.privateToken!!, cartInfo.cartId!!)
    }

    open fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
        val apiInfo = yapiFormatter!!.doc2Item(doc)
        apiInfo["token"] = privateToken
        apiInfo["catid"] = cartId
        return yapiApiHelper!!.saveApiInfo(apiInfo)
    }

    protected fun findAttrOfClass(cls: PsiClass): String? {
        val docText = docHelper!!.getAttrOfDocComment(cls)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls)
        }
    }

}