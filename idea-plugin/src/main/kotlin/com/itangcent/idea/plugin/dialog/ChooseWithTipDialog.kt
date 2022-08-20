package com.itangcent.idea.plugin.dialog

import com.intellij.ui.MutableCollectionComboBoxModel
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.utils.SwingUtils
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class ChooseWithTipDialog<T>(owner: Window? = null) : JDialog(owner) {
    private var contentPane: JPanel? = null
    private var messageLabel: JLabel? = null
    private var buttonOK: JButton? = null
    private var buttonCancel: JButton? = null
    private var itemComboBox: JComboBox<String?>? = null
    private var itemTip: JTextArea? = null
    private var items: List<T>? = null
    private var tipAs: ((T) -> String?)? = null
    private var callBack: ((T?) -> Unit)? = null

    fun updateItems(
        message: String?,
        items: List<T>?,
        showAs: ((T) -> String?)?,
        tipAs: ((T) -> String?)?,
        callBack: ((T?) -> Unit),
    ) {
        this.items = items
        this.tipAs = tipAs
        this.callBack = callBack
        val showValues = items?.map(showAs ?: { it.toString() }) ?: emptyList()
        EventQueue.invokeLater {
            if (message.isNullOrBlank()) {
                messageLabel!!.isVisible = false
            } else {
                messageLabel!!.text = message
            }
            if (showValues.notNullOrEmpty()) {
                itemComboBox!!.model =
                    MutableCollectionComboBoxModel(showValues)
                itemComboBox!!.selectedIndex = 0
            }
        }
    }

    private fun onOK() {
        val selectedItem = itemComboBox!!.selectedIndex.takeIf { it != -1 }
            ?.let { items!![it] }
        dispose()
        callBack!!(selectedItem)
    }

    private fun onCancel() {
        dispose()
        callBack!!(null)
    }

    private fun close() {
        dispose()
    }

    init {
        setContentPane(contentPane)
        isModal = true
        getRootPane().defaultButton = buttonOK
        SwingUtils.centerWindow(this)

        buttonOK!!.addActionListener { onOK() }
        buttonCancel!!.addActionListener { onCancel() }

        // call onCancel() when cross is clicked
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction({ onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

        itemComboBox?.addActionListener {
            onItemSelected()
        }
    }

    private fun onItemSelected() {
        val tip = itemComboBox!!.selectedIndex.takeIf { it != -1 }
            .takeIf { tipAs != null }
            ?.let { items!![it] }?.let { tipAs!!(it) }
        if (tip.isNullOrBlank()) {
            itemTip!!.isVisible = false
            return
        }
        itemTip!!.isVisible = true
        itemTip!!.text = tip
    }
}