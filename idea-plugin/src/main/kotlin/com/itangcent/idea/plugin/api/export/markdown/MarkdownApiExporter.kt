package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.FileType
import java.util.*

@Singleton
class MarkdownApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val markdownFormatter: MarkdownFormatter? = null

    @Inject
    private val settingBinder: SettingBinder? = null

    fun export() {

        logger!!.info("Start find apis...")
        val docs: MutableList<Doc> = Collections.synchronizedList(ArrayList<Doc>())

        SelectedHelper.Builder()
                .dirFilter { dir, callBack ->
                    actionContext!!.runInSwingUI {
                        try {
                            val project = actionContext.instance(Project::class)
                            val yes = Messages.showYesNoDialog(project,
                                    "Export the api in directory [${ActionUtils.findCurrentPath(dir)}]?",
                                    "Are you sure",
                                    Messages.getQuestionIcon())
                            if (yes == Messages.YES) {
                                callBack(true)
                            } else {
                                logger.debug("Cancel the operation export api from [${ActionUtils.findCurrentPath(dir)}]!")
                                callBack(false)
                            }
                        } catch (e: Exception) {
                            callBack(false)
                        }
                    }
                }
                .fileFilter { file -> FileType.acceptable(file.name) }
                .classHandle {
                    actionContext!!.checkStatus()
                    classExporter!!.export(it) { doc -> docs.add(doc) }
                }
                .onCompleted {
                    try {
                        if (classExporter is Worker) {
                            classExporter.waitCompleted()
                        }
                        if (docs.isEmpty()) {
                            logger.info("No api be found to export!")
                            return@onCompleted
                        }
                        logger.info("Start parse apis")
                        val apiInfo = markdownFormatter!!.parseRequests(docs)
                        docs.clear()
                        actionContext!!.runAsync {
                            try {
                                fileSaveHelper!!.saveOrCopy(apiInfo, Charsets.forName(settingBinder!!.read().outputCharset)?.charset()
                                        ?: kotlin.text.Charsets.UTF_8, {
                                    logger.info("Exported data are copied to clipboard,you can paste to a md file now")
                                }, {
                                    logger.info("Apis save success: $it")
                                }) {
                                    logger.info("Apis save failed")
                                }
                            } catch (e: Exception) {
                                logger.traceError("Apis save failed", e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.traceError("Apis save failed", e)

                    }
                }
                .traversal()
    }
}