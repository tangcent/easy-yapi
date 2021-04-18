package com.itangcent.idea.plugin.api.call

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.dialog.ApiCallDialog
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.UIUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.lang.ref.WeakReference
import java.util.*

class ApiCaller {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val project: Project? = null

    @Inject
    private val classExporter: ClassExporter? = null

    fun showCallWindow() {

        var apiCallDialog = project!!.getUserData(API_CALL_DIALOG)?.get()
        if (apiCallDialog != null) {
            SwingUtils.focus(apiCallDialog)
            return
        }

        logger!!.info("Start find apis...")
        val requests: MutableList<Request> = Collections.synchronizedList(ArrayList<Request>())

        SelectedHelper.Builder()
                .classHandle {
                    actionContext!!.checkStatus()
                    classExporter!!.export(it, requestOnly { request ->
                        requests.add(request)
                    })
                }
                .onCompleted {
                    try {
                        if (classExporter is Worker) {
                            classExporter.waitCompleted()
                        }
                        if (requests.isEmpty()) {
                            logger.info("No api be found to call!")
                            return@onCompleted
                        }

                        apiCallDialog = actionContext!!.instance { ApiCallDialog() }
                        apiCallDialog!!.updateRequestList(requests)
                        UIUtils.show(apiCallDialog!!)
                        project.putUserData(API_CALL_DIALOG, WeakReference(apiCallDialog!!))
                        actionContext.on(EventKey.ON_COMPLETED) {
                            project.putUserData(API_CALL_DIALOG, null)
                        }
                    } catch (e: Exception) {
                        logger.info("Apis find failed" + ExceptionUtils.getStackTrace(e))
                    }
                }
                .traversal()
    }

    companion object {
        private val API_CALL_DIALOG = Key.create<WeakReference<ApiCallDialog>>("API_CALL_DIALOG")
    }
}