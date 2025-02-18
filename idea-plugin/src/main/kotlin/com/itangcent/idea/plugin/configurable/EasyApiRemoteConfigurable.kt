package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.EasyApiSettingRemoteConfigGUI
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.idea.swing.SimpleActiveWindowProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class EasyApiRemoteConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable.Remote"
    }

    override fun getDisplayName(): String {
        return "Remote"
    }

    override fun createGUI(): EasyApiSettingGUI {
        return EasyApiSettingRemoteConfigGUI()
    }

    override fun afterBuildActionContext(builder: ActionContextBuilder) {
        super.afterBuildActionContext(builder)
        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(ActiveWindowProvider::class) { it.with(SimpleActiveWindowProvider::class).singleton() }
    }
}