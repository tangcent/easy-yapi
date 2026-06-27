package com.itangcent.easyapi.ai

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.ai.agent.Clarification
import com.itangcent.easyapi.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.ai.agent.ClarificationGate
import com.itangcent.easyapi.ai.agent.RuleAuthoringAgent
import com.itangcent.easyapi.ai.tools.ToolContext
import com.itangcent.easyapi.ai.tools.ToolRegistry
import com.itangcent.easyapi.ai.tools.standardRuleTools
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Project-level service that owns the AI assistant's conversation state
 *.
 *
 * - **Survives UI hide/show** — the [ConversationSession] lives
 * here, not in the chat panel. The panel subscribes to
 * [events] and drives the agent via [runTurn].
 * - **Lazy agent** — the [RuleAuthoringAgent] is created only on first
 * use, and only when [AiSettings.load] returns non-null.
 * - **Single active turn** — only one [runTurn] may be active at a time;
 * the panel holds the [Job] and cancels it via the Stop button.
 *
 * @see AiChatPanel
 */
class AiAssistantService(private val project: Project) : Disposable, IdeaLog {

    /**
     * The conversation session — agent + memory + events.
     *
     * `null` until first use (or after [resetConversation]). Re-created
     * lazily by [session].
     */
    @Volatile
    private var _session: ConversationSession? = null

    /** Cache of the latest settings used to build the agent. */
    @Volatile
    private var _builtSettingsHash: Int? = null

    /**
     * Factory seam for the AI service. Production uses [AIServiceFactory.create];
     * tests override this to inject a [FakeAIService].
     */
    internal var aiServiceFactory: (AiSettings) -> AIService =
        { settings -> AIServiceFactory.create(settings) }

    /**
     * Returns the active session, lazily creating it from the current
     * [AiSettings].
     *
     * If settings have changed since the last build (different hash), the
     * session is rebuilt — discarding any in-flight proposal but keeping
     * the transcript would be jarring, so a settings change is treated as
     * "start a new conversation".
     *
     * @return `null` if AI settings are not configured (no provider / key).
     */
    fun session(): ConversationSession? {
        val settings = AiSettings.load(project) ?: run {
            LOG.info("session(): AI settings not configured for project ${project.name}")
            return null
        }
        val hash = settings.hashCode()
        val existing = _session
        if (existing != null && _builtSettingsHash == hash) {
            LOG.info("session(): reusing existing session (hash=$hash)")
            return existing
        }
        // Settings changed (or first build) — start a fresh conversation.
        LOG.info("session(): building new session (hash=$hash prevHash=$_builtSettingsHash)")
        val newSession = buildSession(settings)
        _session = newSession
        _builtSettingsHash = hash
        return newSession
    }

    /** Builds a [ConversationSession] from resolved [settings]. */
    private fun buildSession(settings: AiSettings): ConversationSession {
        val aiService = aiServiceFactory(settings)
        val configReader = ConfigReader.getInstance(project)
        val ruleFileResolver = RuleFileResolver(project)
        val memory = AgentMemory()
        val events = MutableSharedFlow<com.itangcent.easyapi.ai.agent.AgentEvent>(
            replay = 64,
            extraBufferCapacity = 64
        )
        // ApprovalGate: blocks ACTION tools until the UI completes the deferred.
        val approvals = UiApprovalGate()
        // ClarificationGate: suspends `ask_clarification` until the user answers.
        val clarifications = UiClarificationGate(events)
        // FileReadConsentGate: suspends `read_rule_file` for out-of-scope reads.
        val readConsents = UiFileReadConsentGate(events)
        val ctx = ToolContext(
            project = project,
            configReader = configReader,
            aiSettings = settings,
            ruleFileResolver = ruleFileResolver,
            workingMemory = memory,
            approvals = approvals,
            clarifications = clarifications,
            readConsents = readConsents
        )
        val tools = ToolRegistry(standardRuleTools())
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events)
        return ConversationSession(agent, memory, events, approvals, clarifications, readConsents)
    }

    /** Whether AI is configured and ready (i.e., [session] would return non-null). */
    fun isConfigured(): Boolean = AiSettings.load(project) != null

    /**
     * Discard the current conversation — clears memory and emits nothing
     * further. The next [session] call rebuilds lazily.
     */
    fun resetConversation() {
        _session?.memory?.reset()
        _session = null
        _builtSettingsHash = null
    }

    override fun dispose() {
        // Memory + flow are GC'd; any running Job is owned by the panel.
        _session = null
        _builtSettingsHash = null
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AiAssistantService =
            project.getService(AiAssistantService::class.java)
    }
}

