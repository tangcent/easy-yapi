package com.itangcent.idea.plugin.script

import com.itangcent.intellij.context.ActionContext

interface ActionExt {

    fun init(builder: ActionContext.ActionContextBuilder)
}