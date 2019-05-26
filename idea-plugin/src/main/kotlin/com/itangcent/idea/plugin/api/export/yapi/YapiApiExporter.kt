package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.DocCommentUtils
import org.apache.commons.lang3.StringUtils


class YapiApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val yapiApiHelper: YapiApiHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val parseHandle: ParseHandle? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Inject
    private val yapiFormatter: YapiFormatter? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null


    //cls -> CartInfo
    private val clsCartMap: HashMap<PsiClass, CartInfo> = HashMap()

    @Inject
    private val project: Project? = null

    fun export() {
        val serverFound = yapiApiHelper!!.findServer() != null
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

    private fun getCartForCls(psiClass: PsiClass): CartInfo? {

        var cartId = clsCartMap[psiClass]
        if (cartId != null) return cartId
        synchronized(clsCartMap)
        {
            cartId = clsCartMap[psiClass]
            if (cartId != null) return cartId

            return buildCartForCls(psiClass)
        }

    }

    private var tryInputTokenOfModule: HashSet<String> = HashSet()

    private fun buildCartForCls(psiClass: PsiClass): CartInfo? {

        val module = moduleHelper!!.findModule(psiClass) ?: return null

        if (!yapiApiHelper!!.hasPrivateToken(module)) {
//            logger!!.info("no token be found for module:$module")
            if (tryInputTokenOfModule.contains(module)) {
                return null
            } else {
                tryInputTokenOfModule.add(module)
                val modulePrivateToken = actionContext!!.callInSwingUI {
                    return@callInSwingUI Messages.showInputDialog(project, "Input Private Token Of Module:$module",
                            "Yapi Private Token", Messages.getInformationIcon())
                }
                if (!modulePrivateToken.isNullOrBlank()) {
                    yapiApiHelper.setToken(module, modulePrivateToken)
                } else {
                    return null
                }
            }
        }
        val privateToken = yapiApiHelper.getPrivateToken(module)
        if (privateToken == null) {
            logger!!.info("No token be found for $module")
            return null
        }

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

        var cartId = yapiApiHelper.findCat(privateToken, name!!)
        if (cartId == null) {
            if (yapiApiHelper.addCart(module, name, desc)) {
                cartId = yapiApiHelper.findCat(privateToken, name)
            } else {
                //failed
                return null
            }
        }

        val cartInfo = CartInfo()
        cartInfo.cartId = cartId
        cartInfo.cartName = name
        cartInfo.privateToken = yapiApiHelper.getPrivateToken(module)

        clsCartMap[psiClass] = cartInfo
        return cartInfo
    }

    private fun exportRequest(request: Request) {
        if (request.resource == null) return
        val findResourceClass = resourceHelper!!.findResourceClass(request.resource!!) ?: return
        val cartInfo = getCartForCls(findResourceClass) ?: return
        val request2Item = yapiFormatter!!.request2Item(request)
        request2Item["token"] = cartInfo.privateToken
        request2Item["catid"] = cartInfo.cartId
        yapiApiHelper!!.saveApiInfo(request2Item)
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls, parseHandle!!)
        }
    }

    class CartInfo {
        var cartId: String? = null
        var cartName: String? = null
        var privateToken: String? = null
    }

    companion object {
        val NULL_RESOURCE = Object()
    }
}