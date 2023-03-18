package com.itangcent.test

import com.itangcent.common.logger.Log
import com.itangcent.idea.icons.DefaultIconLoader
import com.itangcent.idea.icons.IconLoader
import java.awt.Component
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.image.RenderedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.Icon

class IconLoaderHeadlessSupport : IconLoader {

    companion object : Log()

    private val isActivated = !GraphicsEnvironment.isHeadless()

    override fun findIcon(path: String, aClass: Class<*>): Icon? {
        if (isActivated) return DefaultIconLoader.findIcon(path, aClass)
        val url = resolve(path, aClass.classLoader, aClass)
        return findIcon(url)
    }

    override fun findIcon(url: URL?): Icon? {
        if (url == null) {
            return null
        }
        val image = ImageIO.read(url)
        return HeadlessIcon(image)
    }

    private fun resolve(
        path: String?,
        classLoader: ClassLoader?,
        ownerClass: Class<*>?,
    ): URL? {
        var resolvedPath = path
        var url: URL? = null
        if (resolvedPath != null) {
            if (classLoader != null) {
                // paths in ClassLoader getResource must not start with "/"
                resolvedPath = if (resolvedPath[0] == '/') resolvedPath.substring(1) else resolvedPath
                url = findUrl(resolvedPath) { classLoader.getResource(it) }
            }
            if (url == null && ownerClass != null) {
                // some plugins use findIcon("icon.png",IconContainer.class)
                url = findUrl(resolvedPath) { ownerClass.getResource(it) }
            }
        }
        if (url == null) {
            LOG.warn("Can't find icon in '$resolvedPath' near $classLoader")
        }
        return url
    }

    private fun findUrl(path: String, urlProvider: (String) -> URL?): URL? {
        var path = path
        val url = urlProvider(path)
        if (url != null) {
            return url
        }

        // Find either PNG or SVG icon. The icon will then be wrapped into CachedImageIcon
        // which will load proper icon version depending on the context - UI theme, DPI.
        // SVG version, when present, has more priority than PNG.
        // See for details: com.intellij.util.ImageLoader.ImageDescList#create
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length - 4) + ".svg"
        } else if (path.endsWith(".svg")) {
            path = path.substring(0, path.length - 4) + ".png"
        } else {
            LOG.debug("unexpected path: $path")
        }
        return urlProvider(path)
    }
}

private class HeadlessIcon(private val image: RenderedImage) : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        //NOP
    }

    override fun getIconWidth(): Int {
        return image.width
    }

    override fun getIconHeight(): Int {
        return image.height
    }
}