package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import java.awt.Component

@Singleton
class DefaultYapiApiInputHelper : YapiApiInputHelper {

    @Inject
    private val settingBinder: SettingBinder? = null

    @Inject
    protected val yapiApiHelper: YapiApiHelper? = null

    @Inject
    protected val actionContext: ActionContext? = null

    @Inject
    protected val project: Project? = null

    @Inject
    protected val logger: Logger? = null

    override fun inputServer(): String? {
        return actionContext!!.callInSwingUI {
            val yapiServer = Messages.showInputDialog(project, "Input server of yapi",
                    "server of yapi", Messages.getInformationIcon())
            if (yapiServer.isNullOrBlank()) {
                logger!!.info("No yapi server")
                return@callInSwingUI null
            }

            yapiApiHelper!!.setYapiServer(yapiServer)
            return@callInSwingUI yapiServer
        }
    }

    override fun inputServer(parent: Component): String? {
        return actionContext!!.callInSwingUI {
            val yapiServer = Messages.showInputDialog(parent, "Input server of yapi",
                    "server of yapi", Messages.getInformationIcon())
            if (yapiServer.isNullOrBlank()) {
                logger!!.info("No yapi server")
                return@callInSwingUI null
            }

            yapiApiHelper!!.setYapiServer(yapiServer)
            return@callInSwingUI yapiServer
        }
    }

    override fun inputServer(next: (String?) -> Unit) {
        actionContext!!.runAsync {
            Thread.sleep(200)
            actionContext.runInSwingUI {
                val yapiServer = Messages.showInputDialog(project, "Input server of yapi",
                        "server of yapi", Messages.getInformationIcon())
                if (yapiServer.isNullOrBlank()) {
                    logger!!.info("No yapi server")
                    return@runInSwingUI
                }

                yapiApiHelper!!.setYapiServer(yapiServer)
                next(yapiServer)
            }
        }

    }

    override fun inputServer(parent: Component, next: (String?) -> Unit) {
        actionContext!!.runAsync {
            Thread.sleep(200)
            actionContext.runInSwingUI {
                val yapiServer = Messages.showInputDialog(parent, "Input server of yapi",
                        "server of yapi", Messages.getInformationIcon())
                if (yapiServer.isNullOrBlank()) {
                    logger!!.info("No yapi server")
                    return@runInSwingUI
                }
                yapiApiHelper!!.setYapiServer(yapiServer)
                next(yapiServer)
            }
        }

    }

    override fun inputToken(): String? {

        if (loginMode()) {
            val projectId = actionContext!!.callInSwingUI {
                return@callInSwingUI Messages.showInputDialog(project,
                        "Input ProjectId",
                        "Yapi ProjectId", Messages.getInformationIcon())
            }
            if (projectId.isNullOrBlank()) {
                return null
            }
            return projectId
        }

        val modulePrivateToken = actionContext!!.callInSwingUI {
            return@callInSwingUI Messages.showInputDialog(project, "Input Private Token",
                    "Yapi Private Token", Messages.getInformationIcon())
        }
        if (modulePrivateToken.isNullOrBlank()) {
            return null
        }

        return modulePrivateToken
    }

    override fun inputToken(parent: Component): String? {

        if (loginMode()) {
            val projectId = actionContext!!.callInSwingUI {
                return@callInSwingUI Messages.showInputDialog(parent,
                        "Input ProjectId",
                        "Yapi ProjectId", Messages.getInformationIcon())
            }
            if (projectId.isNullOrBlank()) {
                return null
            }
            return projectId
        }

        val modulePrivateToken = actionContext!!.callInSwingUI {
            return@callInSwingUI Messages.showInputDialog(parent, "Input Private Token",
                    "Yapi Private Token", Messages.getInformationIcon())
        }

        if (modulePrivateToken.isNullOrBlank()) {
            return null
        }
        return modulePrivateToken
    }

    private var tryInputTokenOfModule: HashSet<String> = HashSet()

    override fun inputToken(module: String): String? {

        //return null if user had cancel or ignore of input token of this module
        if (!tryInputTokenOfModule.add(module)) {
            return null
        }

        tryInputTokenOfModule.add(module)

        if (loginMode()) {
            val projectId = actionContext!!.callInSwingUI {
                return@callInSwingUI Messages.showInputDialog(project,
                        "Input ProjectId Of Module:$module",
                        "Yapi ProjectId", Messages.getInformationIcon())
            }
            if (projectId.isNullOrBlank()) {
                return null
            }
            yapiApiHelper!!.setToken(module, projectId)
            return projectId
        }

        val modulePrivateToken = actionContext!!.callInSwingUI {
            return@callInSwingUI Messages.showInputDialog(project, "Input Private Token Of Module:$module",
                    "Yapi Private Token", Messages.getInformationIcon())
        }
        if (modulePrivateToken.isNullOrBlank()) {
            return null
        }

        yapiApiHelper!!.setToken(module, modulePrivateToken)
        return modulePrivateToken
    }

    override fun inputToken(parent: Component, module: String): String? {

        //return null if user had cancel or ignore of input token of this module
        if (!tryInputTokenOfModule.add(module)) {
            return null
        }

        tryInputTokenOfModule.add(module)

        if (loginMode()) {
            val projectId = actionContext!!.callInSwingUI {
                return@callInSwingUI Messages.showInputDialog(parent,
                        "Input ProjectId Of Module:$module",
                        "Yapi ProjectId", Messages.getInformationIcon())
            }
            if (projectId.isNullOrBlank()) {
                return null
            }
            yapiApiHelper!!.setToken(module, projectId)
            return projectId
        }

        val modulePrivateToken = actionContext!!.callInSwingUI {
            return@callInSwingUI Messages.showInputDialog(parent, "Input Private Token Of Module:$module",
                    "Yapi Private Token", Messages.getInformationIcon())
        }

        if (modulePrivateToken.isNullOrBlank()) {
            return null
        }
        yapiApiHelper!!.setToken(module, modulePrivateToken)
        return modulePrivateToken
    }

    private fun loginMode(): Boolean {
        return settingBinder!!.read().loginMode
    }
}