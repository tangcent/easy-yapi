package com.itangcent.easyapi.core.ai.agent

/**
 * Builds the two distinct instruction bodies used by Magic.
 *
 * Magic is not a single uniform flow: an empty rule file goes straight to
 * the detection-pass contract (Route A), while a non-empty file runs a
 * single Reactive review turn first and gates the user before optional
 * detections (Route B). The two flows need different instruction bodies,
 * each pure of side effect so it can be unit-tested in isolation.
 *
 * The **prompt text lives in resources**, not in this file: the three templates
 * under `src/main/resources/ai/magic/`. This class only loads the right file and
 * interpolates the data (the file name, the seeded-task manifest, the current
 * file content) — so prompt wording is reviewable as a diff and cannot hide
 * inside a Kotlin string literal.
 *
 * - [detectionInstruction] — the empty-file Magic body (Phase 3 final
 *   directive) from `detection-instruction.md`, or
 *   `detection-instruction-empty.md` when no detection matched the enabled
 *   features (FR-2.5). Tells the agent a task list has already been seeded into
 *   working memory, forbids `create_task_list`, and directs it to walk
 *   `PENDING` tasks one at a time by calling `run_sub_agent(taskId=...)`
 *   for each — the sub-agent runs the detection recipe in an isolated
 *   context and reports findings back. `run_sub_agent` auto-records the
 *   task status (completed if the sub-agent ran successfully, failed on
 *   error) and ticks the checklist card, so the orchestrator does NOT
 *   need to call `update_task` for the status afterwards. After all
 *   tasks close, the orchestrator calls `propose_rule_content` once with
 *   the merged findings. The orchestrator never calls perception tools
 *   itself (FR-3.2/3.3).
 *
 *   Crucially, the seeded task ids (e.g. `detect_spring_filters_interceptors`)
 *   are rendered into this body as an explicit manifest — the orchestrator
 *   LLM has no other way to discover them (the `TaskList` lives only in
 *   working memory + the UI panel; it is never serialized into the LLM
 *   transcript by the agent loop). The manifest lines are the only thing
 *   composed in code, because they are data, not prose.
 *
 * - [reviewInstruction] — the non-empty Magic Stage-1 body from
 *   `review-instruction.md`. A single Reactive review turn that directs the
 *   agent to review and improve the file. The file's current content is
 *   embedded in a fenced block. It contains no task-list directive, no
 *   `update_task`, and no detection language — the gate (Route B Stage 2)
 *   decides whether to enter the detection-pass contract.
 *
 * A missing/empty resource is a packaging error, not a runtime condition to
 * tolerate: [load] throws rather than degrading the directive into a no-op that
 * would leave the orchestrator guessing. `MagicInstructionBuilderTest` pins the
 * anchors each body must keep.
 */
object MagicInstructionBuilder {

    private const val DETECTION_RESOURCE = "/ai/magic/detection-instruction.md"
    private const val DETECTION_EMPTY_RESOURCE = "/ai/magic/detection-instruction-empty.md"
    private const val REVIEW_RESOURCE = "/ai/magic/review-instruction.md"

    private const val NAME_PLACEHOLDER = "{{name}}"
    private const val MANIFEST_PLACEHOLDER = "{{manifest}}"
    private const val CONTENT_PLACEHOLDER = "{{content}}"

    private val detectionTemplate: String by lazy { load(DETECTION_RESOURCE) }
    private val detectionEmptyTemplate: String by lazy { load(DETECTION_EMPTY_RESOURCE) }
    private val reviewTemplate: String by lazy { load(REVIEW_RESOURCE) }

