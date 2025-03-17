package com.itangcent.idea.plugin.dialog

import com.intellij.ui.CollectionListModel
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * @author tangcent
 */
object SearchSupport {

    val BASIC_MATCHER: Matcher = { search, item ->
        item.toString().contains(search, true)
    }

    val ENHANCED_MATCHER: Matcher = { search, item ->
        isSubsequence(search, item.toString())
    }

    private fun isSubsequence(s: String, t: String): Boolean {
        var indexS = 0
        var indexT = 0
        while (indexS < s.length && indexT < t.length) {
            if (s[indexS].equals(t[indexT], ignoreCase = true)) {
                indexS++
            }
            indexT++
        }
        return indexS == s.length
    }

    @Suppress("SYNTHETIC_SETTER_PROJECTED_OUT", "UNCHECKED_CAST")
    fun bindSearch(
        searchInputField: JTextField,
        sourceList: () -> List<*>,
        uiList: JList<*>,
        match: (String, Any) -> Boolean = ENHANCED_MATCHER,
        onSearch: (String) -> Unit = {}
    ) {
        // Keep track of the previous text to avoid unnecessary updates
        var previousText: String? = null

        fun updateList() {
            val source = sourceList()
            val query = searchInputField.text

            if (query == previousText) {
                return
            }

            previousText = query

            // Remember the currently selected items
            val selectedItems = uiList.selectedValuesList

            val newModel: CollectionListModel<Any?> = if (query.isNullOrEmpty()) {
                CollectionListModel(source)
            } else {
                val filtered = source.asSequence().filterNotNull().filter { match(query, it) }.toList()
                CollectionListModel(filtered)
            }

            // Cast the JList to raw type to avoid type mismatch
            (uiList as JList<Any?>).model = newModel

            // Restore the selection
            val indicesToSelect = mutableListOf<Int>()
            for (selectedItem in selectedItems) {
                val index = newModel.items.indexOf(selectedItem)
                if (index != -1) {
                    indicesToSelect.add(index)
                }
            }

            val selectedIndices = indicesToSelect.toIntArray()
            uiList.selectedIndices = selectedIndices

            onSearch(query)
        }

        val timer = Timer(600) { updateList() }

        searchInputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                timer.restart()
            }

            override fun removeUpdate(e: DocumentEvent) {
                timer.restart()
            }

            override fun changedUpdate(e: DocumentEvent) {
                timer.restart()
            }
        })
    }
}

typealias Matcher = (String, Any) -> Boolean