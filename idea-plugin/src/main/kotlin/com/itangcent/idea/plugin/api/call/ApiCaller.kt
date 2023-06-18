package com.itangcent.idea.plugin.api.call

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import java.lang.ref.WeakReference

class ApiCaller {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private val project: Project? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    fun showCallWindow() {
        project!!.getUserData(API_CALL_UI)?.get()?.let {
            it.focusUI()
            return
        }

        try {
            val requests = classApiExporterHelper.export().mapNotNull { it as? Request }

            if (requests.isEmpty()) {
                logger.info("No api be found to call!")
                return
            }

            val apiCallUI = actionContext.instance(ApiCallUI::class)
            apiCallUI.updateRequestList(requests)
            apiCallUI.showUI()
            val uiWeakReference = WeakReference(apiCallUI)
            project.putUserData(API_CALL_UI, uiWeakReference)
            actionContext.on(EventKey.ON_COMPLETED) {
                project.putUserData(API_CALL_UI, null)
                uiWeakReference.clear()
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
        }
    }

    companion object {
        private val API_CALL_UI = Key.create<WeakReference<ApiCallUI>>("API_CALL_UI")
    }
}