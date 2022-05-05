package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.openapi.ui.Messages
import com.intellij.util.containers.ContainerUtil
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileType
import kotlin.collections.set


class YapiApiExporter : AbstractYapiApiExporter() {

    fun export() {
        val serverFound = yapiSettingsHelper.getServer(false).notNullOrBlank()
        if (serverFound) {
            doExport()
        }
    }

    private fun doExport() {

        logger!!.info("Start find apis...")

        val boundary = actionContext.createBoundary()
        try {
            SelectedHelper.Builder()
                .dirFilter { dir, callBack ->
                    actionContext.runInSwingUI {
                        try {
                            val yes = actionContext.instance(MessagesHelper::class).showYesNoDialog(
                                "Export apis in directory [${ActionUtils.findCurrentPath(dir)}]?",
                                "Please Confirm",
                                Messages.getQuestionIcon()
                            )
                            if (yes == Messages.YES) {
                                callBack(true)
                            } else {
                                logger.info("Cancel the operation export api from [${ActionUtils.findCurrentPath(dir)}]!")
                                callBack(false)
                            }
                        } catch (e: Exception) {
                            callBack(false)
                        }
                    }
                }
                .fileFilter { file -> FileType.acceptable(file.name) }
                .classHandle {
                    classExporter!!.export(it) { doc -> exportDoc(doc) }
                }
                .traversal()
        } catch (e: Exception) {
            logger.traceError("failed export apis!", e)
        }

        boundary.waitComplete()
        logger.info("Apis exported completed")
    }

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

    private var successExportedCarts: MutableSet<String> = ContainerUtil.newConcurrentSet()

    override fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
        if (super.exportDoc(doc, privateToken, cartId)) {
            if (successExportedCarts.add(cartId)) {
                logger!!.info(
                    "Export to ${
                        yapiApiHelper!!.getCartWeb(
                            yapiApiHelper.getProjectIdByToken(privateToken)!!,
                            cartId
                        )
                    } success"
                )
            }
            return true
        }
        return false
    }
}