package com.itangcent.idea.plugin.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.psi.PsiMethod
import com.intellij.ui.components.JBCheckBox
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.utils.SwingUtils
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.concurrent.TimeUnit
import javax.swing.*

class SuvApiExportDialog : ContextDialog() {
    private var contentPane: JPanel? = null
    private var buttonOK: JButton? = null
    private var buttonCancel: JButton? = null
    private var channelComboBox: JComboBox<*>? = null

    private var apiList: JList<*>? = null
    private var docList: List<*>? = null

    private var selectAllCheckBox: JBCheckBox? = null

    private var apisHandle: ((Any?, List<*>) -> Unit)? = null

    private var onChannelChanged: ((Any?) -> Unit)? = null

    init {
        this.isUndecorated = false
        this.isResizable = false
        setContentPane(contentPane)
        isModal = false
        getRootPane().defaultButton = buttonOK
        SwingUtils.centerWindow(this)

        buttonOK!!.addActionListener { onOK() }

        buttonCancel!!.addActionListener { onCancel() }

        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        SwingUtils.immersed(this.channelComboBox!!)

        EasyIcons.Close.iconOnly(this.buttonCancel)
        EasyIcons.OK.iconOnly(this.buttonOK)

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        // call onCallClick() on ENTER
        contentPane!!.registerKeyboardAction(
            { onOK() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        selectAllCheckBox!!.addChangeListener {
            onSelectedAll()
        }

        channelComboBox!!.addActionListener {
            onChannelChanged?.invoke(channelComboBox?.selectedItem)
        }
    }

    private fun onSelectedAll() {
        if (selectAllCheckBox!!.isSelected) {
            apiList!!.selectionModel!!.addSelectionInterval(0, docList!!.size)
        } else {
            apiList!!.selectionModel!!.clearSelection()
        }
    }

    fun updateRequestList(requestList: List<*>) {
        this.docList = requestList
        this.apiList!!.model = DefaultComboBoxModel(requestList.toTypedArray())
    }

    fun selectAll(){
        this.selectAllCheckBox!!.isSelected = true
        onSelectedAll()
    }

    fun selectMethod(api: Any?){
        this.selectAllCheckBox!!.isSelected = false
        this.docList?.indexOf(api)?.let {
            apiList!!.selectedIndex = it
        }
    }

    fun setChannels(channels: List<*>) {
        this.channelComboBox!!.model = DefaultComboBoxModel(channels.toTypedArray())


        val lastUsedChannel = PropertiesComponent.getInstance().getValue(LAST_USED_CHANNEL)
        if (lastUsedChannel.notNullOrEmpty()) {
            channels.firstOrNull { it.toString() == lastUsedChannel }
                ?.let { this.channelComboBox!!.model.selectedItem = it }
        }

        onChannelChanged?.let { it(this.channelComboBox?.selectedItem) }
    }

    fun setApisHandle(apisHandle: (Any?, List<*>) -> Unit) {
        this.apisHandle = apisHandle
    }

    fun setOnChannelChanged(onChannelChanged: ((Any?) -> Unit)) {
        this.onChannelChanged = onChannelChanged
    }

    override fun init() {
        actionContext.keepAlive(TimeUnit.MINUTES.toMillis(3))

        actionContext.runAsync {
            for (i in 0..10) {
                Thread.sleep(500)
                if (disposed) {
                    return@runAsync
                }

                if (actionContext.callInSwingUI {
                        if (!disposed && !this.isFocused) {
                            this.apiList!!.requestFocus()
                            return@callInSwingUI false
                        } else {
                            return@callInSwingUI true
                        }
                    } == true) {
                    break
                }
            }

            Thread.sleep(200)

            if (disposed) {
                return@runAsync
            }

            actionContext.runInSwingUI {
                if (!disposed && this.isFocused) {
                    this.addWindowFocusListener(object : WindowFocusListener {
                        override fun windowLostFocus(e: WindowEvent?) {
                            onCancel()
                        }

                        override fun windowGainedFocus(e: WindowEvent?) {
                        }
                    })
                }
            }
        }
    }

    private fun onOK() {
        val selectedChannel = this.channelComboBox!!.selectedItem
        val selectedApis = GsonUtils.copy(this.apiList!!.selectedValuesList!!) as List<*>
        actionContext.runAsync {
            try {
                this.apisHandle!!(selectedChannel, selectedApis)
            } catch (e: Throwable) {
                logger.traceError("apis export failed", e)
            } finally {
                actionContext.unHold()
            }
        }
        PropertiesComponent.getInstance().setValue(LAST_USED_CHANNEL, selectedChannel?.toString())
        cancelSilent()
    }

    @Synchronized
    fun cancelSilent() {
        if (!disposed) {
            disposed = true
            dispose()
        }
    }

    companion object {
        private const val LAST_USED_CHANNEL = "com.itangcent.last.used.channel"
    }
}
