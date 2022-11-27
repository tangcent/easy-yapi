package com.itangcent.idea.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ReflectionUtil
import com.itangcent.common.logger.Log
import com.itangcent.common.utils.invokeMethod
import com.itangcent.common.utils.safe
import com.itangcent.idea.utils.setSizeIfNecessary
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Dimension
import java.net.URL
import javax.swing.AbstractButton
import javax.swing.Icon

/**
 * @see com.intellij.icons.AllIcons
 */
object EasyIcons : Log() {

    val WebFolder = tryLoad("/nodes/webFolder.png") // 16x16

    val Class = tryLoad("/nodes/class.png") // 16x16

    val Method = tryLoad("/nodes/method.png") // 16x16

    val CollapseAll = tryLoad(
        "/general/collapseAll.png",
        "actions/collapseall.png"
    ) // 11x16

    val Add = tryLoad("/general/add.png") // 16x16

    val Refresh = tryLoad("/actions/refresh.png") // 16x16

    val Link = tryLoad("/ide/link.png") // 12x12

    val Run = tryLoad(
        "/general/run.png",
        "/general/run@2x.png",
        "/runConfigurations/testState/run.png",
        "/runConfigurations/testState/run@2x.png"
    ) // 7x10

    val Module = tryLoad("/nodes/Module.png") // 16x16

    val ModuleGroup = tryLoad("/nodes/moduleGroup.png") // 16x16

    val UpFolder = tryLoad("/nodes/upFolder.png") // 16x16

    val Close = tryLoad(
        "/notification/close.png",
        "/actions/close.png"
    )
        ?: tryLoadByUrl(URL("https://raw.githubusercontent.com/tangcent/easy-yapi/blob/master/assets/close.png"))

    val OK = tryLoad(
        "/general/inspectionsOK.png",
        "/process/state/GreenOK.png"
    )
        ?: tryLoadByUrl(URL("https://raw.githubusercontent.com/tangcent/easy-yapi/blob/master/assets/ok.png"))

    val Export = tryLoad(
        "/toolbarDecorator/export.svg",
        "/toolbarDecorator/export.png",
        "/general/ExportSettings.png",
        "/graph/export.png",
        "/actions/export.png"
    )

    val Import = tryLoad(
        "/toolbarDecorator/import.svg",
        "/toolbarDecorator/import.png",
        "/general/ImportSettings.png",
        "/welcome/importProject.png",
        "/css/import.png"
    )

    private fun tryLoad(vararg paths: String): Icon? {
        for (path in paths) {
            try {
                getIcon(path)?.takeIf {
                    it.iconWidth > 2 && it.iconHeight > 2
                }?.let { return it }
            } catch (_: Exception) {
            }
        }
        LOG.error("non icon be found in ${paths}")
        return null
    }

    private fun getIcon(@NonNls path: String): Icon? {
        val callerClass = ReflectionUtil.getGrandCallerClass()
        if (callerClass == null) {
            debug("getGrandCallerClass failed")
            return null
        }

        return IconLoader.findIcon(path, callerClass)
    }

    private fun tryLoadByUrl(vararg paths: URL): Icon? {
        for (path in paths) {
            try {
                IconLoader.findIcon(path)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }
}

fun Icon?.iconOnly(component: Component?) {
    if (this == null || component == null) {
        return
    }
    safe {
        component.invokeMethod("setIcon", this)
        component.invokeMethod("setText", "")
    }
}

private fun Int.preferredSize(): Int {
    return (this * 1.2).toInt() + 10
}

fun Icon?.iconOnly(component: AbstractButton?, rescale: Boolean = false) {
    if (this == null || component == null) {
        return
    }
    safe {
        component.icon = this
        component.text = ""
        if (rescale) {
            Dimension(this.iconWidth.preferredSize(), this.iconHeight.preferredSize()).let {
                component.preferredSize = it
                component.maximumSize = it
                component.minimumSize = it
                component.setSizeIfNecessary(it.width, it.height)
                LOG.info("update size of $component -> ${it.width} X ${it.height}")
            }
        }
        LOG.info("icon of $component -> ${component.icon}")
    }
}

val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(EasyIcons::class.java)