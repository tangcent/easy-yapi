package com.itangcent.idea.plugin.dialog

import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.components.JBScrollPane
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.utils.SwingUtils
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import kotlin.math.max

class ChooseWithTipDialog<T>(owner: Window? = null) : JDialog(owner) {

    private val contentPane = JPanel(BorderLayout())
    private val messageLabel = JLabel("Label")
    private val itemComboBox = JComboBox<String>()
    private val itemTip = JTextArea()
    private val buttonOK = JButton("OK")
    private val buttonCancel = JButton("Cancel")

    private var items: List<T> = emptyList()
    private var tipAs: ((T) -> String) = { it.toString() }
    private var callBack: ((T?) -> Unit) = {}

    fun updateItems(
        message: String?,
        items: List<T>,
        showAs: ((T) -> String?)?,
        tipAs: ((T) -> String)?,
        callBack: ((T?) -> Unit),
    ) {
        this.items = items
        if (tipAs != null) {
            this.tipAs = tipAs
        }
        this.callBack = callBack
        val showValues = items.map(showAs ?: { it.toString() })
        EventQueue.invokeLater {
            if (message.isNullOrBlank()) {
                messageLabel.isVisible = false
            } else {
                messageLabel.text = message
            }
            if (showValues.notNullOrEmpty()) {
                itemComboBox.model =
                    MutableCollectionComboBoxModel(showValues)
                itemComboBox.selectedIndex = 0
            }
        }

        val widthForShowItems = showValues.asSequence()
            .filterNotNull()
            .map { it.length }
            .maxOrNull()?.let { it * 10 } ?: 0
        val tips = items.map(this.tipAs).map { it.split("\n") }
        val heightForTips = tips.asSequence()
            .map { it.size }
            .maxOrNull()?.let { it * 20 } ?: 0
        val widthForTips = tips.asSequence()
            .flatMap { it.asSequence() }
            .map { it.length }
            .maxOrNull()?.let { it * 10 } ?: 0
        EventQueue.invokeLater {
            this.size = Dimension(
                max(widthForShowItems, widthForTips)
                    .coerceIn(200, 500),
                heightForTips
                    .coerceIn(200, 500)
            )
        }
    }

    private fun onOK() {
        val selectedItem = itemComboBox.selectedIndex.takeIf { it != -1 }
            ?.let { items[it] }
        dispose()
        callBack(selectedItem)
    }

    private fun onCancel() {
        dispose()
        callBack(null)
    }

    private fun close() {
        dispose()
    }

    init {
        // Top Section
        contentPane.add(messageLabel, BorderLayout.NORTH)

        // Middle Section
        val middlePanel = JPanel(BorderLayout())
        middlePanel.add(itemComboBox, BorderLayout.NORTH)
        val scrollPane = JBScrollPane(itemTip)
        middlePanel.add(scrollPane, BorderLayout.CENTER)
        contentPane.add(middlePanel, BorderLayout.CENTER)

        // Bottom Section
        val bottomPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        bottomPanel.add(buttonOK)
        bottomPanel.add(buttonCancel)
        contentPane.add(bottomPanel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)  // Center the dialog on the screen

        // Action Listeners
        buttonOK.addActionListener { onOK() }
        buttonCancel.addActionListener { onCancel() }

        setContentPane(contentPane)
        isModal = true
        getRootPane().defaultButton = buttonOK
        SwingUtils.centerWindow(this)

        buttonOK.addActionListener { onOK() }
        buttonCancel.addActionListener { onCancel() }

        // call onCancel() when cross is clicked
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        itemComboBox.addActionListener {
            onItemSelected()
        }
    }

    private fun onItemSelected() {
        val tip = itemComboBox.selectedIndex.takeIf { it != -1 }
            ?.let { items[it] }
            ?.let { tipAs(it) }
        if (tip.isNullOrBlank()) {
            itemTip.isVisible = false
            itemTip.text = ""
            return
        }
        itemTip.isVisible = true
        itemTip.text = tip
    }
}