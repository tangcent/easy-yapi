package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.ide.util.PropertiesComponent
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.safe
import com.itangcent.idea.utils.initAfterShown
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.WindowConstants

abstract class ContextDialog : JDialog() {
    companion object : Log() {
        private const val LAST_SIZE = "com.itangcent.easyapi.suv.last.size"
    }

    private val lastSizePropertiesName
        get() = "$LAST_SIZE.${this::class.qualifiedName}"

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var logger: Logger

    @Volatile
    protected var disposed = false

    @Volatile
    protected var init = false

    init {
        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        restoreSize()

        this.initAfterShown {
            try {
                init()
            } catch (e: Exception) {
                logger.traceError("failed init ${this::class.qualifiedName}", e)
                onCancel()
            } finally {
                init = true
            }

            //restore size
            restoreSize()
        }
    }

    private fun restoreSize() {
        PropertiesComponent.getInstance().getValue(lastSizePropertiesName)?.let {
            val split = it.split(",")
            if (split.size == 2) {
                val width = split[0].toIntOrNull()
                val height = split[1].toIntOrNull()
                if (width != null && height != null) {
                    this.size = Dimension(width, height)
                }
            }
        }
    }

    abstract fun init()

    fun doAfterInit(action: () -> Unit) {
        if (init) {
            action()
        } else {
            actionContext.runAsync {
                while (!init) {
                    actionContext.checkStatus()
                    Thread.sleep(100)
                }
                action()
            }
        }
    }

    @PostConstruct
    fun postConstruct() {
        actionContext.hold()
        actionContext.on(EventKey.ON_COMPLETED) {
            this.onCancel()
        }
        try {
            onPostConstruct()
        } catch (e: Exception) {
            logger.traceError("failed post construct of ${this::class.qualifiedName}", e)
            onCancel()
        }
    }

    open fun onPostConstruct() {

    }

    @Synchronized
    fun onCancel() {
        if (!disposed) {
            disposed = true
            dispose()
            actionContext.runAsync {
                safe {
                    PropertiesComponent.getInstance().setValue(
                        lastSizePropertiesName,
                        "${this.size.width},${this.size.height}"
                    )
                }
                safe { onDispose() }
                safe {
                    actionContext.unHold()
                    actionContext.stop()
                }
            }
        }
    }

    open fun onDispose() {
    }
}