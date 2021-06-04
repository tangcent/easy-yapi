package com.itangcent.idea.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ReflectionUtil
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.utils.invokeMethod
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.net.URL
import javax.swing.AbstractButton
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

    val Run = tryLoad(
        "/general/run.png",
        "/runConfigurations/testState/run.png"
    ) // 7x10

    val Module = tryLoad("/nodes/Module.png") // 16x16

    val ModuleGroup = tryLoad("/nodes/moduleGroup.png") // 16x16

    val UpFolder = tryLoad("/nodes/upFolder.png") // 16x16

    val Close = tryLoad(
        "/notification/close.png",
        "/actions/close.png"
    )
        ?: tryLoadByUrl(URL("https://github.com/tangcent/easy-api/blob/master/assets/close.png"))

    val OK = tryLoad(
        "/general/inspectionsOK.png",
        "/process/state/GreenOK.png"
    )
        ?: tryLoadByUrl(URL("https://github.com/tangcent/easy-api/blob/master/assets/ok.png"))

    val Export = tryLoad(
        "/actions/export.png",
        "/graph/export.png",
        "/general/ExportSettings.png"

    )

    val Import = tryLoad(
        "/css/import.png",
        "/toolbarDecorator/import.png",
        "/welcome/importProject.png",
        "/general/ImportSettings.png"
    )

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

    private fun tryLoadByUrl(vararg paths: URL): Icon? {
        for (path in paths) {
            try {
                IconLoader.findIcon(path)?.let { return it }
            } catch (e: Exception) {
            }
        }
        return null
    }

}

fun Icon?.iconOnly(component: Component?) {
    if (this == null || component == null) {
        return
    }
    KitUtils.safe { component.invokeMethod("setIcon", this) }
    KitUtils.safe { component.invokeMethod("setText", "") }
}

fun Icon?.iconOnly(component: AbstractButton?) {
    if (this == null || component == null) {
        return
    }
    KitUtils.safe { component.icon = this }
    KitUtils.safe { component.text = "" }
}