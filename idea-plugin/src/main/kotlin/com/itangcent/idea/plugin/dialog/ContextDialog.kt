package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.logger.traceError
import com.itangcent.idea.utils.initAfterShown
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.WindowConstants

abstract class ContextDialog : JDialog() {

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

        this.initAfterShown {
            try {
                init()
            } catch (e: Exception) {
                logger.traceError("failed init ${this::class.qualifiedName}", e)
                onCancel()
            } finally {
                init = true
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
            KitUtils.safe { onDispose() }
        }
    }

    open fun onDispose() {
        actionContext.unHold()
        actionContext.stop()
        actionContext.suicide()
    }
}