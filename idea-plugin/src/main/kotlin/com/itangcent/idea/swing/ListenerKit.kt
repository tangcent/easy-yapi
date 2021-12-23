@file:Suppress("UNCHECKED_CAST")

package com.itangcent.idea.swing

import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent

fun JTextComponent.onTextChange(action: (String?) -> Unit) {
    val jTextComponent = this
    jTextComponent.document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
            action(jTextComponent.text)
        }

        override fun removeUpdate(e: DocumentEvent) {
            action(jTextComponent.text)
        }

        override fun changedUpdate(e: DocumentEvent) {
            action(jTextComponent.text)
        }
    })
}

fun <T> JComboBox<T>.selected(): T? {
    return this.selectedItem as? T
}

fun <T> JComboBox<T>.onSelect(action: (T?) -> Unit) {
    val jComboBox = this
    jComboBox.model.addListDataListener(object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent?) {
            action(jComboBox.selected())
        }

        override fun intervalRemoved(e: ListDataEvent?) {
            action(jComboBox.selected())
        }

        override fun intervalAdded(e: ListDataEvent?) {
            action(jComboBox.selected())
        }
    })
}

fun JCheckBox.onSelect(action: (Boolean) -> Unit) {
    this.addActionListener {
        action(this.isSelected)
    }
}