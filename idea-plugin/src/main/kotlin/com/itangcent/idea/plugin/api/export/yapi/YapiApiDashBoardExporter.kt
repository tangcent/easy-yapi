package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.util.containers.ContainerUtil
import com.itangcent.common.model.Doc
import com.itangcent.idea.plugin.api.export.Folder
import java.util.*
import kotlin.collections.set


class YapiApiDashBoardExporter : AbstractYapiApiExporter() {

    private var successExportedCarts: MutableSet<String> = ContainerUtil.newConcurrentSet<String>()

    //privateToken+folderName -> CartInfo
    private val folderNameCartMap: HashMap<String, CartInfo> = HashMap()

    @Synchronized
    override fun getCartForFolder(folder: Folder, privateToken: String): CartInfo? {
        var cartInfo = folderNameCartMap["$privateToken${folder.name}"]
        if (cartInfo != null) return cartInfo

        cartInfo = super.getCartForFolder(folder, privateToken)
        if (cartInfo != null) {
            folderNameCartMap["$privateToken${folder.name}"] = cartInfo
        }
        return cartInfo
    }

    fun exportDoc(doc: Doc, privateToken: String): Boolean {
        if (doc.resource == null) return false
        val cartInfo = getCartForResource(doc.resource!!, privateToken) ?: return false
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