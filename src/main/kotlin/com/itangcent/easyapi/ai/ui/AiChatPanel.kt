package com.itangcent.easyapi.ai.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.ai.AiAssistantService
import com.itangcent.easyapi.ai.ConversationSession
import com.itangcent.easyapi.ai.agent.AgentEvent
import com.itangcent.easyapi.ai.agent.AmbientPerception
import com.itangcent.easyapi.ai.agent.Clarification
import com.itangcent.easyapi.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.ai.agent.QuestionKind
import com.itangcent.easyapi.ai.agent.TurnOutcome
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.HeadlessException
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * The AI assistant chat panel — a persistent transcript + input area
 *.
 *
 * State lives in [AiAssistantService]; this panel only renders events from
 * the service's `MutableSharedFlow<AgentEvent>` and drives the agent via
 * [ConversationSession.agent]. The panel is disposable: when the tool
 * window content is closed, [dispose] cancels the event collector and any
 * running turn job.
 *
 * v1 layout:
 * ```
 * ┌────────────────────────────────────┐
 * │ Transcript (scrollable vertical Box)│
 * │ - message rows (user / assistant) │
 * │ - tool-activity cards │
 * │ - approval cards (inline buttons) │
 * │ - proposal card (Save…/Copy) │
 * ├────────────────────────────────────┤
 * │ Status label + Stop button │
 * ├────────────────────────────────────┤
 * │ Input area (3-row JBTextArea) │
 * │ Send (⌘/Ctrl+Enter) + New Conv. │
 * └────────────────────────────────────┘
 * ```
 */
