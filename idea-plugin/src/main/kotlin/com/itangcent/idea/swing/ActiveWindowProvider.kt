package com.itangcent.idea.swing

import java.awt.Component

interface ActiveWindowProvider {
    fun activeWindow(): Component?
}

interface MutableActiveWindowProvider : ActiveWindowProvider {
    fun setActiveWindow(activeWindow: Component?)
}