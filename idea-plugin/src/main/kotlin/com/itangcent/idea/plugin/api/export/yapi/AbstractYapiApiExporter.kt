package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.Folder
import com.itangcent.idea.plugin.api.export.FormatFolderHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger


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
    protected val yapiFormatter: YapiFormatter? = null

    @Inject
    protected val project: Project? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    protected open fun getTokenOfModule(module: String): String? {
        return yapiApiHelper!!.getPrivateToken(module)
    }

    protected open fun getCartForDoc(resource: Any): CartInfo? {

        val module = actionContext!!.callInReadUI { moduleHelper!!.findModule(resource) } ?: return null
        val folder = formatFolderHelper!!.resolveFolder(resource)

        val privateToken = getTokenOfModule(module)
        if (privateToken == null) {
            logger!!.info("No token be found for $module")
            return null
        }
        return getCartForDoc(folder, privateToken)
    }

    protected open fun getCartForDoc(folder: Folder, privateToken: String): CartInfo? {

        val name: String = folder.first

        var cartId: String?
        try {
            cartId = yapiApiHelper!!.findCat(privateToken, name)
        } catch (e: Exception) {
            logger!!.traceError("error to find cart [$name]", e)
            return null
        }
        if (cartId == null) {
            if (yapiApiHelper.addCart(privateToken, name, folder.second)) {
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
        val cartInfo = getCartForDoc(doc.resource!!) ?: return false
        return exportDoc(doc, cartInfo.privateToken!!, cartInfo.cartId!!)
    }

    open fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
        val apiInfos = yapiFormatter!!.doc2Item(doc)
        var ret = false
        apiInfos.forEach { apiInfo ->
            apiInfo["token"] = privateToken
            apiInfo["catid"] = cartId
            ret = ret or yapiApiHelper!!.saveApiInfo(apiInfo)
        }
        return ret
    }

    companion object {
        const val NULL_RESOURCE = "unknown"
    }

}