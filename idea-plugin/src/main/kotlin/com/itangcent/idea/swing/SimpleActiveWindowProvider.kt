package com.itangcent.idea.swing

import com.google.inject.Singleton
import java.awt.Component

@Singleton
class SimpleActiveWindowProvider : MutableActiveWindowProvider {

    private var activeWindow: Component? = null

    override fun setActiveWindow(activeWindow: Component?) {
        this.activeWindow = activeWindow
    }

    override fun activeWindow(): Component? {
        return this.activeWindow
    }
}