package com.itangcent.idea.plugin.script

import com.intellij.openapi.actionSystem.DataContext
import com.itangcent.intellij.logger.Logger

interface ActionExtLoader {
    fun loadActionExt(dataContext: DataContext, action: String, logger: Logger): ActionExt?
}