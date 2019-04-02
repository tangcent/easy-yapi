package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.SettingDialog
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.setting.DefaultSettingManager
import com.itangcent.intellij.setting.SettingManager
import com.itangcent.intellij.util.UIUtils

class SettingAction : KotlinAnAction() {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(SettingManager::class) { it.with(DefaultSettingManager::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        val gitSettingDialog = actionContext.instance { SettingDialog() }
        UIUtils.show(gitSettingDialog)
    }
}
