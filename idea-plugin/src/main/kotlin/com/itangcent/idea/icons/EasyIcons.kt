package com.itangcent.idea.icons

import com.intellij.util.ReflectionUtil
import com.itangcent.common.logger.Log
import com.itangcent.common.spi.SpiUtils
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

    private val iconLoader: IconLoader by lazy {
        SpiUtils.loadService(IconLoader::class) ?: DefaultIconLoader
    }

    val WebFolder by lazyLoad(
        "/nodes/webFolder.png",
        "nodes/folder.png"
    ) // 16x16

    val Class by lazyLoad("/nodes/class.png")  // 16x16

    val Method by lazyLoad("/nodes/method.png")// 16x16

    val CollapseAll by lazyLoad(
        "/general/collapseAll.png",
        "actions/collapseall.png"
    ) // 11x16

    val Add by lazyLoad("/general/add.png") // 16x16

    val Remove by lazyLoad("/general/remove.png") // 16x16

    val Refresh by lazyLoad("/actions/refresh.png") // 16x16

    val Link by lazyLoad("/ide/link.png") // 12x12

    val Run by lazyLoad(
        "/general/run.png",
        "/general/run@2x.png",
        "/runConfigurations/testState/run.png",
        "/runConfigurations/testState/run@2x.png"
    ) // 7x10

    val Module by lazyLoad("/nodes/Module.png") // 16x16

    val ModuleGroup by lazyLoad("/nodes/moduleGroup.png") // 16x16

    val UpFolder by lazyLoad("/nodes/upFolder.png") // 16x16

    val Close by lazy {
        tryLoad(
            "/notification/close.png",
            "/actions/close.png"
        ) ?: tryLoadByUrl(URL("https://raw.githubusercontent.com/tangcent/easy-yapi/blob/master/assets/close.png"))
    }

    val OK by lazy {
        tryLoad(
            "/general/inspectionsOK.png",
            "/process/state/GreenOK.png"
        ) ?: tryLoadByUrl(URL("https://raw.githubusercontent.com/tangcent/easy-yapi/blob/master/assets/ok.png"))
    }

    val Export by lazyLoad(
        "/toolbarDecorator/export.svg",
        "/toolbarDecorator/export.png",
        "/general/ExportSettings.png",
        "/graph/export.png",
        "/actions/export.png"
    )

    val Import by lazyLoad(
        "/toolbarDecorator/import.svg",
        "/toolbarDecorator/import.png",
        "/general/ImportSettings.png",
        "/welcome/importProject.png",
        "/css/import.png"
    )

    private fun lazyLoad(vararg paths: String): Lazy<Icon?> = lazy { tryLoad(*paths) }

    private fun tryLoad(vararg paths: String): Icon? {
        for (path in paths) {
            try {
                getIcon(path)
                    ?.takeIf {
                        it.iconWidth > 2 && it.iconHeight > 2
                    }
                    ?.let { return it }
            } catch (_: Exception) {
            }
        }
        LOG.error("non icon be found in $paths")
        return null
    }

    private fun getIcon(@NonNls path: String): Icon? {
        val callerClass = ReflectionUtil.getGrandCallerClass() ?: this::class.java
        return iconLoader.findIcon(path, callerClass)
    }

    private fun tryLoadByUrl(vararg paths: URL): Icon? {
        for (path in paths) {
            try {
                iconLoader.findIcon(path)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }
}


interface IconLoader {
    fun findIcon(path: String, aClass: Class<*>): Icon?
    fun findIcon(url: URL?): Icon?
}

object DefaultIconLoader : IconLoader {
    override fun findIcon(path: String, aClass: Class<*>): Icon? {
        return com.intellij.openapi.util.IconLoader.findIcon(path, aClass)
    }

    override fun findIcon(url: URL?): Icon? {
        return com.intellij.openapi.util.IconLoader.findIcon(url)
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