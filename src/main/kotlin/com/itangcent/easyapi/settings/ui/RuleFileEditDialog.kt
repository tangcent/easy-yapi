package com.itangcent.easyapi.settings.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.itangcent.easyapi.ai.ui.AiChatPanel
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Edit dialog for a rule file in the `.easyapi/` project folder
 * or the `~/.easyapi/` global folder.
 *
 * On OK:
 * - If the name changed, renames the file on disk (same directory).
 * - Writes the (possibly edited) content via `Files.writeString`.
 * - Triggers `ConfigReader.getInstance(project).reload()` so new rules take
 * effect immediately.
 *
 * On failure, surfaces `NotificationUtils.notifyError`.
 *
 * @param project Used for `ConfigReader.reload()` + notification project scope.
 * @param filePath Absolute path of the file to edit.
 */
class RuleFileEditDialog(
    private val project: Project,
    private val filePath: String
) : DialogWrapper(project), IdeaLog {

    override val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(RuleFileEditDialog::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val nameField = JBTextField().apply {
        columns = 40
    }
    private val contentArea = JBTextArea().apply {
        rows = 20
        columns = 80
        isEditable = true
    }

    /**
     * Snapshot of the file's content/name as last loaded (or saved), used by
     * [isContentModified] to detect unsaved edits before closing. Null until the
     * initial load completes — treated as "no changes yet" while still loading.
     */
    private var loadedName: String? = null
    private var loadedContent: String? = null

    /**
     * Inline AI assistant. Hidden until the user
     * clicks Chat / Magic. A staged proposal can be applied straight into
     * [contentArea] via [AiChatPanel.onApplyProposal]. When no AI provider is
     * configured, the panel shows an "Open AI Settings" button wired to
     * [openAiSettings].
     *
     * Added directly (NOT wrapped in a JScrollPane): [AiChatPanel.component]
     * already scrolls its transcript internally, so an outer scroll would
     * double-scroll. The wrapper below fixes its preferred height so the
     * BorderLayout.SOUTH region sizes predictably.
     */
    private val aiChatPanel = AiChatPanel(project, editingFilePath = filePath).apply {
        onApplyProposal = { proposed -> contentArea.text = proposed }
        onConfigureAi = { openAiSettings() }
    }
    private val aiPanelHolder = JPanel(BorderLayout()).apply {
        add(aiChatPanel.component, BorderLayout.CENTER)
        isVisible = false
        preferredSize = Dimension(720, 320)
    }
    private val chatButton = JButton("Chat").apply {
        toolTipText = "Ask the AI assistant about this rule file"
        icon = AllIcons.Toolwindows.ToolWindowAskAI
        addActionListener { onChat() }
    }
    private val magicButton = JButton("Magic").apply {
        toolTipText = "Auto-review and improve this rule file with AI"
        icon = AllIcons.Actions.Lightning
        addActionListener { onMagic() }
    }

    init {
        val file = Paths.get(filePath)
        title = "Edit Rule File: ${file.fileName}"
        init()
        loadContentAsync()
    }

    private fun onChat() {
        aiPanelHolder.isVisible = true
        aiChatPanel.refreshConfiguredState()
        aiChatPanel.focusInput()
        revalidateDialog()
    }

    private fun onMagic() {
        aiPanelHolder.isVisible = true
        aiChatPanel.refreshConfiguredState()
        revalidateDialog()
        val name = nameField.text.trim().ifBlank { Paths.get(filePath).fileName.toString() }
        val empty = contentArea.text.isBlank()
        // Short, constant display message; the rich instruction (file + project
        // context) is carried implicitly to the agent (issue 3). When the file
        // is empty, skip "review/improve" and focus purely on detection.
        val displayText = if (empty) {
            "✨ Detect missing custom-pattern rules and draft initial content for \"$name\"."
        } else {
            "✨ Review and improve \"$name\" and detect any missing custom-pattern rules."
        }
        aiChatPanel.runMagic(displayText = displayText, instruction = buildMagicInstruction())
    }

    /** Opens Settings → EasyApi → AI so the user can configure a provider. */
    private fun openAiSettings() {
        EasyApiSettingsConfigurable.selectTab(EasyApiSettingsConfigurable.TAB_AI)
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, EasyApiSettingsConfigurable::class.java)
        aiChatPanel.refreshConfiguredState()
    }

    private fun revalidateDialog() {
        rootPane?.revalidate()
        rootPane?.repaint()
    }

    /**
     * Builds the Magic instruction scoped to the file being edited.
     *
     * - When the file already has content: review/improve it AND detect any
     * custom framework patterns that lack a rule, then propose the full
     * updated file content.
     * - When the file is empty: skip the review step and focus purely on
     * detecting custom patterns and drafting initial rule content for them.
     */
    private fun buildMagicInstruction(): String {
        val name = nameField.text.trim().ifBlank { Paths.get(filePath).fileName.toString() }
        val content = contentArea.text
        return buildString {
            appendLine(
                if (content.isBlank()) {
                    "I'm starting a new rule file '$name' (currently empty). Detect any custom framework patterns in this project that lack a rule, then propose initial rule content for them."
                } else {
                    "Review and improve the rule file '$name' that I'm editing. Fix anything broken or incomplete, detect any custom framework patterns that lack a rule, then propose the full updated file content."
                }
            )
            appendLine()
            appendLine("Standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS, Feign) need no rules. Scan for Custom-Pattern Catalog signals: Filter/HandlerInterceptor/WebFilter requiring a header, ResponseBodyAdvice wrapping responses, HandlerMethodArgumentResolver injecting hidden params, custom meta-/security annotations. Use find_classes_by_annotation + get_psi_class_info to confirm a hit, then apply the catalog recipe from the rule guide. Also scan for Workflow-Pattern Catalog signals: secured endpoints paired with a login/token endpoint (auth token chaining), static API-key/Basic auth, correlation/idempotency header requirements, and HMAC request signing — apply the catalog recipe from the rule guide when found.")
            if (content.isNotBlank()) {
                appendLine()
                appendLine("Current content of '$name':")
                appendLine("```")
                append(content)
                appendLine()
                appendLine("```")
            }
        }
    }

    private fun loadContentAsync() {
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { Files.readString(Paths.get(filePath)) }
                    .onFailure { LOG.warn("Failed to read rule file $filePath", it) }
                    .getOrElse { "" }
            }
            withContext(Dispatchers.Main) {
                val name = Paths.get(filePath).fileName.toString()
                nameField.text = name
                contentArea.text = content
                // Snapshot the loaded state so unsaved-change detection works.
                loadedName = name
                loadedContent = content
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        // Layout (no outer scroll — each region scrolls independently):
        //
        //   [ Name: ... ]              ← fixed (NORTH)
        //   [ Context ]  - scrollable  ← own vertical scroll (CENTER)
        //   [ buttons ]                ← fixed (SOUTH.NORTH)
        //   [ Status / chat input ]    ← hidden until Chat/Magic; the panel
        //                                 scrolls its own transcript internally
        //
        // When the AI panel is hidden, only Name + Context-scroll + buttons
        // are visible, so the content area owns all the vertical space.
        val nameRow = JPanel(BorderLayout()).apply {
            add(JLabel("Name:"), BorderLayout.WEST)
            add(nameField, BorderLayout.CENTER)
        }
        // The rule-file content gets its OWN scroll, independent of the AI
        // transcript below it.
        val contentScroll = JScrollPane(contentArea)

        // SOUTH: AI action bar (Chat / Magic) + the hidden inline AI panel.
        val aiBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(chatButton)
            add(magicButton)
        }
        val south = JPanel(BorderLayout()).apply {
            add(aiBar, BorderLayout.NORTH)
            // The AI panel holder is added directly (it scrolls its own
            // transcript internally). Hidden until the user opens Chat/Magic.
            add(aiPanelHolder, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            add(nameRow, BorderLayout.NORTH)
            add(contentScroll, BorderLayout.CENTER)
            add(south, BorderLayout.SOUTH)
            // DialogWrapper sizes the window from the center panel's preferred
            // size. The content area owns the vertical space; the AI panel
            // (hidden by default) expands it further when opened.
            preferredSize = Dimension(820, 640)
        }
    }

    override fun doOKAction() {
        val originalPath = Paths.get(filePath)
        val parent = originalPath.parent ?: run {
            NotificationUtils.notifyError(project, "EasyApi Rules", "Cannot resolve parent dir of $filePath")
            return
        }
        val newName = nameField.text.trim().ifBlank { originalPath.fileName.toString() }
        val newPath = parent.resolve(newName)
        val content = contentArea.text

        scope.launch {
            withContext(Dispatchers.IO) {
                // Rename if name changed.
                if (newPath != originalPath) {
                    runCatching {
                        Files.move(originalPath, newPath, StandardCopyOption.REPLACE_EXISTING)
                    }.onFailure {
                        LOG.warn("Failed to rename ${originalPath} → ${newPath}", it)
                        withContext(Dispatchers.Main) {
                            NotificationUtils.notifyError(
                                project,
                                "EasyApi Rules",
                                "Failed to rename file: ${it.message}"
                            )
                        }
                        return@withContext
                    }
                }
                // Write content.
                runCatching { Files.writeString(newPath, content) }
                    .onFailure {
                        LOG.warn("Failed to write rule file $newPath", it)
                        withContext(Dispatchers.Main) {
                            NotificationUtils.notifyError(
                                project,
                                "EasyApi Rules",
                                "Failed to write file: ${it.message}"
                            )
                        }
                        return@withContext
                    }
                // Reload config so new rules take effect.
                runCatching { ConfigReader.getInstance(project).reload() }
                    .onFailure { LOG.warn("ConfigReader.reload failed after edit", it) }
            }
            withContext(Dispatchers.Main) {
                // Refresh the snapshot so a subsequent Cancel isn't flagged as unsaved.
                loadedName = newName
                loadedContent = content
                close(OK_EXIT_CODE)
            }
        }
    }

    /**
     * True if the on-screen name or content differs from what was last loaded
     * (or saved). While the initial load is still in flight (`loadedContent ==
     * null`), reports false — there's nothing to discard yet.
     */
    internal fun isContentModified(): Boolean {
        val savedContent = loadedContent ?: return false
        val savedName = loadedName ?: return false
        return nameField.text.trim() != savedName.trim() || contentArea.text != savedContent
    }

    /**
     * Intercepts Cancel / window-close (X). If there are unsaved edits, asks the
     * user whether to save before closing or discard the changes; "Cancel" in
     * that prompt keeps the editor open.
     */
    override fun doCancelAction() {
        if (!isContentModified()) {
            super.doCancelAction()
            return
        }
        val choice = Messages.showYesNoCancelDialog(
            this.contentPane,
            "The rule file has unsaved changes.\nSave before closing?",
            "Unsaved Changes",
            "Save",
            "Discard",
            "Cancel",
            Messages.getQuestionIcon()
        )
        when (choice) {
            Messages.YES -> doOKAction()
            Messages.NO -> super.doCancelAction()
            // CANCEL (or closed): stay in the editor.
        }
    }

    override fun dispose() {
        runCatching { aiChatPanel.dispose() }
        scope.cancel()
        super.dispose()
    }

    // --- Test helpers ---

    /** Sets the content area text directly (test-only). */
    internal fun setContentForTest(content: String) {
        contentArea.text = content
    }

    /** Sets the name field text directly (test-only). */
    internal fun setNameForTest(name: String) {
        nameField.text = name
    }

    /** Returns the snapshot content captured on load (test-only). */
    internal fun snapshotContent(): String? = loadedContent

    /**
     * Simulates the async load having completed — sets the form fields and the
     * snapshot (test-only). Avoids depending on [Dispatchers.Main] scheduling,
     * which plain `runBlocking` does not advance.
     */
    internal fun simulateLoadedForTest(name: String, content: String) {
        nameField.text = name
        contentArea.text = content
        loadedName = name
        loadedContent = content
    }

    /** Disposes the dialog from tests (dispose() is protected on DialogWrapper). */
    internal fun disposeForTest() {
        dispose()
    }
}
