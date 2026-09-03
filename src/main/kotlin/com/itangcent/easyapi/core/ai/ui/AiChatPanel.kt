package com.itangcent.easyapi.core.ai.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.core.ai.AiAssistantService
import com.itangcent.easyapi.core.ai.ConversationSession
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AmbientPerception
import com.itangcent.easyapi.core.ai.agent.Clarification
import com.itangcent.easyapi.core.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.core.ai.agent.EntryPath
import com.itangcent.easyapi.core.ai.agent.QuestionKind
import com.itangcent.easyapi.core.ai.agent.Task
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.ai.agent.TurnOutcome
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.RuleFileTextIo
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.logging.IdeaLog
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
import java.awt.Window
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
import javax.swing.JDialog
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
        SupervisorJob() + com.itangcent.easyapi.core.internal.threading.IdeDispatchers.SwingAny
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

    /**
     * Set to `true` by [dispose]. Checked by [offerContinueOrCancel] /
     * [offerLoopRecovery] before showing a modal dialog, so a panel that is
     * disposed while a `StepLimitHit` / `LoopDetected` outcome is still
     * pending on the EDT does not pop a dangling dialog after teardown.
     */
    @Volatile
    private var disposed: Boolean = false

    /**
     * Test seam: when non-null, [offerContinueOrCancel] delegates to this
     * instead of showing a modal `JOptionPane`. Lets tests verify the
     * `StepLimitHit` outcome path without popping a blocking dialog that
     * would hang the EDT for the rest of the suite. `null` in production.
     */
    internal var offerContinueOrCancelHandler: ((ConversationSession) -> Unit)? = null

    /** Test seam for [offerLoopRecovery] — see [offerContinueOrCancelHandler]. */
    internal var offerLoopRecoveryHandler: ((ConversationSession) -> Unit)? = null

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
     * The reason/tool captured from the most recent [AgentEvent.LoopDetected],
     * used to enrich the recovery dialog text. Overwritten on each loop
     * detection; consumed by [offerLoopRecovery].
     */
    private var lastLoopReason: String? = null
    private var lastLoopTool: String? = null

    /**
     * Tracks the tool name of the most recent [AgentEvent.Perceiving] or
     * [AgentEvent.Acting] when that tool is `create_task_list` or `update_task`.
     *
     * Per design C11's double-emission handling: these two task-list tools emit
     * both the standard `Perceiving`/`Acting` + `Observed` cards (from the
     * agent loop) AND a `TaskListCreated`/`Task*` card (from the tool itself via
     * `ctx.events`). To avoid a noisy double-card transcript, the matching
     * [AgentEvent.Observed] for these two tools is suppressed — the
     * `TaskListCreated`/`Task*` event already drives the UI. The field is cleared
     * on the next non-task-list `Perceiving`/`Acting` and on [AgentEvent.TurnComplete].
     */
    private var pendingTaskListToolCallName: String? = null

    /**
     * Route B ReviewGate arm flag.
     *
     * Set by [runReviewTurn] when Magic is invoked on a **non-empty** rule
     * file. While armed, the next normal [AgentEvent.TurnComplete] renders the
     * *"Review complete. Continue to detection pass?"* Yes/No card and clears
     * the flag (so the gate fires at most once per run).
     *
     * Cleared without firing on abnormal end ([AgentEvent.Failed],
     * [AgentEvent.LoopDetected], `TurnOutcome.StepLimitHit`, cancellation, or
     * thrown turn).
     *
     * Not set on empty-file Magic, Chat, or turn-2+ custom messages.
     */
    private var reviewGatePending: Boolean = false

    /**
     * Optional hook. When set, a staged proposal
     * card shows an **"Apply to editor"** button that calls this with the
     * proposal content — used by [com.itangcent.easyapi.core.settings.ui.RuleFileEditDialog]
     * to write the proposal into the file being edited.
     */
    var onApplyProposal: ((String) -> Unit)? = null

    /**
     * Optional hook. When the AI provider is not
     * configured, the panel shows an "Open AI Settings" button that calls
     * this — wired by the host to open Settings → EasyApi → AI.
     */
    var onConfigureAi: (() -> Unit)? = null

    /**
     * Optional hook invoked when the user clicks **Yes** at the ReviewGate
     * (Route B Stage-2 entry).
     *
     * The host reads the file content **at this time** (reflecting any Stage-1
     * proposal applied in between), builds the detection task list
     * via `MagicTaskListBuilder.buildDetectionPlan`, and calls back into
     * [runTaskList] with the **empty-file** detection instruction body
     * (`MagicInstructionBuilder.detectionInstruction(name, taskList)`). Stage 2 then
     * runs the same detection-pass contract as Route A.
     *
     * When `null`, the gate's Yes button is a no-op (the card still renders
     * so the user sees the question; only the action is suppressed). Wired
     * by [com.itangcent.easyapi.core.settings.ui.RuleFileEditDialog].
     */
    var onReviewGateYes: (() -> Unit)? = null

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

    /**
     * The conversation side of the split: transcript + status + input.
     * Behaviour is unchanged from v1 — this is the same `JPanel(BorderLayout())`
     * that used to be the whole `component`, now wrapped as the left pane of
     * [splitPane] (design R2-C3).
     */
    private val conversationPanel: JPanel = JPanel(BorderLayout()).apply {
        add(transcriptScroll, BorderLayout.CENTER)
        val north = JPanel(BorderLayout()).apply {
            add(notConfiguredBanner, BorderLayout.NORTH)
            add(statusRow(), BorderLayout.CENTER)
        }
        add(north, BorderLayout.NORTH)
        add(inputRow(), BorderLayout.SOUTH)
    }

    /**
     * The right-side Todo List panel (design R2-C3). Invisible until a task
     * list arrives via [AgentEvent.TaskListCreated]; populated by
     * [TodoListPanel.setTaskList] and updated row-by-row on `Task*` events.
     * Cleared on `resetConversation`.
     */
    private val todoListPanel: TodoListPanel = TodoListPanel()

    /**
     * Horizontal split: conversation (left, 5) + Todo List (right, 3).
     *
     * `resizeWeight = 0.625` keeps the 5:3 ratio on window resize. The right
     * pane is hidden (via [TodoListPanel] visibility) until a task list
     * arrives, so the layout is visually indistinguishable from v1 for plain
     * chat.
     *
     * **Initial divider location (R3-C1):** `resizeWeight` alone is not
     * enough on first paint — `JSplitPane` computes the initial divider
     * position from the children's preferred sizes when the divider location
     * is unset, which collapses the Todo List to its minimum width until the
     * first window resize. `addNotify()` sets the divider to `0.625` once,
     * after the host has sized the pane, so the 5:3 ratio holds from the
     * first paint. Subsequent resizes are handled by `resizeWeight`.
     *
     * **Child-visibility relayout:** `JSplitPane` does NOT recompute its
     * divider when a child's visibility is toggled from inside the child's
     * own `revalidate()` — a known Swing pitfall. The pane is initially
     * laid out with the Todo List hidden, so the divider sits where that
     * layout left it; flipping the Todo List to visible without telling the
     * split pane leaves it at zero width (effectively unrendered).
     * [relayoutForTodoList] is therefore called from
     * [TodoListPanel.setTaskList] / [clear] whenever visibility changes — it
     * re-applies the proportional divider and forces the split pane itself
     * to lay out.
     */
    private inner class ChatSplitPane : javax.swing.JSplitPane(
        javax.swing.JSplitPane.HORIZONTAL_SPLIT,
        conversationPanel,
        todoListPanel
    ) {
        /** Guards against re-stomping a divider the user has dragged. */
        private var initialDividerSet = false

        init {
            dividerSize = 6
            resizeWeight = 0.625 // 5 / (5 + 3)
            isOneTouchExpandable = false
            // The right pane starts hidden; TodoListPanel.setTaskList makes it visible.
            todoListPanel.isVisible = false
        }

        override fun addNotify() {
            super.addNotify()
            if (!initialDividerSet) {
                // setDividerLocation(proportional) is only meaningful once the
                // peer has sized the pane. addNotify fires after the host has
                // laid the pane out, so the call lands on a real width.
                setDividerLocation(0.625)
                initialDividerSet = true
            }
        }

        /**
         * Re-apply the 5:3 divider and force this split pane to lay out, so the
         * Todo List actually receives width when [TodoListPanel] becomes visible
         * (and the conversation pane reclaims it when the Todo List is hidden).
         *
         * Called whenever the Todo List's visibility changes. `revalidate()` on
         * the child alone does not move the divider — `JSplitPane`'s layout
         * manager must run on the pane itself.
         */
        fun relayoutForTodoList() {
            if (todoListPanel.isVisible) {
                // Re-assert the proportional divider so the now-visible right
                // pane gets its 3/8 share. Only meaningful once the pane has a
                // real width; otherwise addNotify's initial set still applies.
                if (width > 0) {
                    setDividerLocation(0.625)
                }
            }
            revalidate()
            repaint()
        }
    }

    private val splitPane: ChatSplitPane = ChatSplitPane()

    /** The tool-window host component (the split pane, or just conversation when no plan). */
    val component: JComponent = splitPane

    init {
        refreshConfiguredState()
    }

    /**
     * Reflects whether an AI provider is configured: toggles the banner and
     * enables/disables the input + Send button (issue 1).
     */
    fun refreshConfiguredState() {
        val configured = com.itangcent.easyapi.core.ai.AiRuntimeConfig.load(project) != null
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
        val configured = com.itangcent.easyapi.core.ai.AiRuntimeConfig.load(project) != null
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
     * Programmatic task-list-entry seam (design C10 / task B7, R2-C2).
     *
     * Seeds [taskList] into the session's working memory and emits
     * [AgentEvent.TaskListCreated] *before* the turn starts, so the Todo List
     * renders in the right-side panel before the first LLM round-trip. Then
     * drives the turn with [EntryPath.TASK_LIST_PROGRAMMATIC].
     *
     * Wired to the Magic button in `RuleFileEditDialog.onMagic` (design
     * R2-C2): Magic builds a task list with one task per detection pattern via
     * `MagicTaskListBuilder.buildDetectionPlan()` and passes it here. The agent
     * executes the seeded task list directly — it is instructed not to call
     * `create_task_list` itself.
     *
     * @param taskList The task list to seed into working memory. Task statuses
     *   are ignored — the Todo List renders from the task list as-is, then
     *   `Task*` events update rows as the agent works.
     * @param displayText What the user sees in the transcript.
     * @param instruction The rich instruction sent to the agent (file +
     *   project context).
     */
    fun runTaskList(taskList: TaskList, displayText: String, instruction: String) {
        if (turnJob?.isActive == true) return
        // Use the already-bound session if available (e.g. bound by a prior
        // bindSession call or bindSessionForTest in tests); otherwise bind now.
        val sess = session ?: bindSession() ?: run {
            // AI not configured — show the banner instead of emitting into a void.
            refreshConfiguredState()
            return
        }
        sess.memory.taskList = taskList
        // tryEmit: non-suspending; the session's SharedFlow has a 64-slot
        // buffer so this never drops. Emitting before startTurn guarantees the
        // TaskListCreated card renders before the first LLM round-trip.
        sess.events.tryEmit(AgentEvent.TaskListCreated(taskList))
        startTurn(
            displayText = displayText,
            agentMessage = instruction,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
    }

    /**
     * Route B Stage-1 entry.
     *
     * Starts a single Reactive review turn for a **non-empty** rule file.
     * Does NOT seed a task list, does NOT emit [AgentEvent.TaskListCreated],
     * and does NOT use the `TASK_LIST_PROGRAMMATIC` entry path. Arms the
     * [ReviewGate][reviewGatePending] so the gate fires on the next normal
     * [AgentEvent.TurnComplete].
     *
     * The host ([com.itangcent.easyapi.core.settings.ui.RuleFileEditDialog])
     * builds [instruction] via
     * `MagicInstructionBuilder.reviewInstruction(name, content)` — a body that
     * contains no task-list directive, no `update_task`, and no detection
     * language. Stage 2 (detection pass) is decided by the gate.
     *
     * @param displayText What the user sees in the transcript.
     * @param instruction The review instruction body (Route B Stage-1 body
     *   from [com.itangcent.easyapi.core.ai.agent.MagicInstructionBuilder.reviewInstruction]).
     */
    fun runReviewTurn(displayText: String, instruction: String) {
        if (turnJob?.isActive == true) return
        val sess = session ?: bindSession() ?: run {
            // AI not configured — show the banner instead of emitting into a void.
            refreshConfiguredState()
            return
        }
        // Arm the gate — the next normal TurnComplete renders the Yes/No card.
        // Abnormal end (Failed/LoopDetected/StepLimitHit/cancel) clears it
        // without firing.
        reviewGatePending = true
        startTurn(
            displayText = displayText,
            agentMessage = instruction,
            entryPath = EntryPath.REACTIVE
        )
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
     *
     * @param entryPath The entry path selecting the seed-prompt shape (design
     * C9). Defaults to [EntryPath.REACTIVE] for plain chat; `runTaskList`
     * passes [EntryPath.TASK_LIST_PROGRAMMATIC].
     */
    private fun startTurn(
        displayText: String,
        agentMessage: String,
        entryPath: EntryPath = EntryPath.REACTIVE
    ) {
        if (turnJob?.isActive == true) return
        // Committing to a new turn supersedes any pending proposal — freeze its
        // actions before doing anything else, so a stale card can't be acted on.
        freezeLiveProposalButtons(outdated = true)
        // Use the already-bound session if one is set (runTaskList/runReviewTurn
        // bind it before calling startTurn; tests bind via bindSessionForTest).
        // Only fall back to bindSession() — which resolves via AiAssistantService
        // — when no session is bound yet (plain chat via sendCurrentInput).
        val sess = session ?: bindSession() ?: run {
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
                // Phase 3 — pick the orchestrator agent for Magic detection
                // turns (TASK_LIST_PROGRAMMATIC). The orchestrator has the
                // split tool set (update_task + run_sub_agent +
                // propose_rule_content); the Reactive agent has the full set.
                // Falls back to sess.agent when magicAgent is null (tests
                // that don't exercise the Magic path).
                val agent = if (entryPath == EntryPath.TASK_LIST_PROGRAMMATIC) {
                    sess.magicAgent ?: sess.agent
                } else {
                    sess.agent
                }
                val outcome = agent.runTurn(
                    agentMessage, sess.memory,
                    AmbientPerception.capture(project, editingFilePath),
                    entryPath
                )
                ui {
                    when (outcome) {
                        TurnOutcome.Proposed -> statusLabel.text = "Proposal ready — review below."
                        TurnOutcome.Answered -> statusLabel.text = "Ready."
                        TurnOutcome.StepLimitHit -> {
                            // Abnormal end — clear the ReviewGate arm without
                            // firing. StepLimitHit does not emit
                            // TurnComplete, so the gate's TurnComplete handler
                            // never runs.
                            reviewGatePending = false
                            statusLabel.text = "Request limit reached."
                            offerContinueOrCancel(sess)
                        }
                        TurnOutcome.LoopDetected -> {
                            // Defensive: renderEvent(LoopDetected) already
                            // cleared the arm, but clear again in case the
                            // event collector hasn't drained yet.
                            reviewGatePending = false
                            statusLabel.text = "Agent appears stuck."
                            offerLoopRecovery(sess)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                ui {
                    statusLabel.text = "Cancelled."
                    // Cancellation is an abnormal end — clear the arm without firing.
                    reviewGatePending = false
                }
                throw e
            } catch (e: Throwable) {
                LOG.warn("AI agent turn failed", e)
                ui {
                    statusLabel.text = "Failed: ${e.message}"
                    // Thrown turn is an abnormal end — clear the arm without firing.
                    reviewGatePending = false
                }
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
                // Track task-list-tool calls so the matching Observed can be
                // suppressed (design C11 double-emission handling). The
                // Perceiving card itself is still rendered — only the
                // redundant Observed line is dropped.
                pendingTaskListToolCallName = if (ev.tool == CREATE_TASK_LIST || ev.tool == UPDATE_TASK) ev.tool else null
                appendToolActivityCard("🔍", ev.tool, ev.args, null)
            }
            is AgentEvent.Acting -> {
                statusLabel.text = "Acting ${ev.tool}…"
                pendingTaskListToolCallName = if (ev.tool == CREATE_TASK_LIST || ev.tool == UPDATE_TASK) ev.tool else null
                appendToolActivityCard("⚙", ev.tool, ev.args, null)
            }
            is AgentEvent.Observed -> {
                // Suppress the redundant Observed line for create_task_list /
                // update_task — the TaskListCreated / Task* event already drives
                // the UI (design C11). Clear the tracker either way.
                val suppress = pendingTaskListToolCallName == ev.tool &&
                    (ev.tool == CREATE_TASK_LIST || ev.tool == UPDATE_TASK)
                pendingTaskListToolCallName = null
                if (!suppress) {
                    appendToolObservation(ev.tool, ev.resultSummary)
                }
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
                // Abnormal end — clear the ReviewGate arm without firing.
                reviewGatePending = false
                NotificationUtils.notifyError(
                    project,
                    "EasyApi AI Assistant",
                    ev.reason
                )
            }
            is AgentEvent.LoopDetected -> {
                // Capture the loop reason/tool so the recovery dialog can
                // name the offending tool. The status label is set from the
                // TurnOutcome.LoopDetected branch after the turn ends.
                lastLoopReason = ev.reason
                lastLoopTool = ev.tool
                // Abnormal end — clear the ReviewGate arm without firing.
                reviewGatePending = false
            }
            is AgentEvent.Retrying -> {
                statusLabel.text = "Retrying chat (attempt ${ev.attempt}/${ev.maxRetries})…"
            }
            // Phase B task-list lifecycle (design C11, R2-C3) — Todo List panel.
            is AgentEvent.TaskListCreated -> {
                statusLabel.text = "Task list committed (${ev.taskList.tasks.size} tasks)."
                todoListPanel.setTaskList(ev.taskList)
            }
            is AgentEvent.TaskStarted -> {
                statusLabel.text = "Working task ${ev.taskId}…"
                todoListPanel.updateTask(ev.taskId, TaskStatus.IN_PROGRESS)
            }
            is AgentEvent.TaskCompleted -> {
                statusLabel.text = "Task ${ev.taskId} completed."
                todoListPanel.updateTask(ev.taskId, TaskStatus.COMPLETED)
            }
            is AgentEvent.TaskFailed -> {
                statusLabel.text = "Task ${ev.taskId} failed: ${ev.reason}"
                todoListPanel.updateTask(ev.taskId, TaskStatus.FAILED)
            }
            is AgentEvent.TaskSkipped -> {
                statusLabel.text = "Task ${ev.taskId} skipped."
                todoListPanel.updateTask(ev.taskId, TaskStatus.SKIPPED)
            }
            AgentEvent.TurnComplete -> {
                // Turn finished — status already updated in sendCurrentInput.
                // The Todo List stays visible so the user can review what was
                // done; it is cleared on `resetConversation`. Clear the
                // redundant-tool-card suppression tracker.
                pendingTaskListToolCallName = null
                // Route B ReviewGate: on normal
                // TurnComplete while armed, render the Yes/No card exactly
                // once and clear the arm. Abnormal ends (Failed/LoopDetected/
                // StepLimitHit) clear the arm without firing (handled above
                // and in startTurn's outcome branch).
                if (reviewGatePending) {
                    reviewGatePending = false
                    appendReviewGateCard()
                }
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

    /**
     * The right-side Todo List panel (design R2-C3). Replaces the v1
     * in-transcript `PlanCard`: the task list lives in a persistent side panel
     * so it stops pushing conversation off-screen.
     *
     * One row per [Task], **real [JCheckBox]** + title, updating live as
     * `Task*` events arrive. The checkbox reflects COMPLETED (selected) vs
     * all other states (not selected); the title's foreground colour carries
     * the finer status:
     *
     * - PENDING — checkbox off, normal text
     * - IN_PROGRESS — checkbox off, blue text
     * - COMPLETED — checkbox on, normal text
     * - FAILED — checkbox off, red text
     * - SKIPPED — checkbox off, gray text
     *
     * The checkboxes are display-only (the agent drives status updates); user
     * clicks are absorbed so the check state always matches the agent's
     * [TaskStatus].
     *
     * The panel is invisible until [setTaskList] is called (i.e. a task list
     * is committed via [AgentEvent.TaskListCreated]). [clear] hides it again —
     * used by `resetConversation` (New Conversation button).
     */
    private inner class TodoListPanel : JPanel(BorderLayout()) {
        private val rowsBox: JPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        private val checkboxes: MutableMap<String, JCheckBox> = mutableMapOf()
        private val titles: MutableMap<String, JLabel> = mutableMapOf()
        // Status per task — kept in sync with the checkbox + title colour so
        // test helpers can resolve a glyph even though the UI now uses real
        // JCheckBoxes (which only expose a binary selected state).
        private val statuses: MutableMap<String, TaskStatus> = mutableMapOf()

        init {
            border = EmptyBorder(6, 8, 6, 8)
            add(JLabel("Todo List").apply {
                font = font.deriveFont(Font.BOLD)
                border = EmptyBorder(0, 0, 4, 0)
            }, BorderLayout.NORTH)
            add(JBScrollPane(
                rowsBox,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER)
            isVisible = false
        }

        /** Rebuild the row list from [taskList] and make the panel visible. */
        fun setTaskList(taskList: TaskList) {
            rowsBox.removeAll()
            checkboxes.clear()
            titles.clear()
            statuses.clear()
            if (taskList.tasks.isEmpty()) {
                rowsBox.add(JLabel("No detection tasks").apply {
                    foreground = Color.GRAY
                    border = EmptyBorder(2, 2, 2, 2)
                })
            } else {
                for (task in taskList.tasks) {
                    statuses[task.id] = task.status
                    val cb = JCheckBox().apply {
                        isOpaque = false
                        isFocusPainted = false
                        isFocusable = false
                        isBorderPainted = false
                        isContentAreaFilled = false
                        // Display-only: absorb clicks so the check state always
                        // matches the agent's TaskStatus (issue: user clicks
                        // must not toggle the visual state).
                        isEnabled = false
                        isSelected = task.status == TaskStatus.COMPLETED
                    }
                    val title = JLabel(task.title).apply {
                        foreground = statusForeground(task.status)
                        if (task.detail != null) toolTipText = task.detail
                    }
                    val taskRow = JPanel(BorderLayout(4, 0)).apply {
                        isOpaque = false
                        border = EmptyBorder(1, 0, 1, 0)
                        add(cb, BorderLayout.WEST)
                        add(title, BorderLayout.CENTER)
                    }
                    rowsBox.add(taskRow)
                    checkboxes[task.id] = cb
                    titles[task.id] = title
                }
            }
            isVisible = true
            // JSplitPane does not move its divider when a child flips visible
            // inside its own revalidate(); tell the split pane to re-layout so
            // the Todo List actually receives width.
            splitPane.relayoutForTodoList()
        }

        /** Update the checkbox + title colour for [taskId]. */
        fun updateTask(taskId: String, status: TaskStatus) {
            val cb = checkboxes[taskId] ?: return
            val title = titles[taskId] ?: return
            statuses[taskId] = status
            cb.isSelected = status == TaskStatus.COMPLETED
            title.foreground = statusForeground(status)
            cb.repaint()
            title.repaint()
        }

        /** Empty the row list and hide the panel. */
        fun clear() {
            rowsBox.removeAll()
            checkboxes.clear()
            titles.clear()
            statuses.clear()
            isVisible = false
            // Symmetric to setTaskList: let the split pane reclaim the right
            // pane's width for the conversation side.
            splitPane.relayoutForTodoList()
        }

        /** Whether the checkbox for [taskId] is selected (test-only). */
        internal fun checkBoxSelectedForTest(taskId: String): Boolean? =
            checkboxes[taskId]?.isSelected

        /**
         * Status glyph for [taskId] (test-only). Mirrors the pre-checkbox
         * glyphs so existing tests keep working after the real-JCheckBox
         * refactor: `[ ]` PENDING, `[~]` IN_PROGRESS, `[X]` COMPLETED,
         * `[!]` FAILED, `[-]` SKIPPED. Returns `null` when the task id is
         * unknown or no task list is active.
         */
        internal fun glyphForTest(taskId: String): String? {
            val status = statuses[taskId] ?: return null
            return when (status) {
                TaskStatus.PENDING -> "[ ]"
                TaskStatus.IN_PROGRESS -> "[~]"
                TaskStatus.COMPLETED -> "[X]"
                TaskStatus.FAILED -> "[!]"
                TaskStatus.SKIPPED -> "[-]"
            }
        }

        /** Foreground colour for a [TaskStatus]. */
        private fun statusForeground(status: TaskStatus): Color = when (status) {
            TaskStatus.PENDING -> JBColor.foreground()
            TaskStatus.IN_PROGRESS -> JBColor(Color(0, 0, 180), Color(120, 160, 255))
            TaskStatus.COMPLETED -> JBColor.foreground()
            TaskStatus.FAILED -> JBColor(Color(180, 0, 0), Color(255, 120, 120))
            TaskStatus.SKIPPED -> Color.GRAY
        }
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
     * Render the Route B ReviewGate card.
     *
     * Fired exactly once by the [AgentEvent.TurnComplete] handler while
     * [reviewGatePending] is armed. Presents *"Review complete. Continue to
     * detection pass?"* with **Yes / No** buttons:
     *
     * - **Yes** — invokes [onReviewGateYes], which reads the file content at
     *   this time, builds the detection task list, and calls [runTaskList]
     *   with the empty-file detection instruction. Stage 2
     *   then runs the same detection-pass contract as Route A.
     * - **No** — terminates; no Stage 2. The Stage-1 outcome
     *   (proposal or answer) stands as-is.
     *
     * Either button collapses the card. The arm flag is already cleared by
     * the caller (the TurnComplete handler), so the gate cannot re-fire.
     */
    private fun appendReviewGateCard() {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = EmptyBorder(6, 12, 6, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        row.add(JLabel("✨ Review complete. Continue to detection pass?"))
        val yesBtn = JButton("Yes").apply {
            toolTipText = "Run the detection pass (same as Magic on an empty file)"
            addActionListener {
                row.isVisible = false
                onReviewGateYes?.invoke()
            }
        }
        val noBtn = JButton("No").apply {
            toolTipText = "Keep the review outcome as-is; do not run detections"
            addActionListener {
                // No Stage 2 — terminate.
                row.isVisible = false
            }
        }
        row.add(yesBtn)
        row.add(noBtn)
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
            RuleFileTextIo.writeUtf8WithBom(targetFile.toPath(), content)

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

    /**
     * Discard the current conversation and reset the UI to a fresh "Ready."
     * state. Called by the New Conversation button and by the rule-file editor
     * ([com.itangcent.easyapi.core.settings.ui.RuleFileEditDialog]) so that
     * opening a rule file — or clicking Magic — always starts from an empty
     * transcript (and cancels any still-running turn first).
     */
    internal fun resetConversation() {
        cancelRunningTurn()
        AiAssistantService.getInstance(project).resetConversation()
        session = null
        liveProposalButtonPanel = null
        todoListPanel.clear()
        pendingTaskListToolCallName = null
        reviewGatePending = false
        transcriptBox.removeAll()
        transcriptBox.revalidate()
        transcriptBox.repaint()
        statusLabel.text = "Ready."
    }

    private fun offerContinueOrCancel(sess: ConversationSession) {
        if (disposed) return
        // Test seam: when set, skip the modal dialog (which would block the
        // EDT and hang subsequent tests). Used by AiChatPanelTest to verify
        // the StepLimitHit outcome without popping a real JOptionPane.
        offerContinueOrCancelHandler?.let { it(sess); return }
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

    /**
     * Offers loop-recovery after a [TurnOutcome.LoopDetected] turn, mirroring
     * [offerContinueOrCancel].
     *
     * The dialog names the offending tool (captured from
     * [AgentEvent.LoopDetected]) when available. Continuing injects a
     * user-style anti-repetition hint and re-runs the
     * turn — a fresh `LoopGuard` starts automatically (it is per-turn).
     * Recovery is user-initiated; there is no auto-retry.
     */
    private fun offerLoopRecovery(sess: ConversationSession) {
        if (disposed) return
        offerLoopRecoveryHandler?.let { it(sess); return }
        val tool = lastLoopTool
        val dialogText = if (tool != null) {
            "The agent was repeating the same action ($tool). Retry with guidance, or stop?"
        } else {
            "The agent was repeating the same action. Retry with guidance, or stop?"
        }
        val options = arrayOf("Continue", "Cancel")
        val choice = JOptionPane.showOptionDialog(
            null,
            dialogText,
            "EasyApi AI Assistant",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        if (choice == JOptionPane.YES_OPTION) {
            // User-style anti-repetition hint — same
            // injection channel as the "(continue)" nudge in offerContinueOrCancel.
            val hint = "The previous turn repeated without progress. " +
                "Try a different tool, change the arguments, or communicate your conclusion."
            setRunning(true)
            turnJob = workScope.launch {
                try {
                    sess.agent.runTurn(
                        hint, sess.memory,
                        AmbientPerception.capture(project, editingFilePath)
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    LOG.warn("Loop-recovery turn failed", e)
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
        disposed = true
        cancelRunningTurn()
        eventCollectorJob?.cancel()
        uiScope.cancel()
        workScope.cancel()
        // Dismiss any modal dialog opened by offerContinueOrCancel /
        // offerLoopRecovery (StepLimitHit / LoopDetected outcomes).
        // JOptionPane.showOptionDialog is a blocking native call that survives
        // coroutine-scope cancellation — without this, a disposed panel can
        // leave a dangling modal dialog that blocks the EDT for all subsequent
        // UI operations.
        Window.getWindows()
            .filterIsInstance<JDialog>()
            .filter { it.isShowing }
            .forEach { it.dispose() }
    }

    // --- Test helpers ---

    /** Directly render an [AgentEvent] (test-only — bypasses the flow). */
    internal fun renderEventForTest(ev: AgentEvent) {
        renderEvent(ev)
    }

    /** Number of components in the transcript (test-only). */
    internal fun transcriptComponentCount(): Int = transcriptBox.componentCount

    /**
     * The split pane's divider location in pixels, or `-1` when the divider
     * has not been positioned yet (test-only — used to verify R3-C1's
     * `addNotify` initial-divider-set).
     */
    internal fun splitPaneDividerLocationForTest(): Int = splitPane.dividerLocation

    /** The split pane's total width (test-only). */
    internal fun splitPaneWidthForTest(): Int = splitPane.width

    /**
     * The split pane's divider size in pixels (test-only). `setDividerLocation(double)`
     * factors this out of the available space, so tests that assert on the resulting
     * pixel location must use `(width - dividerSize) * proportion` as the expected value.
     */
    internal fun splitPaneDividerSizeForTest(): Int = splitPane.dividerSize

    /**
     * Current glyph text for [taskId] in the live [TodoListPanel], or `null`
     * when no task list is active / the task id is unknown (test-only).
     */
    internal fun taskListGlyphForTest(taskId: String): String? =
        todoListPanel.glyphForTest(taskId)

    /** Whether the Todo List side panel is currently visible (test-only). */
    internal fun isTodoListPanelVisibleForTest(): Boolean = todoListPanel.isVisible

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

    // --- Route B ReviewGate test helpers ---

    /** Test-only: whether the ReviewGate arm flag is currently set. */
    internal fun isReviewGatePendingForTest(): Boolean = reviewGatePending

    /**
     * Test-only: set the ReviewGate arm flag directly (without driving a full
     * `runReviewTurn`). Used by gate-rendering tests that drive events via
     * [renderEventForTest] rather than starting a real agent turn.
     */
    internal fun armReviewGateForTest() {
        reviewGatePending = true
    }

    /**
     * Test-only: click the "Yes" button on a rendered ReviewGate card.
     * Returns false if no such button is present.
     */
    internal fun clickReviewGateYesForTest(): Boolean {
        val btn = findButtonByText(component, "Yes") ?: return false
        btn.doClick()
        return true
    }

    /**
     * Test-only: click the "No" button on a rendered ReviewGate card.
     * Returns false if no such button is present.
     */
    internal fun clickReviewGateNoForTest(): Boolean {
        val btn = findButtonByText(component, "No") ?: return false
        btn.doClick()
        return true
    }

    /**
     * Test-only: whether a ReviewGate card (containing the "Continue to
     * detection pass?" label) is currently rendered in the transcript.
     */
    internal fun isReviewGateCardRenderedForTest(): Boolean {
        return findLabelContaining(component, "Continue to detection pass?") != null
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

    /** Recursively find a JLabel whose text contains [substring] (test-only). */
    private fun findLabelContaining(c: Component, substring: String): JLabel? {
        if (c is JLabel && c.text?.contains(substring) == true) return c
        if (c is java.awt.Container) {
            for (child in c.components) {
                findLabelContaining(child, substring)?.let { return it }
            }
        }
        return null
    }

    private companion object {
        /** Theme-aware chat bubble backgrounds (light, dark). */
        val USER_BUBBLE = JBColor(Color(0xE8, 0xF0, 0xFE), Color(0x2B, 0x3A, 0x55))
        val ASSISTANT_BUBBLE = JBColor(Color(0xF2, 0xF2, 0xF2), Color(0x3C, 0x3F, 0x41))

        /** Tool names whose `Observed` card is suppressed (design C11). */
        const val CREATE_TASK_LIST = "create_task_list"
        const val UPDATE_TASK = "update_task"
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
