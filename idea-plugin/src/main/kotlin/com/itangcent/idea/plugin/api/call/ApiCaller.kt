package com.itangcent.idea.plugin.api.call

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import java.lang.ref.WeakReference
import java.util.*

class ApiCaller {

    @Inject
    private val logger: Logger? = null

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private val project: Project? = null

    @Inject
    private val classExporter: ClassExporter? = null

    fun showCallWindow() {
        project!!.getUserData(API_CALL_UI)?.get()?.let {
            it.focusUI()
            return
        }

        logger!!.info("Start find apis...")
        val requests: MutableList<Request> = Collections.synchronizedList(ArrayList())

        val boundary = actionContext.createBoundary()
        try {
            SelectedHelper.Builder()
                .classHandle {
                    actionContext.checkStatus()
                    classExporter!!.export(it, requestOnly { request ->
                        requests.add(request)
                    })
                }
                .traversal()
        } catch (e: Exception) {
            logger.traceError("failed export apis!", e)
        }
        try {
            boundary.waitComplete()
            if (requests.isEmpty()) {
                logger.info("No api be found to call!")
                return
            }

            val apiCallUI = actionContext.instance(ApiCallUI::class)
            apiCallUI.updateRequestList(requests)
            apiCallUI.showUI()
            project.putUserData(API_CALL_UI, WeakReference(apiCallUI))
            actionContext.on(EventKey.ON_COMPLETED) {
                project.putUserData(API_CALL_UI, null)
            }
        } catch (e: Exception) {
            logger.traceError("Apis find failed", e)
        }
    }

    companion object {
        private val API_CALL_UI = Key.create<WeakReference<ApiCallUI>>("API_CALL_UI")
    }
}