class AiChatPanel(
    private val project: Project,
    private val editingFilePath: String? = null
) : Disposable, IdeaLog {

    /**
     * UI scope — collects agent events and marshals UI updates onto the EDT.
     * Uses [IdeDispatchers.SwingAny] (`ModalityState.any()`) so it keeps
     * working while a **modal** dialog (the Rule File Editor) is open.
     */
    private val uiScope = CoroutineScope(
        SupervisorJob() + com.itangcent.easyapi.core.threading.IdeDispatchers.SwingAny
    )

    /**
     * Work scope — runs the agent turn off the EDT. Running the turn on the
     * EDT (the previous behaviour) deadlocked inside the modal dialog: the
     * Swing dispatcher is `nonModal`, so the turn stalled, a write-intent
     * action timed out, and the job was cancelled.
     */
    private val workScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    private var turnJob: Job? = null
    private var eventCollectorJob: Job? = null

    /** The session this panel is bound to (re-resolved on each send). */
    private var session: ConversationSession? = null

    /**
     * The button panel of the most recent proposal card, while its actions
     * (Apply/Save/Copy) are still live. Nulled once the proposal is consumed
     * — either by clicking "Apply to editor" or by starting a new turn, since
     * a consumed/superseded proposal is stale and its actions should no longer
     * be reachable. The card itself stays in the transcript (read-only).
     */
    private var liveProposalButtonPanel: JPanel? = null

    /**
     * True while a clarification card is awaiting the user. While set,
     * the input area stays enabled and a typed reply resolves the pending
     * clarification instead of starting a new turn.
     */
    private var clarificationPending: Boolean = false

    /**
     * Optional hook. When set, a staged proposal
     * card shows an **"Apply to editor"** button that calls this with the
     * proposal content — used by [com.itangcent.easyapi.settings.ui.RuleFileEditDialog]
     * to write the proposal into the file being edited.
     */
    var onApplyProposal: ((String) -> Unit)? = null

    /**
     * Optional hook. When the AI provider is not
     * configured, the panel shows an "Open AI Settings" button that calls
     * this — wired by the host to open Settings → EasyApi → AI.
     */
    var onConfigureAi: (() -> Unit)? = null

    // --- UI components ---

    private val transcriptBox = object : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // KEY: track the viewport width so message bubbles wrap to the
        // available width instead of widening the dialog off-screen (issue 3).
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int): Int = 16
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int): Int = 100

        // Report a tiny preferred width so long message content cannot inflate
        // the enclosing scroll pane's preferred width — which would push the
        // dialog/viewport wider than the screen and send right-aligned buttons
        // (Apply/Save/Copy) off the visible area (issue 3). At layout time
        // getScrollableTracksViewportWidth()=true forces this panel to fill the
        // viewport, so the rendered width is the viewport width, not 1.
        override fun getPreferredSize(): Dimension {
            val d = super.getPreferredSize()
            return Dimension(1, d.height)
        }
    }
    private val transcriptScroll = JBScrollPane(
        transcriptBox,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    private val statusLabel = JLabel("Ready.").apply {
        foreground = Color.GRAY
        border = EmptyBorder(4, 6, 4, 6)
    }
    /**
     * Continuous indeterminate progress indicator.
     * Visible for the entire duration of a running turn — including the silent
     * initial LLM round-trip — so the user always knows the agent is working.
     */
    private val progressBar = javax.swing.JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
        preferredSize = java.awt.Dimension(120, preferredSize.height)
    }
    private val stopButton = JButton("Stop").apply {
        isEnabled = false
        addActionListener { cancelRunningTurn() }
    }

    private val inputArea = JBTextArea(3, 24).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Enter to send · Shift+Enter for a new line"
        // Enter sends; Shift+Enter inserts a newline (chat convention, issue 2).
        val im = getInputMap(JComponent.WHEN_FOCUSED)
        val am = actionMap
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "easyapi-send")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break")
        // Cmd/Ctrl+Enter also sends (kept for habit). Resolved defensively so
        // the panel can be constructed in a headless environment (e.g. unit
        // tests on Linux CI) — there, the shortcut simply is not registered.
        try {
            im.put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                "easyapi-send"
            )
        } catch (_: HeadlessException) {
            // No display available; platform shortcut stays unbound.
        }
        am.put("easyapi-send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = sendCurrentInput()
        })
    }
    private val sendButton = JButton("Send").apply {
        addActionListener { sendCurrentInput() }
    }
    private val newConversationButton = JButton("New Conversation").apply {
        addActionListener { resetConversation() }
    }

    /**
     * "Not configured" banner (issue 1). Shown when no
     * AI provider is configured; offers a button to open the AI settings.
     */
    private val configureButton = JButton("Open AI Settings").apply {
        addActionListener { onConfigureAi?.invoke() }
    }
    private val notConfiguredBanner = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
        add(JLabel("AI provider not configured."))
        add(configureButton)
        isVisible = false
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        add(transcriptScroll, BorderLayout.CENTER)
        val north = JPanel(BorderLayout()).apply {
            add(notConfiguredBanner, BorderLayout.NORTH)
            add(statusRow(), BorderLayout.CENTER)
        }
        add(north, BorderLayout.NORTH)
        add(inputRow(), BorderLayout.SOUTH)
    }

    init {
        refreshConfiguredState()
    }

    /**
     * Reflects whether an AI provider is configured: toggles the banner and
     * enables/disables the input + Send button (issue 1).
     */
    fun refreshConfiguredState() {
        val configured = com.itangcent.easyapi.ai.AiRuntimeConfig.load(project) != null
        notConfiguredBanner.isVisible = !configured
        inputArea.isEnabled = configured
        sendButton.isEnabled = configured && (turnJob?.isActive != true)
        if (!configured) {
            statusLabel.text = "Configure an AI provider to start."
        } else if (statusLabel.text.startsWith("Configure")) {
            statusLabel.text = "Ready."
        }
        notConfiguredBanner.parent?.revalidate()
        notConfiguredBanner.parent?.repaint()
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private fun statusRow(): JPanel = JPanel(BorderLayout()).apply {
        add(statusLabel, BorderLayout.CENTER)
        val east = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(progressBar)
            add(stopButton)
        }
        add(east, BorderLayout.EAST)
    }

    /**
     * Toggles the running state: send/stop buttons and the indeterminate
     * progress indicator. Called at turn start and in the turn's
     * `finally`/cancel paths.
     */
    private fun setRunning(running: Boolean) {
        val configured = com.itangcent.easyapi.ai.AiRuntimeConfig.load(project) != null
        sendButton.isEnabled = !running && configured
        inputArea.isEnabled = !running && configured
        stopButton.isEnabled = running
        progressBar.isVisible = running
    }

    private fun inputRow(): JPanel = JPanel(BorderLayout(4, 4)).apply {
        border = EmptyBorder(4, 6, 4, 6)
        add(inputArea, BorderLayout.CENTER)
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(sendButton)
            add(newConversationButton)
        }
        add(buttons, BorderLayout.EAST)
    }

    // -------------------------------------------------------------------------
    // Driving the agent
    // -------------------------------------------------------------------------

    /**
     * Focuses the input area. Called by the Rules tab's **Chat** button.
     */
    fun focusInput() {
        inputArea.requestFocusInWindow()
    }

    /**
     * Magic run (issue 3): show a short, constant [displayText] in the
     * transcript while sending the richer [instruction] (file + project
     * context) to the agent implicitly.
     */
    fun runMagic(displayText: String, instruction: String) {
        startTurn(displayText = displayText, agentMessage = instruction)
    }

    private fun sendCurrentInput() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        // If a clarification card is pending, a typed reply resolves it
        // rather than starting a new turn (the turn is suspended on the gate).
        val sess = session
        if (clarificationPending && sess != null) {
            appendMessageRow(text, isUser = true)
            inputArea.text = ""
            clarificationPending = false
            inputArea.isEnabled = false
            sendButton.isEnabled = false
            sess.clarifications.completeRaw(text)
            return
        }
        startTurn(displayText = text, agentMessage = text)
    }

    /**
     * Starts an agent turn. [displayText] is what the user sees in the
     * transcript; [agentMessage] is what the agent actually receives (they
     * differ for Magic, which carries hidden file/project context).
     *
     * The turn runs on [workScope] (background) so it never blocks the EDT or
     * stalls inside a modal dialog; UI updates marshal back via [uiScope].
     */
    private fun startTurn(displayText: String, agentMessage: String) {
        if (turnJob?.isActive == true) return
        // Committing to a new turn supersedes any pending proposal — freeze its
        // actions before doing anything else, so a stale card can't be acted on.
        freezeLiveProposalButtons(outdated = true)
        val sess = bindSession() ?: run {
            // AI not configured — show the banner instead of a (suppressed) balloon.
            refreshConfiguredState()
            return
        }
        appendMessageRow(displayText, isUser = true)
        inputArea.text = ""
        setRunning(true)
        statusLabel.text = "Thinking…"

        turnJob = workScope.launch {
            try {
                val outcome = sess.agent.runTurn(
                    agentMessage, sess.memory,
                    AmbientPerception.capture(project, editingFilePath)
                )
                ui {
                    when (outcome) {
                        TurnOutcome.Proposed -> statusLabel.text = "Proposal ready — review below."
                        TurnOutcome.Answered -> statusLabel.text = "Ready."
                        TurnOutcome.StepLimitHit -> {
                            statusLabel.text = "Request limit reached."
                            offerContinueOrCancel(sess)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                ui { statusLabel.text = "Cancelled." }
                throw e
            } catch (e: Throwable) {
                LOG.warn("AI agent turn failed", e)
                ui { statusLabel.text = "Failed: ${e.message}" }
            } finally {
                ui { setRunning(false) }
            }
        }
    }

    /** Marshal a UI update onto the EDT (modal-safe). */
    private fun ui(block: () -> Unit) {
        uiScope.launch { block() }
    }

    /** Resolve (or lazily build) the session and subscribe to its event flow. */
    private fun bindSession(): ConversationSession? {
        val service = AiAssistantService.getInstance(project)
        val sess = service.session() ?: return null
        if (sess !== session) {
            eventCollectorJob?.cancel()
            session = sess
            eventCollectorJob = sess.events.onEach { ev -> renderEvent(ev) }.launchIn(uiScope)
        }
        return sess
    }

    // -------------------------------------------------------------------------
    // Event rendering
    // -------------------------------------------------------------------------

    private fun renderEvent(ev: AgentEvent) {
        when (ev) {
            is AgentEvent.Thinking -> {
                statusLabel.text = "Thinking (step ${ev.step})…"
            }
            is AgentEvent.Perceiving -> {
                statusLabel.text = "Perceiving ${ev.tool}…"
                appendToolActivityCard("🔍", ev.tool, ev.args, null)
            }
            is AgentEvent.Acting -> {
                statusLabel.text = "Acting ${ev.tool}…"
                appendToolActivityCard("⚙", ev.tool, ev.args, null)
            }
            is AgentEvent.Observed -> {
                appendToolObservation(ev.tool, ev.resultSummary)
                statusLabel.text = "Ready."
            }
            is AgentEvent.ApprovalRequested -> {
                appendApprovalCard(ev.tool, ev.args)
            }
            is AgentEvent.ClarificationRequested -> {
                appendClarificationCard(ev.clarification)
            }
            is AgentEvent.FileReadConsentRequested -> {
                appendReadConsentCard(ev.requestedPath)
            }
            is AgentEvent.Message -> {
                appendMessageRow(ev.content, isUser = false)
            }
            is AgentEvent.ProposalReady -> {
                appendProposalCard(ev.proposal.content, ev.proposal.suggestedFileName)
            }
            is AgentEvent.Failed -> {
                statusLabel.text = "Failed: ${ev.reason}"
                NotificationUtils.notifyError(
                    project,
                    "EasyApi AI Assistant",
                    ev.reason
                )
            }
            AgentEvent.TurnComplete -> {
                // Turn finished — status already updated in sendCurrentInput.
            }
        }
        scrollToBottom()
    }

    private fun appendMessageRow(content: String, isUser: Boolean) {
        val bg = if (isUser) USER_BUBBLE else ASSISTANT_BUBBLE
        val header = JLabel(if (isUser) "You" else "Assistant").apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            foreground = UIUtil.getContextHelpForeground()
            border = EmptyBorder(0, 2, 2, 2)
        }
        // Soft-wrap to the bubble's width. A plain JBTextArea with lineWrap=true
        // computes its preferred size from the *unwrapped* content (very wide,
        // 1 line tall), so the row's max-height cap clips the wrapped text.
        // Overriding getPreferredSize() to wrap to the parent's width makes the
        // preferred height reflect the actual number of wrapped lines.
        val area = object : JBTextArea(content) {
            override fun getPreferredSize(): java.awt.Dimension {
                val pw = parent?.width ?: 0
                if (pw <= 0) return super.getPreferredSize()
                super.setSize(pw, Int.MAX_VALUE)
                return java.awt.Dimension(pw, super.getPreferredSize().height)
            }
        }.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = true
            background = bg
            foreground = UIUtil.getLabelForeground()
            border = EmptyBorder(6, 8, 6, 8)
        }
        val bubble = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(header, BorderLayout.NORTH)
            add(area, BorderLayout.CENTER)
        }
        // Full-width row: cap height to preferred so it doesn't stretch
        // vertically, but let it fill the transcript width.
        val row = object : JPanel(BorderLayout()) {
            override fun getMaximumSize(): Dimension =
                Dimension(Int.MAX_VALUE, super.getPreferredSize().height)
        }.apply {
            isOpaque = false
            border = EmptyBorder(3, 6, 3, 6)
            add(bubble, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        transcriptBox.add(row)
        transcriptBox.add(Box.createVerticalStrut(4))
    }

    private fun appendToolActivityCard(icon: String, tool: String, args: String?, observation: String?) {
        val label = JLabel("$icon $tool${if (args.isNullOrBlank()) "" else " — $args"}")
        label.foreground = Color(0x55, 0x55, 0x55)
        val row = JPanel(BorderLayout()).apply {
            border = EmptyBorder(2, 12, 2, 6)
            add(label, BorderLayout.WEST)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        transcriptBox.add(row)
        if (observation != null) {
            val obs = JLabel(" → $observation").apply {
                foreground = Color(0x88, 0x88, 0x88)
                border = EmptyBorder(0, 24, 2, 6)
            }
            transcriptBox.add(obs)
        }
        transcriptBox.add(Box.createVerticalStrut(2))
    }

    private fun appendToolObservation(tool: String, summary: String) {
        // v1: append the observation as a plain line; richer card-linking is polish.
        val obs = JLabel(" → [$tool] $summary").apply {
            foreground = Color(0x88, 0x88, 0x88)
            border = EmptyBorder(0, 24, 2, 6)
        }
        transcriptBox.add(obs)
    }

    private fun appendApprovalCard(tool: String, args: String) {
        val sess = session ?: return
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = EmptyBorder(2, 12, 2, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        row.add(JLabel("⚙ $tool needs approval — $args"))
        val approveBtn = JButton("Approve").apply {
            addActionListener {
                sess.approvals.complete(true)
                row.isVisible = false
            }
        }
        val rejectBtn = JButton("Reject").apply {
            addActionListener {
                sess.approvals.complete(false)
                row.isVisible = false
            }
        }
        row.add(approveBtn)
        row.add(rejectBtn)
        transcriptBox.add(row)
        transcriptBox.add(Box.createVerticalStrut(2))
    }

    /**
     * Render a one-time read-consent card for `read_rule_file`: asks the user
     * to approve reading [requestedPath], which is outside the allowed rule
     * directories. On a decision it completes the session's read-consent gate
     * and collapses the card. The grant is single-use — no persistence.
     */
    private fun appendReadConsentCard(requestedPath: String) {
        val sess = session ?: return
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = EmptyBorder(2, 12, 2, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        row.add(JLabel("📄 read_rule_file wants to read outside the rule folders — $requestedPath"))
        val approveBtn = JButton("Allow once").apply {
            addActionListener {
                sess.readConsents.complete(true)
                row.isVisible = false
            }
        }
        val rejectBtn = JButton("Deny").apply {
            addActionListener {
                sess.readConsents.complete(false)
                row.isVisible = false
            }
        }
        row.add(approveBtn)
        row.add(rejectBtn)
        transcriptBox.add(row)
        transcriptBox.add(Box.createVerticalStrut(2))
    }

    /**
     * Render a structured clarification card: one card grouping all
     * questions (radio group for SINGLE_CHOICE, checkbox list for MULTI_CHOICE,
     * text field for FREE_TEXT, plus an "Other…" field for choice kinds) with a
     * single Submit button. On submit it completes the session's clarification
     * gate and collapses to a compact summary.
     */
    private fun appendClarificationCard(clarification: Clarification) {
        val sess = session ?: return
        clarificationPending = true

        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(6, 12, 6, 6)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = true
            background = ASSISTANT_BUBBLE
        }
        clarification.prompt?.takeIf { it.isNotBlank() }?.let { intro ->
            card.add(JLabel(intro).apply {
                font = font.deriveFont(Font.BOLD)
                alignmentX = Component.LEFT_ALIGNMENT
            })
            card.add(Box.createVerticalStrut(4))
        }

        // Per-question answer extractors, keyed by question id.
        val extractors = mutableListOf<Pair<String, () -> List<String>>>()

        for (q in clarification.questions) {
            val qPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                border = EmptyBorder(4, 0, 4, 0)
            }
            qPanel.add(JLabel(q.text).apply {
                font = font.deriveFont(Font.BOLD, font.size2D - 1f)
                alignmentX = Component.LEFT_ALIGNMENT
            })

            when (q.kind) {
                QuestionKind.SINGLE_CHOICE -> {
                    val group = ButtonGroup()
                    val buttons = q.options.map { opt ->
                        JRadioButton(opt.label).apply {
                            actionCommand = opt.value
                            isSelected = opt.isDefault
                            isOpaque = false
                            alignmentX = Component.LEFT_ALIGNMENT
                            group.add(this)
                            qPanel.add(this)
                        }
                    }
                    if (buttons.isNotEmpty() && group.selection == null) {
                        buttons.first().isSelected = true
                    }
                    val other = JTextField(16)
                    qPanel.add(otherRow(other))
                    extractors += q.id to {
                        val typed = other.text.trim()
                        when {
                            typed.isNotEmpty() -> listOf(typed)
                            else -> group.selection?.actionCommand?.let { listOf(it) } ?: emptyList()
                        }
                    }
                }
                QuestionKind.MULTI_CHOICE -> {
                    val checks = q.options.map { opt ->
                        JCheckBox(opt.label).apply {
                            actionCommand = opt.value
                            isSelected = opt.isDefault
                            isOpaque = false
                            alignmentX = Component.LEFT_ALIGNMENT
                            qPanel.add(this)
                        }
                    }
                    val other = JTextField(16)
                    qPanel.add(otherRow(other))
                    extractors += q.id to {
                        val selected = checks.filter { it.isSelected }.map { it.actionCommand }
                        val typed = other.text.trim()
                        if (typed.isNotEmpty()) selected + typed else selected
                    }
                }
                QuestionKind.FREE_TEXT -> {
                    val field = JTextField(24).apply { alignmentX = Component.LEFT_ALIGNMENT }
                    qPanel.add(field)
                    extractors += q.id to {
                        field.text.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
                    }
                }
            }
            card.add(qPanel)
        }

        val submit = JButton("Submit")
        card.add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(submit)
        })

        submit.addActionListener {
            if (!clarificationPending) return@addActionListener
            val answers = extractors.associate { (id, fn) -> id to fn() }
            clarificationPending = false
            inputArea.isEnabled = false
            sendButton.isEnabled = false
            // Collapse the card to a compact read-only summary.
            val summary = clarification.questions.joinToString(" · ") { q ->
                val a = answers[q.id].orEmpty().filter { it.isNotBlank() }
                "${shortLabel(q.text)}: ${if (a.isEmpty()) "(skipped)" else a.joinToString(", ")}"
            }
            card.removeAll()
            card.add(JLabel("✓ $summary").apply {
                foreground = UIUtil.getContextHelpForeground()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            card.revalidate()
            card.repaint()
            sess.clarifications.complete(ClarificationAnswers(answers))
        }

        val row = object : JPanel(BorderLayout()) {
            override fun getMaximumSize(): Dimension =
                Dimension(Int.MAX_VALUE, super.getPreferredSize().height)
        }.apply {
            isOpaque = false
            border = EmptyBorder(3, 6, 3, 6)
            add(card, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        transcriptBox.add(row)
        transcriptBox.add(Box.createVerticalStrut(4))
        transcriptBox.revalidate()

        // Keep the input usable so the user may type a free-form reply.
        inputArea.isEnabled = true
        sendButton.isEnabled = true
    }

    /** An "Other:" label + free-text field row for choice questions. */
    private fun otherRow(field: JTextField): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel("Other:"))
            add(field)
        }

    /** Trim a question into a short label for the collapsed summary. */
    private fun shortLabel(text: String): String =
        text.trim().removeSuffix("?").let { if (it.length > 24) it.take(24) + "…" else it }

    private fun appendProposalCard(content: String, suggestedFileName: String) {
        // A fresh proposal supersedes any previous one: freeze its actions so the
        // user can't act on a stale card.
        freezeLiveProposalButtons()
        val card = JPanel(BorderLayout(4, 4)).apply {
            border = EmptyBorder(6, 12, 6, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val area = JBTextArea(content).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = EmptyBorder(6, 8, 6, 8)
        }
        val scroll = JScrollPane(area)
        scroll.preferredSize = java.awt.Dimension(600, 240)
        card.add(JLabel("Proposed rule: $suggestedFileName"), BorderLayout.NORTH)
        card.add(scroll, BorderLayout.CENTER)
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        val applyBtn = JButton("Apply to editor")
        val saveBtn = JButton("Save…")
        val copyBtn = JButton("Copy")
        applyBtn.addActionListener {
            onApplyProposal?.invoke(area.text)
            // The proposal is consumed — its actions are now stale.
            freezeLiveProposalButtons()
            NotificationUtils.notifyInfo(project, "EasyApi AI Assistant", "Applied to editor.")
        }
        saveBtn.addActionListener { saveProposal(content, suggestedFileName) }
        copyBtn.addActionListener {
            val text = area.text
            val selection = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            selection.setContents(java.awt.datatransfer.StringSelection(text), null)
            NotificationUtils.notifyInfo(project, "EasyApi AI Assistant", "Copied to clipboard.")
        }
        // "Apply to editor" only makes sense when embedded in an editor dialog.
        if (onApplyProposal != null) {
            buttons.add(applyBtn)
        }
        buttons.add(saveBtn)
        buttons.add(copyBtn)
        card.add(buttons, BorderLayout.SOUTH)
        liveProposalButtonPanel = buttons
        transcriptBox.add(card)
        transcriptBox.add(Box.createVerticalStrut(4))
    }

    /**
     * Replaces the live proposal card's action buttons (Apply/Save/Copy) with a
     * small "(applied)" / "(outdated)" hint and stops tracking it. The proposal
     * content stays visible read-only — only the now-stale actions are removed.
     * Safe to call when no proposal is live (no-op).
     */
    private fun freezeLiveProposalButtons(outdated: Boolean = false) {
        val buttons = liveProposalButtonPanel ?: return
        val card = buttons.parent ?: run {
            liveProposalButtonPanel = null
            return
        }
        buttons.removeAll()
        buttons.add(JLabel(if (outdated) "(outdated)" else "(applied)").apply {
            foreground = UIUtil.getContextHelpForeground()
        })
        card.revalidate()
        card.repaint()
        liveProposalButtonPanel = null
    }

    // -------------------------------------------------------------------------
    // Save flow
    // -------------------------------------------------------------------------

    private fun saveProposal(content: String, suggestedFileName: String) {
        val dialog = SaveProposalDialog(suggestedFileName, project.basePath)
        if (!dialog.showAndGet()) return

        val targetFile = dialog.targetFile()
        try {
            Files.createDirectories(targetFile.parentFile.toPath())
            Files.writeString(targetFile.toPath(), content)

            // Folder is the source of truth — no settings-list registration needed.

            // Reload config so the new rules take effect immediately.
            ApplicationManager.getApplication().invokeLater {
                val cr = ConfigReader.getInstance(project)
                workScope.launch {
                    runCatching { cr.reload() }
.onFailure { LOG.warn("ConfigReader.reload failed after AI save", it) }
                }
                // Open the new file in the editor.
                val virtualFile = LocalFileSystem.getInstance()
.refreshAndFindFileByPath(targetFile.absolutePath)
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                }
                NotificationUtils.notifyInfo(
                    project,
                    "EasyApi AI Assistant",
                    "Saved rule file: ${targetFile.name}"
                )
            }
        } catch (e: Exception) {
            NotificationUtils.notifyError(
                project,
                "EasyApi AI Assistant",
                "Failed to save: ${e.message}",
                e
            )
        }
    }

    // -------------------------------------------------------------------------
    // Stop / Reset
    // -------------------------------------------------------------------------

    private fun cancelRunningTurn() {
        turnJob?.cancel()
        turnJob = null
        statusLabel.text = "Cancelled."
        setRunning(false)
    }

    private fun resetConversation() {
        cancelRunningTurn()
        AiAssistantService.getInstance(project).resetConversation()
        session = null
        liveProposalButtonPanel = null
        transcriptBox.removeAll()
        transcriptBox.revalidate()
        transcriptBox.repaint()
        statusLabel.text = "Ready."
    }

    private fun offerContinueOrCancel(sess: ConversationSession) {
        val options = arrayOf("Continue", "Cancel")
        val choice = JOptionPane.showOptionDialog(
            null,
            "The agent reached its request limit. Continue or stop?",
            "EasyApi AI Assistant",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        if (choice == JOptionPane.YES_OPTION) {
            // Re-run with the existing memory — a fresh step budget.
            setRunning(true)
            turnJob = workScope.launch {
                try {
                    sess.agent.runTurn(
                        "(continue)", sess.memory,
                        AmbientPerception.capture(project, editingFilePath)
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    LOG.warn("Continue turn failed", e)
                } finally {
                    ui { setRunning(false) }
                }
            }
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bar = transcriptScroll.verticalScrollBar
            bar.value = bar.maximum
        }
    }

    override fun dispose() {
        cancelRunningTurn()
        eventCollectorJob?.cancel()
        uiScope.cancel()
        workScope.cancel()
    }

    // --- Test helpers ---

    /** Directly render an [AgentEvent] (test-only — bypasses the flow). */
    internal fun renderEventForTest(ev: AgentEvent) {
        renderEvent(ev)
    }

    /** Number of components in the transcript (test-only). */
    internal fun transcriptComponentCount(): Int = transcriptBox.componentCount

    /** Bind a pre-built session without going through the service (test-only). */
    internal fun bindSessionForTest(sess: ConversationSession) {
        session = sess
        eventCollectorJob?.cancel()
        eventCollectorJob = sess.events.onEach { ev -> renderEvent(ev) }.launchIn(uiScope)
    }

    /**
     * Test-only: find and click the "Apply to editor" button in the transcript.
     * Returns false if no such button exists (e.g. [onApplyProposal] not set).
     */
    internal fun clickApplyToEditorForTest(): Boolean {
        val btn = findButtonByText(component, "Apply to editor") ?: return false
        btn.doClick()
        return true
    }

    /** Test-only: whether a clarification card is awaiting the user. */
    internal fun isClarificationPendingForTest(): Boolean = clarificationPending

    /**
     * Test-only: click the "Submit" button on a pending clarification card.
     * Returns false if no such button is present.
     */
    internal fun clickSubmitClarificationForTest(): Boolean {
        val btn = findButtonByText(component, "Submit") ?: return false
        btn.doClick()
        return true
    }

    /** Test-only: set the input text then trigger a send (typed-reply path). */
    internal fun typeAndSendForTest(text: String) {
        inputArea.text = text
        sendCurrentInput()
    }

    private fun findButtonByText(c: Component, text: String): JButton? {
        if (c is JButton && c.text == text) return c
        if (c is java.awt.Container) {
            for (child in c.components) {
                findButtonByText(child, text)?.let { return it }
            }
        }
        return null
    }

    private companion object {
        /** Theme-aware chat bubble backgrounds (light, dark). */
        val USER_BUBBLE = JBColor(Color(0xE8, 0xF0, 0xFE), Color(0x2B, 0x3A, 0x55))
        val ASSISTANT_BUBBLE = JBColor(Color(0xF2, 0xF2, 0xF2), Color(0x3C, 0x3F, 0x41))
    }
}

/**
 * Modal dialog for the "Save proposal" flow.
 *
 * Asks the user for scope (Global / Project) + filename, defaulting the
 * project directory to [projectBasePath]. Computes the target [File] via
 * [targetFile] when the user confirms.
 */
private class SaveProposalDialog(
    suggestedFileName: String,
    private val projectBasePath: String?
) : javax.swing.JDialog() {

    private val globalRadio = JRadioButton("Global (~/.easyapi/)").apply { isSelected = true }
    private val projectRadio = JRadioButton("Project (<project>/.easyapi/)")
    private val fileNameField = JTextField(suggestedFileName)
    private val projectDirField = JTextField(projectBasePath ?: "").apply { isEnabled = false }

    /** True iff the user dismissed the dialog via OK. */
    private var confirmed = false

    init {
        title = "Save Proposed Rule"
        isModal = true
        globalRadio.addActionListener { projectDirField.isEnabled = false }
        projectRadio.addActionListener { projectDirField.isEnabled = true }

        val content = JPanel(BorderLayout(8, 8)).apply {
            border = EmptyBorder(12, 12, 12, 12)
            val top = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(globalRadio)
                add(projectRadio)
            }
            add(top, BorderLayout.NORTH)
            val form = JPanel(java.awt.GridLayout(2, 2, 4, 4)).apply {
                add(JLabel("File name:"))
                add(fileNameField)
                add(JLabel("Project dir:"))
                add(projectDirField)
            }
            add(form, BorderLayout.CENTER)
        }
        val ok = JButton("OK").apply {
            addActionListener {
                confirmed = true
                isVisible = false
            }
        }
        val cancel = JButton("Cancel").apply {
            addActionListener {
                confirmed = false
                isVisible = false
            }
        }
        contentPane = content
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(ok)
            add(cancel)
        }
        content.add(buttons, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(null)
    }

    /** Show modally and return true if the user confirmed. */
    fun showAndGet(): Boolean {
        isVisible = true
        return confirmed
    }

    fun targetFile(): File {
        val fileName = fileNameField.text.trim().ifBlank { "custom.rules" }
        return if (globalRadio.isSelected) {
            File(File(System.getProperty("user.home"), ".easyapi"), fileName)
        } else {
            val dir = projectDirField.text.trim().ifBlank { projectBasePath ?: "." }
            File(File(dir, ".easyapi"), fileName)
        }
    }
}
