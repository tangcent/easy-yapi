package com.itangcent.idea.plugin.api.dashboard

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.dialog.ApiDashboardDialog
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.UIUtils

@Singleton
class ApiDashBoard {

    @Inject
    private val actionContext: ActionContext? = null

    fun showDashBoardWindow() {
        val apiDashboardDialog = actionContext!!.instance { ApiDashboardDialog() }
        UIUtils.show(apiDashboardDialog)
    }
}