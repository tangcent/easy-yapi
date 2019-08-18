package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import com.itangcent.common.model.Doc


class YapiApiDashBoardExporter : AbstractYapiApiExporter() {

    override fun getCartForCls(psiClass: PsiClass): CartInfo? {

        var cartId = clsCartMap[psiClass to ""]
        if (cartId != null) return cartId
        synchronized(clsCartMap)
        {
            cartId = clsCartMap[psiClass to ""]
            if (cartId != null) return cartId

            return super.getCartForCls(psiClass)
        }
    }

    private var tryInputTokenOfModule: HashSet<String> = HashSet()

    override fun getTokenOfModule(module: String): String? {
        val privateToken = super.getTokenOfModule(module)
        if (!privateToken.isNullOrBlank()) {
            return privateToken
        }

        if (tryInputTokenOfModule.contains(module)) {
            return null
        } else {
            tryInputTokenOfModule.add(module)
            val modulePrivateToken = actionContext!!.callInSwingUI {
                return@callInSwingUI Messages.showInputDialog(project, "Input Private Token Of Module:$module",
                        "Yapi Private Token", Messages.getInformationIcon())
            }
            return if (modulePrivateToken.isNullOrBlank()) {
                null
            } else {
                yapiApiHelper!!.setToken(module, modulePrivateToken)
                modulePrivateToken
            }
        }
    }

    private var successExportedCarts: MutableSet<String> = ContainerUtil.newConcurrentSet<String>()

    //cls -> CartInfo
    private val clsCartMap: HashMap<Pair<PsiClass, String>, CartInfo> = HashMap()

    override fun getCartForCls(psiClass: PsiClass, privateToken: String): CartInfo? {
        var cartId = clsCartMap[psiClass to privateToken]
        if (cartId != null) return cartId
        synchronized(clsCartMap)
        {
            cartId = clsCartMap[psiClass to privateToken]
            if (cartId != null) return cartId

            return super.getCartForCls(psiClass, privateToken)
        }
    }

    fun exportDoc(doc: Doc, privateToken: String): Boolean {
        val findResourceClass = resourceHelper!!.findResourceClass(doc.resource!!) ?: return false
        val cartInfo = getCartForCls(findResourceClass, privateToken) ?: return false
        return exportDoc(doc, privateToken, cartInfo.cartId!!)
    }

    override fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
        if (super.exportDoc(doc, privateToken, cartId)) {
            if (successExportedCarts.add(cartId)) {
                logger!!.info("Export to ${yapiApiHelper!!.getCartWeb(yapiApiHelper.getProjectIdByToken(privateToken)!!, cartId)} success")
            }
            return true
        }
        return false
    }
}