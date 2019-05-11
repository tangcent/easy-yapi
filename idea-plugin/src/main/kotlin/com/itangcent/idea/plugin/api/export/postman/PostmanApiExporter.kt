package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


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
    private val parseHandle: ParseHandle? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

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
                .classHandle {
                    classExporter!!.export(it, parseHandle!!) { request -> requests.add(request) }
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
                        val postman = parseRequests(requests)
                        requests.clear()
                        actionContext!!.runAsync {
                            try {
                                if (postmanApiHelper!!.hasPrivateToken()) {
                                    logger.info("PrivateToken of postman be found")
                                    if (postmanApiHelper.importApiInfo(postman)) {
                                        return@runAsync
                                    } else {
                                        logger.error("Export to postman failed,You could check below:" +
                                                "1.the network " +
                                                "2.the privateToken")
                                    }
                                } else {
                                    logger.info("PrivateToken of postman not be setting")
                                    logger.info("To enable automatically import to postman you could set privateToken" +
                                            " of host [https://api.getpostman.com] in \"File -> Other Setting -> EasyApiSetting\"")
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
                                logger.info("Apis save failed" + ExceptionUtils.getStackTrace(e))
                            }
                        }
                    } catch (e: Exception) {
                        logger.info("Apis save failed" + ExceptionUtils.getStackTrace(e))
                    }
                }
                .traversal()
    }

    private fun parseRequests(requests: MutableList<Request>): HashMap<String, Any?> {

        //group by class into: {class:requests}
        val clsGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        requests.forEach { request ->
            val resource = request.resource?.let { resourceHelper!!.findResourceClass(it) } ?: NULL_RESOURCE
            clsGroupedMap.computeIfAbsent(resource) { ArrayList() }
                    .add(postmanFormatter!!.request2Item(request))
        }

        //only one class
        if (clsGroupedMap.size == 1) {
            clsGroupedMap.entries.first()
                    .let {
                        val module = moduleHelper!!.findModule(it.key) ?: "easy-api"
                        return postmanFormatter!!.wrapRootInfo(module, arrayListOf(postmanFormatter.wrapInfo(it.key, it.value)))
                    }
        }

        //group by module
        val moduleGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        clsGroupedMap.forEach { cls, items ->
            val module = moduleHelper!!.findModule(cls) ?: "easy-api"
            moduleGroupedMap.computeIfAbsent(module) { ArrayList() }
                    .add(postmanFormatter!!.wrapInfo(cls, items))
        }


        //only one module
        if (moduleGroupedMap.size == 1) {
            moduleGroupedMap.entries.first()
                    .let {
                        return postmanFormatter!!.wrapRootInfo(it.key, arrayListOf(postmanFormatter.wrapInfo(it.key, it.value)))
                    }
        }

        val modules: ArrayList<HashMap<String, Any?>> = ArrayList()
        moduleGroupedMap.entries
                .map { postmanFormatter!!.wrapInfo(it.key, arrayListOf(postmanFormatter.wrapRootInfo(it.key, it.value))) }
                .forEach { modules.add(it) }

        val rootModule = moduleHelper!!.findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return postmanFormatter!!.wrapRootInfo("$rootModule-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}", modules)
    }


    companion object {
        val NULL_RESOURCE = Object()
    }
}