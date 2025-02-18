package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.util.containers.ContainerUtil
import com.itangcent.common.model.Doc
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.support.IdeaSupport
import com.itangcent.idea.plugin.utils.NotificationUtils
import com.itangcent.intellij.context.AutoClear
import java.util.concurrent.ConcurrentHashMap


@Singleton
class YapiApiExporter : AbstractYapiApiExporter() {

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    @Inject
    private lateinit var ideaSupport: IdeaSupport

    fun export() {
        val serverFound = yapiSettingsHelper.getServer(false).notNullOrBlank()
        if (serverFound) {
            doExport()
        }
    }

    private fun doExport() {
        var anyFound = false
        classApiExporterHelper.export {
            anyFound = true
            exportDoc(it)
        }
        if (anyFound) {
            NotificationUtils.notifyInfo(project, "APIs exported successfully")
        } else {
            NotificationUtils.notifyInfo(project, "No API found to export")
        }
    }

    //privateToken+folderName -> CartInfo
    private val folderNameCartMap: ConcurrentHashMap<String, CartInfo> = ConcurrentHashMap()

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

    @AutoClear
    private var successExportedCarts: MutableSet<String> = ContainerUtil.newConcurrentSet()

    override fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
        if (super.exportDoc(doc, privateToken, cartId)) {
            if (successExportedCarts.add(cartId)) {
                val cartUrl = yapiApiHelper.getCartWeb(
                    yapiApiHelper.getProjectIdByToken(privateToken)!!,
                    cartId
                ) ?: return false
                NotificationUtils.notifyInfo(project, "Successfully exported to YApi: $cartUrl") {
                    ideaSupport.openUrl(cartUrl)
                }
            }
            return true
        }
        return false
    }
}