    /**
     * The instruction body for the empty-file Magic flow (Route A) —
     * Phase 3 final directive (design §3.8 / FR-3.8).
     *
     * Tells the agent a task list has been seeded with one `PENDING` task
     * per detection pattern; forbids `create_task_list`; directs it to
     * walk each `PENDING` task by calling `run_sub_agent(taskId=...)` —
     * the sub-agent perceives the PSI, decides whether the pattern is
     * present, and reports back via `report_findings`. `run_sub_agent`
     * **auto-records** the task status (`completed` if the sub-agent ran
     * successfully — whether or not it detected the pattern; `failed` on
     * error) and ticks the checklist card in the UI, so the orchestrator
     * does NOT need to call `update_task` for the status afterwards.
     * "Nothing detected" is a valid finding, not a skip. After every task
     * is closed, the orchestrator calls `propose_rule_content` **once** —
     * the tool merges the collected sub-agent findings and rules itself
     * (design §3.9 / D3), so the orchestrator does NOT need to compose the
     * merged content. If no rule was drafted, the tool stages no proposal
     * and the turn ends without prompting the user.
     *
     * The orchestrator has **no perception tools** (FR-3.2) — it must not
     * call `find_classes_by_annotation`, `get_detection_prompt`, etc.
     * All perception happens inside sub-agents. The orchestrator only
     * coordinates: `run_sub_agent` → `propose_rule_content`.
     *
     * The seeded [taskList] is rendered into the body as an explicit
     * manifest of `(id, title)` pairs. This is the **only** channel by
     * which the orchestrator LLM learns the exact task ids — the
     * `TaskList` lives in `AgentMemory` and the UI panel but is never
     * serialized into the LLM transcript by `RuleAuthoringAgent.runTurn`.
     * Without the manifest the LLM would have to guess the ids and
     * `run_sub_agent` would reject every guess with "unknown task id".
     *
     * Empty task list (no detection matched the enabled features, FR-2.5):
     * the body short-circuits to
     * `detection-instruction-empty.md` — a single directive to call
     * `propose_rule_content` once and end the turn — so the turn still
     * terminates normally instead of looping on a zero-task manifest.
     *
     * @param name the rule file's display name (used in the opening line).
     * @param taskList the seeded task list. Its ids are rendered verbatim
     *   into the manifest; the orchestrator must use them unchanged in
     *   `run_sub_agent(taskId=...)` and `update_task(taskId, ...)`.
     */
    fun detectionInstruction(name: String, taskList: TaskList): String {
        if (taskList.tasks.isEmpty()) {
            // FR-2.5 — no detection matched the enabled features. Direct the
            // orchestrator straight to the terminal action so the turn ends
            // normally instead of looping on an empty manifest.
            return detectionEmptyTemplate.replace(NAME_PLACEHOLDER, name)
        }

        val manifest = taskList.tasks
            .mapIndexed { index, task -> "${index + 1}. ${task.id} — ${task.title}" }
            .joinToString("\n")

        return detectionTemplate
            .replace(NAME_PLACEHOLDER, name)
            .replace(MANIFEST_PLACEHOLDER, manifest)
    }

    /**
     * The instruction body for the non-empty Magic flow (Route B Stage 1).
     *
     * Directs the agent to review and improve the file. The file's current
     * content is embedded in a fenced block. Contains no task-list
     * directive, no `update_task`, and no detection language — the gate
     * decides whether to enter the detection-pass contract (Route B
     * Stage 2), and Stage 2 itself runs the [detectionInstruction] body
     * with the file content read at gate-Yes time.
     *
     * @param name the rule file's display name (used in the opening line).
     * @param content the file's current content (embedded in a fenced
     *   block so the agent can review it).
     */
    fun reviewInstruction(name: String, content: String): String =
        reviewTemplate
            .replace(NAME_PLACEHOLDER, name)
            .replace(CONTENT_PLACEHOLDER, content)

    /**
     * Loads a prompt resource from the plugin classpath.
     *
     * Throws when the resource is missing or blank: the file ships in the same
     * JAR as this class, so its absence means the build/package is broken —
     * silently emitting an empty directive would be far worse than failing.
     */
    private fun load(path: String): String {
        val text = javaClass.getResourceAsStream(path)
            ?.use { it.reader(Charsets.UTF_8).readText() }
        check(!text.isNullOrBlank()) {
            "missing or empty prompt resource: $path — the plugin JAR is incomplete"
        }
        return text
    }
}
