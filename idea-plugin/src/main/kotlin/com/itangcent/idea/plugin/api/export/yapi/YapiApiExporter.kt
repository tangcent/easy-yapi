package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils


class YapiApiExporter : AbstractYapiApiExporter() {

    fun export() {
        val serverFound = !yapiApiHelper!!.findServer().isNullOrBlank()
        if (serverFound) {
            doExport()
        } else {

            actionContext!!.runAsync {
                Thread.sleep(200)
                actionContext.runInSwingUI {
                    val yapiServer = Messages.showInputDialog(project, "Input server of yapi",
                            "server of yapi", Messages.getInformationIcon())
                    if (yapiServer.isNullOrBlank()) {
                        logger!!.info("No yapi server")
                        return@runInSwingUI
                    }

                    yapiApiHelper.setYapiServer(yapiServer)

                    doExport()
                }
            }
        }
    }

    private fun doExport() {

        logger!!.info("Start find apis...")

        SelectedHelper.Builder()
                .dirFilter { dir, callBack ->
                    actionContext!!.runInSwingUI {
                        try {
                            val project = actionContext.instance(Project::class)
                            val yes = Messages.showYesNoDialog(project,
                                    "Export the model in directory [${ActionUtils.findCurrentPath(dir)}]?",
                                    "Are you sure",
                                    Messages.getQuestionIcon())
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
                .fileFilter { file -> file.name.endsWith(".java") }
                .classHandle {
                    classExporter!!.export(it, parseHandle!!) { request -> exportRequest(request) }
                }
                .onCompleted {
                    if (classExporter is Worker) {
                        classExporter.waitCompleted()
                    }
                    logger.info("Apis exported completed")
                }
                .traversal()
    }

    //cls -> CartInfo
    private val clsCartMap: HashMap<PsiClass, CartInfo> = HashMap()

    override fun getCartForCls(psiClass: PsiClass): CartInfo? {

        var cartId = clsCartMap[psiClass]
        if (cartId != null) return cartId
        synchronized(clsCartMap)
        {
            cartId = clsCartMap[psiClass]
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

    override fun exportRequest(request: Request, privateToken: String, cartId: String): Boolean {
        if (super.exportRequest(request, privateToken, cartId)) {
            if (successExportedCarts.add(cartId)) {
                logger!!.info("Export to ${yapiApiHelper!!.getCartWeb(yapiApiHelper.getProjectIdByToken(privateToken)!!, cartId)} success")
            }
            return true
        }
        return false
    }
}