package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.common.exporter.RequestHelper
import com.itangcent.common.model.Request
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.requestOnly
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.traceError
import org.apache.commons.lang3.StringUtils
import java.util.*
import kotlin.collections.ArrayList


@Singleton
class PostmanApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val postmanApiHelper: PostmanApiHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val requestHelper: RequestHelper? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val postmanFormatter: PostmanFormatter? = null

    fun export() {
        logger!!.info("Start find apis...")
        val requests: MutableList<Request> = Collections.synchronizedList(ArrayList<Request>())

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
                .fileFilter { file -> file.name.endsWith(".java") }
                .classHandle {
                    classExporter!!.export(it, requestHelper!!, requestOnly { request -> requests.add(request) })
                }
                .onCompleted {
                    try {
                        if (classExporter is Worker) {
                            classExporter.waitCompleted()
                        }
                        if (requests.isEmpty()) {
                            logger.info("No api be found to export!")
                            return@onCompleted
                        }
                        val postman = postmanFormatter!!.parseRequests(requests)
                        requests.clear()
                        actionContext!!.runAsync {
                            try {
                                if (postmanApiHelper!!.hasPrivateToken()) {
                                    logger.info("PrivateToken of postman be found")
                                    val createdCollection = postmanApiHelper.createCollection(postman)

                                    if (!createdCollection.isNullOrEmpty()) {
                                        val collectionName = createdCollection.get("name")?.toString()
                                        if (StringUtils.isNotBlank(collectionName)) {
                                            logger.info("Imported as collection:$collectionName")
                                            return@runAsync
                                        }
                                    }

                                    logger.error("Export to postman failed,You could check below:" +
                                            "1.the network " +
                                            "2.the privateToken")

                                } else {
                                    logger.info("PrivateToken of postman not be setting")
                                    logger.info("To enable automatically import to postman you could set privateToken of postman" +
                                            "in \"Preference -> Other Setting -> EasyApi\"")
                                    logger.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                                            " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
                                }
                                fileSaveHelper!!.saveOrCopy(GsonUtils.prettyJson(postman), {
                                    logger.info("Exported data are copied to clipboard,you can paste to postman now")
                                }, {
                                    logger.info("Apis save success")
                                }) {
                                    logger.info("Apis save failed")
                                }
                            } catch (e: Exception) {
                                logger.error("Apis save failed")
                                logger.traceError(e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Apis save failed")
                        logger.traceError(e)
                    }
                }
                .traversal()
    }

}