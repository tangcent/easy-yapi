package com.itangcent.idea.plugin.support

import com.google.inject.ImplementedBy
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.components.ServiceManager

@ImplementedBy(DefaultIdeaSupport::class)
interface IdeaSupport {
    fun openUrl(url: String)
}

class DefaultIdeaSupport : IdeaSupport {
    override fun openUrl(url: String) {
        ServiceManager.getService(BrowserLauncher::class.java)
            .open(url)
    }

}