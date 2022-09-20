package com.itangcent.idea.plugin.dialog

import com.itangcent.common.logger.Log
import com.itangcent.idea.utils.SwingUtils
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class AskWithApplyAllDialog(owner: Window? = null) : JDialog(owner) {
    private var contentPane: JPanel? = null
    private var buttonOK: JButton? = null
    private var buttonCancel: JButton? = null
    private var buttonNO: JButton? = null
    private var applyToAllCheckBox: JCheckBox? = null
    private var messageLabel: JLabel? = null
    private var callBack: ((Int, Boolean) -> Unit)? = null

    fun updateMessage(
        message: String,
    ) {
        EventQueue.invokeLater {
            messageLabel!!.text = message
        }
    }

    /**
     * @param buttonNames [YES,NO,CANCEL]
     */
    fun updateButtons(buttonNames: Array<String>) {
        EventQueue.invokeLater {
            try {
                buttonOK!!.text = buttonNames[0]
                buttonNO!!.text = buttonNames[1]
                buttonCancel!!.text = buttonNames[2]
            } catch (e: Exception) {
                LOG.error("failed set button name: $buttonNames")
            }
        }
    }

    fun setCallBack(
        callBack: (Int, Boolean) -> Unit,
    ) {
        this.callBack = callBack
    }

    private fun onOK() {
        dispose()
        callBack?.invoke(com.intellij.openapi.ui.Messages.OK, applyToAllCheckBox!!.isSelected)
    }

    private fun onNO() {
        dispose()
        callBack?.invoke(com.intellij.openapi.ui.Messages.NO, applyToAllCheckBox!!.isSelected)
    }

    private fun onCancel() {
        dispose()
        callBack?.invoke(com.intellij.openapi.ui.Messages.CANCEL, applyToAllCheckBox!!.isSelected)
    }

    init {
        setContentPane(contentPane)
        isModal = true
        getRootPane().defaultButton = buttonOK
        SwingUtils.centerWindow(this)

        buttonOK!!.addActionListener { onOK() }
        buttonNO!!.addActionListener { onNO() }
        buttonCancel!!.addActionListener { onCancel() }

        // call onCancel() when cross is clicked
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
    }

    companion object : Log()
}