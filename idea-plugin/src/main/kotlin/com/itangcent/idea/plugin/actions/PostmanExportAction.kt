package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanConfigReader
import com.itangcent.idea.plugin.api.export.postman.PostmanExporter
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ClassRuleConfig
import com.itangcent.intellij.psi.DefaultClassRuleConfig
import com.itangcent.intellij.setting.ReadOnlySettingManager
import com.itangcent.intellij.setting.SettingManager
import com.itangcent.intellij.util.ToolUtils
import org.apache.commons.lang3.exception.ExceptionUtils

class PostmanExportAction : ApiExportAction("Export Postman") {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(SettingManager::class) { it.with(ReadOnlySettingManager::class).singleton() }
        builder.bind(PostmanApiHelper::class) { it.singleton() }
        builder.bind(PostmanExporter::class) { it.singleton() }
        builder.bind(ClassRuleConfig::class) { it.with(DefaultClassRuleConfig::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(PostmanConfigReader::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {

        val logger: Logger? = actionContext.instance(Logger::class)

        actionContext.runInReadUI {
            try {
                logger!!.info("Start find apis...")
                val exportedPostman = actionContext.instance(PostmanExporter::class).export()
                if (exportedPostman == null) {
                    logger.info("No api be found to export!")
                } else {
                    val exportedPostmanStr = GsonUtils.prettyJson(exportedPostman)
                    ToolUtils.copy2Clipboard(exportedPostmanStr)
                    val postManApiHelper: PostmanApiHelper = actionContext.instance(PostmanApiHelper::class)
                    val hasPrivateToken: Boolean = postManApiHelper.hasPrivateToken()
                    if (hasPrivateToken) {
                        logger.info("PrivateToken of postman be found")
                        if (postManApiHelper.importApiInfo(exportedPostman)) {
                            logger.info("Export to postman success")
                            logger.info("To disable automatically import to postman you could remove privateToken" +
                                    " of host [https://api.getpostman.com] in \"File -> Other Setting -> EasyApiSetting \"")
                        } else {
                            logger.error("Export to postman failed,You could check below:" +
                                    "1.the network " +
                                    "2.the privateToken")

                            saveOrCopy(project, exportedPostmanStr, {
                                logger.info("Exported data are copied to clipboard,you can paste to postman now")
                            }, {
                                logger.info("Apis save success")
                            }, {
                                logger.info("Apis save failed")
                            })
                        }
                    } else {
                        logger.info("PrivateToken of postman not be setting")
                        saveOrCopy(project, exportedPostmanStr, {
                            logger.info("Exported data are copied to clipboard,you can paste to postman now")
                        }, {
                            logger.info("Apis save success")
                        }, {
                            logger.info("Apis save failed")
                        })
                        logger.info("To enable automatically import to postman you could set privateToken" +
                                " of host [https://api.getpostman.com] in \"File -> Other Setting -> EasyApiSetting \"")
                        logger.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                                " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
                    }
                }
            } catch (e: Exception) {
                logger!!.error("Error export api to postman:" + ExceptionUtils.getStackTrace(e))
            }
        }
    }

    override fun defaultExportedFile(): String {
        return "postman.json"
    }

    override fun lastImportedLocation(): String {
        return "com.itangcent.postman.export.path"
    }

}