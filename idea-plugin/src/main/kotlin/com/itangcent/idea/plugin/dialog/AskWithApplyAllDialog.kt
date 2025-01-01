package com.itangcent.idea.plugin.dialog

import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import com.itangcent.common.logger.Log
import com.itangcent.idea.utils.SwingUtils
import java.awt.EventQueue
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

/**
 * Represents the labels for the three buttons in a confirmation dialog
 */
data class ConfirmationDialogLabels(
    val okText: String = "OK",
    val noText: String = "No",
    val cancelText: String = "Cancel"
)

class AskWithApplyAllDialog(owner: Window? = null) : JDialog(owner) {
    private val contentPane = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    private val messageLabel = JLabel().apply {
        border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
    }

    private val applyToAllCheckBox = JCheckBox("Apply to all").apply {
        border = BorderFactory.createEmptyBorder(10, 0, 10, 0)
    }

    private val buttonPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
    }

    private val defaultLabels = ConfirmationDialogLabels()
    private val buttonOK = JButton(defaultLabels.okText)
    private val buttonNO = JButton(defaultLabels.noText)
    private val buttonCancel = JButton(defaultLabels.cancelText)

    private var callBack: ((Int, Boolean) -> Unit)? = null

    init {
        title = "Confirm"
        isModal = true
        initComponents()
        initLayout()
        initListeners()
        SwingUtils.centerWindow(this)
    }

    private fun initComponents() {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        rootPane.defaultButton = buttonOK

        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(buttonOK)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(buttonNO)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(buttonCancel)
    }

    private fun initLayout() {
        setContentPane(contentPane)

        contentPane.add(messageLabel, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0)
        })

        contentPane.add(applyToAllCheckBox, GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0)
        })

        contentPane.add(buttonPanel, GridBagConstraints().apply {
            gridx = 0
            gridy = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.EAST
            insets = JBUI.insets(0)
        })

        pack()
    }

    private fun initListeners() {
        buttonOK.addActionListener { onOK() }
        buttonNO.addActionListener { onNO() }
        buttonCancel.addActionListener { onCancel() }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                onCancel()
            }
        })

        contentPane.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
    }

    fun updateMessage(message: String) {
        EventQueue.invokeLater {
            messageLabel.text = message
            pack()
        }
    }

    /**
     * Updates the text of all three buttons.
     *
     * @param labels The labels for all three buttons
     */
    fun updateButtonLabels(labels: ConfirmationDialogLabels) {
        EventQueue.invokeLater {
            buttonOK.text = labels.okText
            buttonNO.text = labels.noText
            buttonCancel.text = labels.cancelText
            pack()
        }
    }

    fun setCallBack(callBack: (Int, Boolean) -> Unit) {
        this.callBack = callBack
    }

    private fun onOK() {
        dispose()
        callBack?.invoke(Messages.OK, applyToAllCheckBox.isSelected)
    }

    private fun onNO() {
        dispose()
        callBack?.invoke(Messages.NO, applyToAllCheckBox.isSelected)
    }

    private fun onCancel() {
        dispose()
        callBack?.invoke(Messages.CANCEL, applyToAllCheckBox.isSelected)
    }

    companion object : Log()
}