/**
 * The per-conversation aggregate — agent + memory + events + gates.
 *
 * Held by [AiAssistantService]; the panel reads [events] and drives [agent].
 */
data class ConversationSession(
    val agent: RuleAuthoringAgent,
    val memory: AgentMemory,
    val events: MutableSharedFlow<com.itangcent.easyapi.ai.agent.AgentEvent>,
    val approvals: UiApprovalGate,
    val clarifications: UiClarificationGate = UiClarificationGate(events),
    val readConsents: UiFileReadConsentGate = UiFileReadConsentGate(events)
)

/**
 * [ApprovalGate] backed by a [CompletableDeferred] that the UI completes
 * when the user clicks Approve / Reject on an approval card.
 *
 * Each call to [await] replaces the inner deferred; the UI calls
 * [complete] with the user's decision. This is intentionally simple —
 * only one approval can be in flight at a time because the agent loop is
 * sequential.
 */
class UiApprovalGate : ApprovalGate {

    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        return deferred.await()
    }

    /** Complete the current pending approval with [approved]. No-op if none pending. */
    fun complete(approved: Boolean) {
        pending?.complete(approved)
        pending = null
    }

    /** Whether an approval is currently awaiting a user decision. */
    fun isPending(): Boolean = pending?.isActive == true
}

/**
 * [ClarificationGate] backed by a [CompletableDeferred] that the UI completes
 * when the user submits a clarification card (or types a free-form reply).
 *
 * On [await] it emits an [com.itangcent.easyapi.ai.agent.AgentEvent.ClarificationRequested]
 * so the panel renders the card, then suspends until [complete]/[completeRaw].
 * Only one clarification can be in flight at a time (the agent loop is
 * sequential). Completing twice is a no-op.
 */
class UiClarificationGate(
    private val events: MutableSharedFlow<com.itangcent.easyapi.ai.agent.AgentEvent>
) : ClarificationGate {

    @Volatile
    private var pending: CompletableDeferred<ClarificationAnswers>? = null

    override suspend fun await(request: Clarification): ClarificationAnswers {
        val deferred = CompletableDeferred<ClarificationAnswers>()
        pending = deferred
        events.emit(com.itangcent.easyapi.ai.agent.AgentEvent.ClarificationRequested(request))
        return deferred.await()
    }

    /** Complete the current pending clarification with [answers]. No-op if none pending. */
    fun complete(answers: ClarificationAnswers) {
        pending?.complete(answers)
        pending = null
    }

    /**
     * Complete the current pending clarification with a raw, typed free-form
     * reply (filed under [ClarificationAnswers.RAW_KEY]). No-op if none pending.
     */
    fun completeRaw(text: String) {
        complete(ClarificationAnswers(mapOf(ClarificationAnswers.RAW_KEY to listOf(text))))
    }

    /** Whether a clarification is currently awaiting a user response. */
    fun isPending(): Boolean = pending?.isActive == true
}

/**
 * [FileReadConsentGate] backed by a [CompletableDeferred] that the UI
 * completes when the user clicks Approve / Reject on a read-consent card
 * (mirrors [UiClarificationGate]).
 *
 * On [await] it emits an [com.itangcent.easyapi.ai.agent.AgentEvent.FileReadConsentRequested]
 * so the panel renders the card, then suspends until [complete] resumes it.
 * Only one consent can be in flight at a time (the agent loop is sequential).
 * Completing twice is a no-op. Each grant is single-use, per path — no
 * persistent allow-list.
 */
class UiFileReadConsentGate(
    private val events: MutableSharedFlow<com.itangcent.easyapi.ai.agent.AgentEvent>
) : com.itangcent.easyapi.ai.agent.FileReadConsentGate {

    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    override suspend fun await(requestedPath: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        events.emit(com.itangcent.easyapi.ai.agent.AgentEvent.FileReadConsentRequested(requestedPath))
        return deferred.await()
    }

    /** Complete the current pending consent with [approved]. No-op if none pending. */
    fun complete(approved: Boolean) {
        pending?.complete(approved)
        pending = null
    }

    /** Whether a read consent is currently awaiting a user decision. */
    fun isPending(): Boolean = pending?.isActive == true
}
