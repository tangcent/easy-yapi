package com.itangcent.idea.icons

import com.intellij.util.ReflectionUtil
import com.itangcent.common.logger.Log
import com.itangcent.common.spi.SpiUtils
import com.itangcent.common.utils.asUrl
import com.itangcent.common.utils.invokeMethod
import com.itangcent.common.utils.safe
import com.itangcent.idea.sqlite.encodeBase64
import com.itangcent.idea.utils.setSizeIfNecessary
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.DefaultLocalFileRepository
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
        "/nodes/webFolder.svg",
        "/nodes/folder.svg",
        "/assets/webFolder.svg",
    ) // 16x16

    val Class by lazyLoad(
        "/nodes/class.svg",
        "/assets/class.svg"
    )  // 16x16

    val Method by lazyLoad(
        "/nodes/method.svg",
        "/assets/method.svg",
    )// 16x16

    val CollapseAll by lazyLoad(
        "/general/collapseAll.png",
        "/actions/collapseall.svg",
        "/assets/collapseall.svg"
    ) // 11x16

    val Add by lazyLoad(
        "/general/add.svg",
        "/assets/add.svg"
    ) // 16x16

    val Remove by lazyLoad(
        "/general/remove.svg",
        "/assets/remove.svg"
    ) // 16x16

    val Refresh by lazyLoad(
        "/actions/refresh.svg",
        "/assets/refresh.svg",
    ) // 16x16

    val Link by lazyLoad(
        "/ide/link.svg",
        "/assets/link.svg"
    ) // 12x12

    val Run by lazyLoad(
        "/general/run.png",
        "/general/run@2x.png",
        "/runConfigurations/testState/run.svg",
        "/assets/run.svg"
    ) // 7x10

    val Reset by lazy {
        tryLoad(
            "/general/reset.svg",
            "/general/reset.png",
        ) ?: tryLoadByUrl("https://raw.githubusercontent.com/tangcent/easy-yapi/master/assets/reset.svg")
    } // 16x16

    val Module by lazyLoad(
        "/nodes/Module.svg",
        "/assets/module.svg"
    ) // 16x16

    val ModuleGroup by lazyLoad(
        "/nodes/moduleGroup.svg",
        "/assets/moduleGroup.svg"
    ) // 16x16

    val UpFolder by lazyLoad(
        "/nodes/upFolder.svg",
        "/assets/upFolder.svg",
    ) // 16x16

    val Close by lazy {
        tryLoad(
            "/notification/close.png",
            "/actions/close.svg",
            "/assets/close.svg"
        ) ?: tryLoadByUrl("https://raw.githubusercontent.com/tangcent/easy-yapi/master/assets/close.png")
    }

    val OK by lazy {
        tryLoad(
            "/general/inspectionsOK.svg",
            "/process/state/GreenOK.png",
            "/assets/ok.svg",
        ) ?: tryLoadByUrl("https://raw.githubusercontent.com/tangcent/easy-yapi/master/assets/ok.png")
    }

    val Export by lazyLoad(
        "/toolbarDecorator/export.svg",
        "/general/ExportSettings.png",
        "/graph/export.png",
        "/actions/export.png",
        "/assets/export.svg"
    )

    val Import by lazyLoad(
        "/toolbarDecorator/import.svg",
        "/general/ImportSettings.png",
        "/welcome/importProject.png",
        "/css/import.png",
        "/assets/import.svg"
    )

    private fun lazyLoad(vararg paths: String): Lazy<Icon?> = lazy { tryLoad(*paths) }

    private fun tryLoad(vararg paths: String): Icon? {
        for (path in paths) {
            try {
                getIcon(path)
                    ?.takeIf {
                        it.iconWidth > 2 && it.iconHeight > 2
                    }
                    ?.let {
                        LOG.info("load icon: $path")
                        return it
                    }
            } catch (_: Exception) {
            }
        }
        LOG.error("non icon be found in ${paths.contentToString()}")
        return null
    }

    private fun getIcon(@NonNls path: String): Icon? {
        val callerClass = ReflectionUtil.getGrandCallerClass() ?: this::class.java
        return iconLoader.findIcon(path, callerClass)
    }

    private fun tryLoadByUrl(vararg urls: String): Icon? {
        return tryLoadByUrl(*urls.map { it.asUrl() }.toTypedArray())
    }

    private fun tryLoadByUrl(vararg urls: URL): Icon? {
        for (path in urls) {
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
        url ?: return null
        return com.intellij.openapi.util.IconLoader.findIcon(tryLoadCache(url))
    }

    private fun tryLoadCache(url: URL): URL {
        if (url.protocol == "file") {
            return url
        }
        val context = ActionContext.getContext() ?: return url
        val localFileRepository = context.instance(DefaultLocalFileRepository::class)
        val urlStr = url.toString()
        val suffix = urlStr.substringAfterLast('.')
        val cachedFile = "${urlStr.encodeToByteArray().encodeBase64()}.$suffix"
        localFileRepository.getFile(cachedFile)?.toURI()?.toURL()?.let {
            return it
        }

        val content = url.openConnection()
            .also {
                it.connectTimeout = 10000
                it.readTimeout = 10000
            }
            .getInputStream().use { it.readBytes() }
        val file = localFileRepository.getOrCreateFile(cachedFile)
        file.writeBytes(content)
        return file.toURI().toURL()
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