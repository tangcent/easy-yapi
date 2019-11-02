package com.itangcent.idea.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.NonNls
import java.io.File
import javax.swing.Icon

/**
 * @see com.intellij.icons.AllIcons
 */
object EasyIcons {
    val WebFolder = tryLoad("${File.separator}nodes${File.separator}webFolder.png") // 16x16

    val Class = tryLoad("${File.separator}nodes${File.separator}class.png") // 16x16
    val Method = tryLoad("${File.separator}nodes${File.separator}method.png") // 16x16

    val CollapseAll = tryLoad("${File.separator}general${File.separator}collapseAll.png") // 11x16

    val Add = tryLoad("${File.separator}general${File.separator}add.png") // 16x16
    val Refresh = tryLoad("${File.separator}actions${File.separator}refresh.png") // 16x16

    val Link = tryLoad("${File.separator}ide${File.separator}link.png") // 12x12

    val Run = tryLoad("${File.separator}general${File.separator}run.png") // 7x10

    val Module = tryLoad("${File.separator}nodes${File.separator}Module.png") // 16x16

    val ModuleGroup = tryLoad("${File.separator}nodes${File.separator}moduleGroup.png") // 16x16

    val UpFolder = tryLoad("${File.separator}nodes${File.separator}upFolder.png") // 16x16

    val Close = tryLoad("${File.separator}notification${File.separator}close.png",
            "${File.separator}actions${File.separator}close.png") // 16x16

    val OK = tryLoad("${File.separator}general${File.separator}inspectionsOK.png",
            "${File.separator}process${File.separator}state/GreenOK.png") // 16x16

    private fun tryLoad(vararg paths: String): Icon? {
        for (path in paths) {
            try {
                getIcon(path)?.let { return it }
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun getIcon(@NonNls path: String): Icon? {
        val callerClass = ReflectionUtil.getGrandCallerClass() ?: error(path)

        return IconLoader.findIcon(path, callerClass)
    }
}