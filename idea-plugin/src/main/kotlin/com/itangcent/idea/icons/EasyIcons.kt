package com.itangcent.idea.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @see com.intellij.icons.AllIcons
 */
object EasyIcons {
    val WebFolder = tryLoad("/nodes/webFolder.png") // 16x16

    val Class = tryLoad("/nodes/class.png") // 16x16
    val Method = tryLoad("/nodes/method.png") // 16x16

    val CollapseAll = tryLoad("/general/collapseAll.png") // 11x16

    val Add = tryLoad("/general/add.png") // 16x16
    val Refresh = tryLoad("/actions/refresh.png") // 16x16

    val Link = tryLoad("/ide/link.png") // 12x12

    private fun tryLoad(path: String): Icon? {
        return try {
            IconLoader.getIcon(path)
        } catch (e: Exception) {
            null
        }
    }
}