package com.itangcent.easyapi.dashboard

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

class ScriptEditorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val scriptCacheService by lazy { ScriptCacheService.getInstance(project) }

    private val jsonFileType by lazy {
        FileTypeManager.getInstance().getFileTypeByExtension("json")
    }

    private val preRequestScriptArea by lazy {
        EditorTextField("", project, jsonFileType).apply {
            setOneLineMode(false)
        }
    }

    private val postResponseScriptArea by lazy {
        EditorTextField("", project, jsonFileType).apply {
            setOneLineMode(false)
        }
    }

    private val titleLabel = JLabel()
    private val infoLabel = JLabel()

    private val tabbedPane = JTabbedPane()

    private var currentScope: ScriptScope? = null
    private var isDirty = false

    private val saveButton = JButton("Save").apply {
        addActionListener { saveCurrentScript() }
        isEnabled = false
    }

    init {
        buildUI()
    }

    private fun buildUI() {
        border = JBUI.Borders.empty(8)

        val headerPanel = JPanel(BorderLayout()).apply {
            add(titleLabel.apply {
                font = font.deriveFont(font.style, 16f)
            }, BorderLayout.WEST)
            add(saveButton, BorderLayout.EAST)
        }

        tabbedPane.addTab("Pre-request Script", JBScrollPane(preRequestScriptArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })
        tabbedPane.addTab("Post-response Script", JBScrollPane(postResponseScriptArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })

        add(headerPanel, BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout()).apply {
            add(tabbedPane, BorderLayout.CENTER)
            add(infoLabel.apply {
                border = JBUI.Borders.emptyTop(4)
            }, BorderLayout.SOUTH)
        }
        add(centerPanel, BorderLayout.CENTER)

        preRequestScriptArea.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                markDirty()
            }
        }, project)
        postResponseScriptArea.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                markDirty()
            }
        }, project)
    }

    fun loadForScope(scope: ScriptScope, endpointCount: Int = 0) {
        saveIfDirty()
        currentScope = scope
        isDirty = false
        saveButton.isEnabled = false

        val cache = scriptCacheService.load(scope)
        preRequestScriptArea.text = cache?.preRequestScript ?: ""
        postResponseScriptArea.text = cache?.postResponseScript ?: ""

        when (scope) {
            is ScriptScope.Module -> {
                titleLabel.text = "\uD83D\uDCC1 ${scope.name}"
                infoLabel.text = if (endpointCount > 0) {
                    "Scripts defined here apply to all $endpointCount endpoints in this module."
                } else {
                    "Scripts defined here apply to all endpoints in this module."
                }
            }
            is ScriptScope.Class -> {
                val shortName = scope.qualifiedName.substringAfterLast('.')
                titleLabel.text = "\uD83D\uDCBB $shortName"
                infoLabel.text = if (endpointCount > 0) {
                    "Scripts defined here apply to all $endpointCount endpoints in this class. They run before/after endpoint-level scripts."
                } else {
                    "Scripts defined here apply to all endpoints in this class."
                }
            }
            is ScriptScope.Endpoint -> {
                titleLabel.text = "\uD83D\uDD27 ${scope.endpointKey.substringAfterLast('#')}"
                infoLabel.text = "This script applies only to this endpoint."
            }
        }

        tabbedPane.selectedIndex = 0
    }

    fun saveIfDirty() {
        if (isDirty && currentScope != null) {
            saveCurrentScript()
        }
    }

    private fun saveCurrentScript() {
        val scope = currentScope ?: return
        val cache = ScriptCache(
            preRequestScript = preRequestScriptArea.text.takeIf { it.isNotBlank() },
            postResponseScript = postResponseScriptArea.text.takeIf { it.isNotBlank() }
        )
        scriptCacheService.save(scope, cache)
        isDirty = false
        saveButton.isEnabled = false
    }

    private fun markDirty() {
        isDirty = true
        saveButton.isEnabled = true
    }
}
