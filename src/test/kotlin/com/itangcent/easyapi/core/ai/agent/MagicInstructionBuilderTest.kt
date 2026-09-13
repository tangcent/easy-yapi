package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [MagicInstructionBuilder].
 *
 * Regression coverage for the "unknown task id" loop: the orchestrator LLM
 * could not discover the seeded task ids because they lived only in
 * `AgentMemory.taskList` + the UI panel and were never serialized into the
 * LLM transcript. The fix renders an explicit `(id, title)` manifest into
 * [MagicInstructionBuilder.detectionInstruction]; these tests pin that
 * contract so it cannot regress.
 *
 * The prompt text itself now lives in resources under `ai/magic/`; the
 * builder only loads and interpolates it. These tests therefore also guard the
 * *anchors* each body must keep, so moving a wording change into a resource
 * file cannot silently drop a directive.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class MagicInstructionBuilderTest {

    // ------------------------------------------------------------------
    // detectionInstruction — the manifest is the orchestrator's only
    // source of the exact task ids it must pass to run_sub_agent /
    // update_task.
    // ------------------------------------------------------------------

    /**
     * Regression for the "unknown task id" loop: every seeded task id MUST
     * appear verbatim in the instruction so the orchestrator LLM can use it
     * unchanged in `run_sub_agent(taskId=...)`.
     */
    @Test
    fun detectionInstructionListsSeededTaskIdsVerbatim() {
        val taskList = TaskList(
            listOf(
                Task("detect_spring_filters_interceptors", "Spring filters, interceptors, web filters"),
                Task("detect_static_auth", "Static auth headers"),
                Task("detect_auth_token_chaining", "Auth-token chaining")
            )
        )

        val body = MagicInstructionBuilder.detectionInstruction(".easy.api.rules", taskList)

        taskList.tasks.forEach { task ->
            assertTrue(
                "instruction must list task id verbatim: ${task.id}\n$body",
                body.contains(task.id)
            )
            assertTrue(
                "instruction must pair id ${task.id} with its title '${task.title}'\n$body",
                body.contains(task.title)
            )
        }
    }

    /**
     * The detection *family* ids (kebab-case, no `detect_` prefix — the ones
     * the base prompt mentions, e.g. `spring-filters-interceptors`) are NOT
     * valid task ids and must NOT appear as bare callable tokens. This guards
     * against the LLM picking a kebab family id (the original failure mode).
     *
     * We assert the *manifest line* uses the `detect_*` form, not the bare
     * kebab form. The body may still mention family names in prose, so we
     * check the manifest-shaped occurrences specifically.
     */
    @Test
    fun detectionInstructionDoesNotOfferKebabFamilyIdAsCallable() {
        val taskList = TaskList(
            listOf(
                Task("detect_spring_filters_interceptors", "Spring filters, interceptors, web filters")
            )
        )

        val body = MagicInstructionBuilder.detectionInstruction(".easy.api.rules", taskList)

        // The numbered manifest entry must be the detect_* form.
        assertTrue(
            "manifest must enumerate the detect_* id:\n$body",
            body.contains("1. detect_spring_filters_interceptors")
        )
        // The bare kebab family id must NOT be offered as a numbered manifest
        // entry (it is not a valid task id).
        assertFalse(
            "manifest must NOT offer the bare kebab family id as a callable task id:\n$body",
            body.contains("1. spring-filters-interceptors")
        )
    }

    /**
     * The instruction must keep the two-turn directive (run_sub_agent →
     * propose_rule_content) and the prohibition on create_task_list /
     * perception tools, so adding the manifest didn't drop any of the
     * existing contract. `run_sub_agent` auto-records the task status
     * (FR-3.5/FR-3.6 post-ship fix-up), so the directive no longer tells
     * the orchestrator to call `update_task` for the status — the test
     * asserts the directive mentions the auto-record behaviour instead.
     */
    @Test
    fun detectionInstructionKeepsTwoTurnDirective() {
        val taskList = TaskList(
            listOf(Task("detect_static_auth", "Static auth headers"))
        )

        val body = MagicInstructionBuilder.detectionInstruction(".easy.api.rules", taskList)

        assertTrue("must forbid create_task_list", body.contains("Do NOT call create_task_list"))
        assertTrue("must direct to run_sub_agent", body.contains("run_sub_agent(taskId=...)"))
        assertTrue(
            "must mention run_sub_agent auto-records the status",
            body.contains("automatically records the task")
        )
        assertTrue("must direct to propose_rule_content", body.contains("propose_rule_content"))
        assertTrue(
            "must forbid perception tools",
            body.contains("Do NOT call") && body.contains("perception tools")
        )
    }

    /**
     * FR-2.5 — when no detection matched the enabled features, the task list
     * is empty. The instruction must NOT emit a bogus zero-task manifest nor
     * direct the orchestrator to call run_sub_agent / update_task on
     * non-existent tasks. Instead it short-circuits to a single
     * propose_rule_content directive so the turn terminates normally.
     */
    @Test
    fun detectionInstructionEmptyTaskListDirectsStraightToPropose() {
        val empty = TaskList(emptyList())

        val body = MagicInstructionBuilder.detectionInstruction(".easy.api.rules", empty)

        // Must direct the orchestrator to end the turn with propose_rule_content.
        assertTrue(
            "empty-task instruction must direct to propose_rule_content:\n$body",
            body.contains("propose_rule_content")
        )
        // Must NOT tell the orchestrator to walk PENDING tasks (there are none).
        assertFalse(
            "empty-task instruction must NOT direct to run_sub_agent:\n$body",
            body.contains("For each PENDING task")
        )
        // Must NOT render a numbered manifest line (there are no tasks).
        assertFalse(
            "empty-task instruction must NOT render a manifest entry:\n$body",
            body.contains("1. ")
        )
    }

    // ------------------------------------------------------------------
    // reviewInstruction — guard against the detectionInstruction signature
    // change bleeding into the Route B Stage-1 review body.
    // ------------------------------------------------------------------

    /**
     * The Route B Stage-1 review body embeds the file content and carries no
     * task-list directive. Pinning this so the detectionInstruction refactor
     * can't accidentally change reviewInstruction's contract.
     */
    @Test
    fun reviewInstructionEmbedsContentAndHasNoTaskListDirective() {
        val content = "method.additional.header={\"name\":\"X-Trace\",\"value\":\"\${uuid}\"}"
        val body = MagicInstructionBuilder.reviewInstruction(".easy.api.rules", content)

        assertTrue(
            "review body must embed the file content:\n$body",
            body.contains(content)
        )
        // No detection-pass directive leaks into the review body.
        assertFalse(
            "review body must NOT contain the detection manifest directive:\n$body",
            body.contains("Seeded tasks")
        )
        assertFalse(
            "review body must NOT reference run_sub_agent:\n$body",
            body.contains("run_sub_agent")
        )
        assertFalse(
            "review body must NOT reference update_task:\n$body",
            body.contains("update_task")
        )
    }

    // ------------------------------------------------------------------
    // Smoke: detectionInstruction composes with a real built plan.
    // ------------------------------------------------------------------

    /**
     * End-to-end-ish: build a real plan via [MagicTaskListBuilder] and render
     * it. Every task id the builder produced must appear verbatim in the
     * rendered instruction — the contract the orchestrator's LLM relies on.
     */
    @Test
    fun detectionInstructionRenderedPlanListsEveryBuiltTaskId() {
        val plan = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        if (plan.tasks.isEmpty()) return // catalog has no unscoped detections

        val body = MagicInstructionBuilder.detectionInstruction(".easy.api.rules", plan)

        plan.tasks.forEach { task ->
            assertTrue(
                "rendered instruction must contain built task id ${task.id}:\n$body",
                body.contains(task.id)
            )
        }
    }

    // ------------------------------------------------------------------
    // Resource templates — the prompt text lives in ai/magic/*.md and is
    // interpolated here. A typo in a placeholder would ship the literal
    // token to the LLM, so pin that each body is fully substituted.
    // ------------------------------------------------------------------

    /**
     * Every template's `{{…}}` placeholder must be substituted in the rendered
     * body, and each body must name the rule file it applies to.
     */
    @Test
    fun everyBodySubstitutesItsPlaceholders() {
        val seeded = MagicInstructionBuilder.detectionInstruction(
            ".easy.api.rules",
            TaskList(listOf(Task("detect_static_auth", "Static auth headers")))
        )
        val empty = MagicInstructionBuilder.detectionInstruction(
            ".easy.api.rules",
            TaskList(emptyList())
        )
        val review = MagicInstructionBuilder.reviewInstruction(".easy.api.rules", "api.name=Demo")

        mapOf("seeded" to seeded, "empty-task" to empty, "review" to review).forEach { (label, body) ->
            assertFalse(
                "the $label body must not leak an unsubstituted placeholder:\n$body",
                body.contains("{{")
            )
            assertTrue(
                "the $label body must name the rule file:\n$body",
                body.contains(".easy.api.rules")
            )
        }
    }
}
