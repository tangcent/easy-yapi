package com.itangcent.idea.plugin.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.util.ui.components.BorderLayoutPanel
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.utils.SwingUtils
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.*


private class SuvApiExportPanel : BorderLayoutPanel() {

    val searchInputField = JTextField().apply {
        minimumSize = Dimension(100, 30)
    }
    val selectAllCheckBox = JBCheckBox()
    val channelComboBox = ComboBox<Any?>().apply {
        isSwingPopup = false
    }
    val buttonOK = JButton("✔").apply {
        preferredSize = Dimension(40, 30)
        minimumSize = Dimension(40, 30)
    }
    val buttonCancel = JButton("X").apply {
        preferredSize = Dimension(40, 30)
        minimumSize = Dimension(40, 30)
    }

    val apiList = JBList<Any?>()

    init {
        // Top Panel
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(searchInputField)  // Add search input field at the top and left
            add(selectAllCheckBox)
            add(channelComboBox)
            add(Box.createHorizontalGlue())  // To push the following components to the right
            add(buttonOK)
            add(buttonCancel)
        }

        // Center Panel
        val centerPanel = JScrollPane(apiList)

        // Setup BorderLayoutPanel
        addToTop(topPanel)
        addToCenter(centerPanel)
    }
}


class SuvApiExportDialog : ContextDialog() {

    companion object {
        private const val LAST_USED_CHANNEL = "com.itangcent.easyapi.suv.last.used.channel"
    }

    private var trigger = TriggerSupport()

    private val suvApiExportPanel = SuvApiExportPanel()
    private val buttonOK get() = suvApiExportPanel.buttonOK
    private val buttonCancel get() = suvApiExportPanel.buttonCancel
    private val channelComboBox get() = suvApiExportPanel.channelComboBox
    private val apiList get() = suvApiExportPanel.apiList
    private val selectAllCheckBox get() = suvApiExportPanel.selectAllCheckBox
    private val searchInputField get() = suvApiExportPanel.searchInputField

    private var docList: List<*>? = null

    private var apisHandle: ((Any?, List<*>) -> Unit)? = null

    private var onChannelChanged: ((Any?) -> Unit)? = null

    init {
        minimumSize = Dimension(400, 400)
        maximumSize = Dimension(800, 800)

        contentPane = suvApiExportPanel
        getRootPane().defaultButton = buttonOK

        buttonOK.addActionListener { onOK() }

        buttonCancel.addActionListener { onCancel() }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        SwingUtils.immersed(this.channelComboBox)

        EasyIcons.Close.iconOnly(this.buttonCancel)
        EasyIcons.OK.iconOnly(this.buttonOK)

        // call onCancel() on ESCAPE
        suvApiExportPanel.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        // call onCallClick() on ENTER
        suvApiExportPanel.registerKeyboardAction(
            { onOK() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        selectAllCheckBox.addChangeListener {
            onSelectedAll()
        }

        channelComboBox.addActionListener {
            onChannelChanged?.invoke(channelComboBox.selectedItem)
        }

        SearchSupport.bindSearch(
            searchInputField = searchInputField,
            sourceList = { docList!! },
            uiList = apiList
        )

        apiList.addListSelectionListener {
            this.trigger.withTrigger("onSelect") {
                selectAllCheckBox.isSelected = apiList.model.size == apiList.selectionModel.selectedItemsCount
            }
        }

        SwingUtils.centerWindow(this)
    }

    private fun onSelectedAll() = this.trigger.withTrigger("onSelectAll") {
        if (selectAllCheckBox.isSelected) {
            apiList.selectionModel!!.addSelectionInterval(0, docList!!.size)
        } else {
            apiList.selectionModel!!.clearSelection()
        }
    }

    fun updateRequestList(requestList: List<*>) {
        this.docList = requestList
        this.apiList.model = DefaultComboBoxModel(requestList.toTypedArray())
    }

    fun selectAll() {
        this.selectAllCheckBox.isSelected = true
    }

    fun selectMethod(api: Any?) {
        this.selectAllCheckBox.isSelected = false
        this.docList?.indexOf(api)?.let {
            apiList.selectedIndex = it
        }
    }

    fun setChannels(channels: List<*>) {
        this.channelComboBox.model = DefaultComboBoxModel(channels.toTypedArray())


        val lastUsedChannel = PropertiesComponent.getInstance().getValue(LAST_USED_CHANNEL)
        if (lastUsedChannel.notNullOrEmpty()) {
            channels.firstOrNull { it.toString() == lastUsedChannel }
                ?.let { this.channelComboBox.model.selectedItem = it }
        }

        onChannelChanged?.let { it(this.channelComboBox.selectedItem) }
    }

    fun setApisHandle(apisHandle: (Any?, List<*>) -> Unit) {
        this.apisHandle = apisHandle
    }

    fun setOnChannelChanged(onChannelChanged: ((Any?) -> Unit)) {
        this.onChannelChanged = onChannelChanged
    }

    override fun init() {
        actionContext.runAsync {
            for (i in 0..10) {
                Thread.sleep(500)
                if (disposed) {
                    return@runAsync
                }

                if (actionContext.callInSwingUI {
                        if (!disposed && !this.isFocused) {
                            this.apiList.requestFocus()
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
        val selectedChannel = this.channelComboBox.selectedItem
        val selectedApis = GsonUtils.copy(this.apiList.selectedValuesList!!) as List<*>
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
}
