package com.itangcent.easyapi.core.settings.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.itangcent.easyapi.core.ai.ui.AiChatPanel
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.RuleFileTextIo
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.logging.IdeaLog
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
 * - Writes the (possibly edited) content as UTF-8 with a BOM
 *   (see [RuleFileTextIo]).
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
        // Route B Stage-2 entry: when
        // the user clicks Yes at the ReviewGate, read the file content **at
        // this time** (reflecting any Stage-1 proposal applied in between),
        // build the detection task list, and re-enter the detection-pass
        // contract via runTaskList with the empty-file detection instruction.
        onReviewGateYes = { onReviewGateYes() }
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
        val content = contentArea.text
        val empty = content.isBlank()
        // Route split: empty file → straight to detections
        // (Route A); non-empty file → review → gate → optional detections
        // (Route B). The two routes use distinct instruction bodies built by
        // MagicInstructionBuilder so each is independently testable.
        if (empty) {
            // Route A — straight to detections (today's flow).
            val displayText = "✨ Detect missing custom-pattern rules and draft initial content for \"$name\"."
            runDetectionPass(displayText, name)
        } else {
            // Route B Stage 1 — review only; the gate decides Stage 2.
            // No ambient capture, no plan build here — Stage 2 builds the plan
            // lazily on gate Yes.
            val displayText = "✨ Review and improve \"$name\"."
            val instruction = com.itangcent.easyapi.core.ai.agent.MagicInstructionBuilder
                .reviewInstruction(name, content)
            aiChatPanel.runReviewTurn(displayText, instruction)
        }
    }

    /**
     * Route B Stage-2 entry — invoked by [AiChatPanel]'s `onReviewGateYes`
     * hook when the user clicks **Yes** at the ReviewGate.
     *
     * Reads the file content **at Yes time** (from [contentArea.text],
     * reflecting any Stage-1 proposal the user applied in between),
     * builds the detection task list with the now-enabled features, and
     * re-enters the detection-pass contract via [AiChatPanel.runTaskList]
     * with the **empty-file** detection instruction body (no "review"
     * directive).
     */
    private fun onReviewGateYes() {
        val name = nameField.text.trim().ifBlank { Paths.get(filePath).fileName.toString() }
        // Read at Yes time — the user may have applied a Stage-1 proposal.
        // (The content itself is NOT embedded in the Stage-2 instruction body;
        // detectionInstruction(name, taskList) carries no content. The read is
        // done here so Stage 2 always sees the latest state, and so a future
        // revision can pass it to the task-list builder if needed.)
        @Suppress("UNUSED_VARIABLE")
        val contentAtYes = contentArea.text
        val displayText = "✨ Detect missing custom-pattern rules and draft initial content for \"$name\"."
        runDetectionPass(displayText, name)
    }

    /**
     * Shared detection-pass entry (Route A and Route B Stage 2).
     *
     * Ambient capture does PSI work (framework detection), so it runs
     * off-EDT; the task list is then built and the orchestrator instruction
     * composed from it, then both are seeded back on the EDT (`runTaskList`
     * touches the session + UI and must run on the dispatch thread). The
     * agent executes the seeded task list directly — it does NOT call
     * `create_task_list`.
     *
     * The instruction is built **after** the task list so it can render the
     * seeded task ids into the orchestrator's user message (the only channel
     * by which the LLM learns the exact ids to pass to `run_sub_agent` /
     * `update_task`). Building it earlier — before the task list exists —
     * was the root cause of the "unknown task id" loop.
     */
    private fun runDetectionPass(displayText: String, name: String) {
        scope.launch {
            val amb = com.itangcent.easyapi.core.ai.agent.AmbientPerception.capture(
                project, editingFilePath = filePath
            )
            val taskList = com.itangcent.easyapi.core.ai.agent.MagicTaskListBuilder.buildDetectionPlan(
                activeChannels = amb.enabledChannels.toSet(),
                activeFormats = amb.enabledFormats.toSet(),
                activeFrameworks = amb.frameworkHints.toSet()
            )
            // Compose the instruction from the freshly-built task list so the
            // orchestrator LLM sees the exact ids it must use.
            val instruction = com.itangcent.easyapi.core.ai.agent.MagicInstructionBuilder
                .detectionInstruction(name, taskList)
            withContext(Dispatchers.Main) {
                aiChatPanel.runTaskList(
                    taskList = taskList,
                    displayText = displayText,
                    instruction = instruction
                )
            }
        }
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

    private fun loadContentAsync() {
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { RuleFileTextIo.readUtf8StrippingBom(Paths.get(filePath)) }
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
                runCatching { RuleFileTextIo.writeUtf8WithBom(newPath, content) }
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
