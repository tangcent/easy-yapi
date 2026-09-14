# AI Module

The reference for EasyApi's AI subsystem. Read this before touching anything
under `core/ai/`, `src/main/resources/ai/`, or `skills/easy-yapi-assistant/`.

---

## 1. Overview

### 1.1 What this page is for

It answers exactly three questions, and nothing else:

1. **What are the parts, and where are the boundaries?** Which component owns
   which fact, and what invariants must hold.
2. **How does one turn run?** From the entry point to a staged proposal.
3. **What do I do when I change something?** Which file is the single source of
   truth, which sync task regenerates the derived copies, and which guard test
   will stop me.

Three rules keep it that way:

- **Contributor conventions are not repeated here.** Threading, logging, package
  layout, and testing are normative in [`AGENTS.md`](../../AGENTS.md) — this page
  links to them. Debugging the agent is a logging question, not a subsystem
  question; see `AGENTS.md` § Logging.
- **Generated content is never enumerated here.** Rule keys, script contexts, the
  tool inventory, and locale/EP listings have generated files; this page points at
  them. Anything derivable from code is listed once, in the file that derives it.
- **Counts are not written here either.** "19 tools", "10 detection recipes",
  "23 providers" rot silently — no test guards a number in prose. Counts live in
  the generated file, or nowhere.

**Keeping it current:** a change to the runtime (§2), a tool registry (§3), a
prompt resource (§4), or a backend setting (§5) must update the matching table on
this page. Everything else here is structural and changes rarely.

### 1.2 The five parts

Listed in the same order as the sections below, so this table doubles as the
table of contents.

| # | Part | Responsibility | Home | § |
|---|------|----------------|------|---|
| 1 | **Runtime** | Drives one turn: the perceive→reason→act loop, memory, events, safety valves | `core/ai/agent/` | [§2](#2-runtime) |
| 2 | **Tools** | What the agent can call, split into three role-scoped registries | `core/ai/tools/` | [§3](#3-tools) |
| 3 | **Prompts and knowledge assets** | Entry base prompts, catalog index, on-demand bodies, and how every asset is kept in sync | `src/main/resources/ai/`, `docs/knowledge-base/` | [§4](#4-prompts-and-knowledge-assets) |
| 4 | **LLM backend and settings** | Provider adapters, config, credentials, timeouts, token budget | `core/ai/*.kt` | [§5](#5-llm-backend-and-settings) |
| 5 | **Surfaces** | The two things people actually touch: the chat panel and the external skill | `core/ai/ui/`, `skills/easy-yapi-assistant/` | [§6](#6-chat-ui), [§7](#7-external-skill) |

The in-plugin knowledge base (`docs/knowledge-base/`) is part of part 3 — it is
served to the agent by the `get_plugin_doc` tool, and shipped to users as-is.

### 1.3 Code and resource map

```
src/main/kotlin/com/itangcent/easyapi/core/ai/
├── agent/                       # runtime kernel
│   ├── EntryPath.kt             #   the three entry paths
│   ├── RuleAuthoringAgent.kt    #   turn loop + TurnOutcome
│   ├── SystemPromptBuilder.kt   #   prompt assembly, per EntryPath
│   ├── PromptCatalog.kt         #   catalog manifest + entry bodies
│   ├── MagicInstructionBuilder.kt    # loads the Magic templates (see §4.4)
│   ├── MagicTaskListBuilder.kt  TaskList.kt
│   ├── AgentMemory.kt  KnowledgeState.kt  AmbientPerception.kt
│   ├── AgentEvent.kt
│   ├── ApprovalGate.kt  ClarificationGate.kt  FileReadConsentGate.kt
│   ├── LoopGuard.kt  LoopSafetyConfig.kt  ChatRetry.kt
│   ├── Clarification.kt  MessageTokens.kt  TaskResult.kt
├── tools/                       # capabilities
│   ├── AiTool.kt                #   interface + ToolKind + ToolContext + ToolResult
│   ├── ToolRegistry.kt          #   dispatch / schemas / approval / timeout
│   ├── RuleTools.kt             #   the three registry factories — single source of truth
│   └── *Tool.kt                 #   tool implementations
├── credentials/                 # credential discovery for the settings page (see §5.3)
├── ui/AiChatPanel.kt            # chat panel: event rendering + proposal saving
├── AIService.kt  AIServiceFactory.kt  LangChain4jAIService.kt
├── AiRuntimeConfig.kt  AiProvider.kt  AiModels.kt  AiApiKeyStore.kt
└── TokenSizeUtils.kt  AiAssistantService.kt  ChatTimeoutException.kt

src/main/resources/ai/                    # prompt resources (single source)
├── agent-base.md                         #   T0 base prompt (main agent)
├── sub-agent-base.md                     #   T0 base prompt (sub-agent)
├── catalog-manifest.txt                  #   catalog index (hand-maintained)
├── detection/*.md                        #   detection recipes
├── key-guides/*.md                       #   per-key guides
└── magic/*.md                            #   Magic instruction templates

skills/easy-yapi-assistant/               # external skill: mirrors, CLI, generated catalogs
docs/knowledge-base/                      # user knowledge base, read via get_plugin_doc
```

AI settings are **not** here: they live in `core/settings/module/AiSettings.kt` at
application scope, and are resolved into `AiRuntimeConfig` — see §5.2.

---

## 2. Runtime

### 2.1 Entry paths

`EntryPath` decides the **seed prompt** and the **tool set** for a turn. The loop
itself is identical on every path, and the caller picks the path — the agent never
decides "this looks complex, switch mode".

| EntryPath | Opening system messages | Tool registry | Used by |
|---|---|---|---|
| `REACTIVE` | **4**: base + detection index + key-guide index + rule-key menu | `standardRuleTools()` | Plain chat; Route B stage 1 (review) |
| `TASK_LIST_PROGRAMMATIC` | **1**: base | `orchestratorToolRegistry(...)` | The Magic detection pass (`RuleFileEditDialog` → `AiChatPanel.runTaskList`) |
| `SUB_AGENT` | **1**: `sub-agent-base.md` | `subAgentToolRegistry()` | Sub-agents spawned by `RunSubAgentTool` |

The `REACTIVE` path gets *indexes* (id + title + cue) and pulls bodies on demand;
the other two pull everything inside their tasks. Keeping the index in the opening
messages and the bodies out of them is what keeps the token budget flat as the
catalog grows.

### 2.2 The turn loop

`RuleAuthoringAgent.runTurn(userMessage, memory, ambient, entryPath)`:

```
1. Capture (or reuse) the Ambient observation; store it on memory.
2. If memory.messages is empty → seed with SystemPromptBuilder.build(entryPath, amb).
   Otherwise → refreshStaleKnowledge(...): when the enabled channels/formats/
   frameworks changed since the last turn, invalidate §keys / §keyContexts and
   rebuild the leading system block.
3. Append this turn's ambient system message + the user message.
4. Build a fresh LoopGuard and ChatRetry (per-turn state; never shared).
5. while (step < aiSettings.maxRequests):          # default 100
     emit Thinking(step + 1)
     budget = contextWindowToBudget(contextWindow) - knowledgeState.estimatedTokens()
     trimToTokenBudget(memory, budget)
     aiService.chat(AiChatRequest(messages, tools.schemas()))
     ├─ exception → CancellationException rethrown; ChatRetriesExhausted or any
     │  other failure → emit Failed + return Answered (no finish())
     ├─ no tool calls → emit Message(text) + finish()          # COMMUNICATE
     └─ tool calls, one by one:
          guard.checkBeforeDispatch → Block writes a synthetic Error (debounce)
          emit Perceiving (PERCEPTION) / Acting (ACTION)
          if ACTION and requiresApproval → emit ApprovalRequested
          tools.dispatch(...)                    # approval + timeout happen inside
          Stateful result → merge into knowledgeState, keep only a receipt in the transcript
          append the tool result + emit Observed
          guard.observeResult → Terminate ends the turn via endLoopDetected()
          if name is a terminal action → finish()
     step++
6. Budget exhausted → TurnOutcome.StepLimitHit
```

`TurnOutcome` is one of `Proposed` / `Answered` / `StepLimitHit` / `LoopDetected`.

- `finish()` emits `ProposalReady` when `memory.proposal != null`, then `TurnComplete`.
- `endLoopDetected()` emits a terminal `LoopDetected` and **no** `TurnComplete` / `ProposalReady`.
- A chat failure emits `Failed` and returns `Answered`; the step-limit exit emits
  nothing at all. Both skip `finish()`, so neither yields `ProposalReady` /
  `TurnComplete`.

**Terminal actions are per-role.** The loop treats `propose_rule_content` and
`report_findings` as terminal; each role's registry contains exactly one of them, so
the check is harmless for the other role.

### 2.3 Events

`AgentEvent` is the only channel between the runtime and the UI; a
`MutableSharedFlow` (replay = 64) carries them. Sub-agents share the orchestrator's
flow, so sub-agent activity is visible in the panel.

| Group | Events |
|---|---|
| Loop | `Thinking(step)` · `Perceiving(tool, args)` · `Acting(tool, args)` · `Observed(tool, summary)` · `Retrying(attempt, max)` |
| Gates | `ApprovalRequested(tool, args)` · `ClarificationRequested(c)` · `FileReadConsentRequested(path)` |
| Output | `Message(content)` · `ProposalReady(proposal)` · `TurnComplete` |
| Failure | `Failed(reason)` · `LoopDetected(reason, tool?, count)` |
| Task list | `TaskListCreated(list)` · `TaskStarted(id)` · `TaskCompleted(id)` · `TaskFailed(id, reason)` · `TaskSkipped(id)` |

### 2.4 Per-conversation state

`AgentMemory`, reused across turns of one conversation and cleared by `reset()`:

| Member | Purpose |
|---|---|
| `messages` | The full transcript |
| `proposal` | Staged by `propose_rule_content`; the UI's "Save…" reads it |
| `ambient` | Most recent `Ambient` observation |
| `taskList` | Magic's seeded task list; always `null` on the Reactive path |
| `collectedSubAgentResults` | `(Task, TaskResult)` pairs collected during one Magic detection pass, merged by the terminal action |
| `knowledgeState` | External knowledge that does not belong in the transcript (below) |
| `openingSystemCount` | How many leading system messages to replace on a mid-conversation refresh |

`KnowledgeState` has three sections, each with **exactly one writer**:
`§keys` (`list_rule_keys`), `§keyContexts` (`get_rule_context`), `§objects`
(`get_script_object_api`). Its `render()` output is injected as a system message at
request time and its token cost is reserved separately, which is why a tool can
return a large catalog without consuming transcript budget.

`Ambient` (captured by `AmbientPerception`) carries: project name, the rule file
being edited, the other rule files, module names, detected framework hints, the
enabled channels/formats, and a non-English user language. It reads module names
and framework tags only — never environment variables. That is a rule about the
*agent*; the settings page's credential discovery does read the environment, on a
user's explicit click — see §5.3.

### 2.5 The three gates

| Gate | Gates | Default | UI |
|---|---|---|---|
| `ApprovalGate` | ACTION tools with `requiresApproval = true`, awaited inside `ToolRegistry.dispatch` | — | Approve / Reject card |
| `ClarificationGate` | `ask_clarification`, which suspends until the user answers | `NOOP` (answers immediately, empty) | Single/multi choice + free text + "Other…" |
| `FileReadConsentGate` | `read_rule_file` for paths outside the allowed rule directories | `NOOP` (**denies**) | Allow once / Deny — not persisted |

Note that the approval gate is effectively dormant: the only tool with
`requiresApproval = true` is `write_rule_file`, which is not registered in any
registry (see §3.3). Every other ACTION tool opts out.

### 2.6 Loop and retry safety

`LoopSafetyConfig` defaults — not user-configurable today; tests override per case:
`repetitionThreshold = 3`, `cycleRepetitions = 2`, `maxCyclePeriod = 4`,
`debounceEnabled = true`, `chatMaxRetries = 2`, `chatBackoffBaseMs = 1000`,
`chatBackoffMaxMs = 8000`. The constructor rejects out-of-range values.

`LoopGuard` (fresh per turn) detects four shapes, cheapest first:

| # | Detection | Reason |
|---|---|---|
| A | The same `(tool, normalized args)` fingerprint `repetitionThreshold` times in a row | `ConsecutiveDuplicate` |
| B | A call cycle with period 2–4 (A→B→A→B) | `CallCycle` |
| C | Identical result fingerprints across **batch** boundaries (identical results *within* one batch are legitimate parallel probing) | `OutputStagnation` |
| D | Identical normalized reasoning text repeated at the tail | `ReasoningRepetition` |

`ChatRetry.chatWithRetry` rethrows `CancellationException` immediately; non-transient
failures and exhausted retries surface as `ChatRetriesExhausted`; everything else
retries with exponential backoff (base → max, plus 0–250 ms jitter) using `delay`.
Transient is decided in three steps inside `ChatRetry.isTransient` — read that
function for the lists rather than mirroring them here:

1. deny-list by **type** (`IllegalArgumentException`, `IllegalStateException`);
2. allow-list by **type** (`IOException`, `ChatTimeoutException`);
3. message substrings, non-transient first (`401` / `403` / `invalid api key` /
   `model not found` / …), then transient (`429` / `rate limit` / `timeout` /
   `5xx` / `unavailable` / …). Anything unmatched is treated as **non-transient**.

### 2.7 The Magic task-list path

```
core/settings/ui/RuleFileEditDialog.onMagic
 ├─ file empty      → runDetectionPass            (Route A)
 └─ file non-empty  → runReviewTurn               (Route B stage 1, REACTIVE)
                       └─ ReviewGate → Yes → runDetectionPass   (Route B stage 2)

runDetectionPass:
  off the EDT, capture ambient → MagicTaskListBuilder.buildDetectionPlan(...)
    · source: PromptCatalog.listFor("detection", enabled...) — the same filter the
      Reactive index uses
    · id = "detect_" + entry.id with every non-alphanumeric char replaced by "_"
    · Task(title = entry.title, detail = entry.cue, status = PENDING)
  → MagicInstructionBuilder.detectionInstruction(name, taskList)
    · the prompt body is ai/magic/detection-instruction.md, or
      detection-instruction-empty.md when nothing was seeded
  → AiChatPanel.runTaskList(...): store taskList on memory, emit TaskListCreated
  → startTurn(EntryPath.TASK_LIST_PROGRAMMATIC) — the orchestrator takes over
```

The rendered `N. <id> — <title>` manifest is the **only** channel by which the
orchestrator learns the task ids: the `TaskList` lives in memory and the UI panel
and is never serialized into the transcript, so without it the model would guess
ids and `run_sub_agent` would reject every guess.

### 2.8 Orchestrator ↔ sub-agent protocol

```
orchestrator                                  sub-agent
  run_sub_agent(taskId=…)
    ├─ taskList.byId(taskId); unknown id → recoverable Error listing valid ids
    ├─ buildSubAgentInstruction(task)
    │    the id must start with "detect_"; "_" is mapped back to "-" to recover the
    │    detection id; the recipe body comes from PromptCatalog.body("detection", id)
    │    (in-process — the sub-agent deliberately has no get_detection_prompt)
    ├─ fresh AgentMemory() + CompletableDeferred<TaskResult>
    │    subCtx = ctx.copy(workingMemory = subMemory, subAgentResult = deferred)
    ├─ RuleAuthoringAgent(aiService, subAgentTools, subCtx, ctx.events)
    │    .runTurn(…, EntryPath.SUB_AGENT)
    │       └─ the sub-agent calls report_findings → deferred.complete(TaskResult)
    ├─ completed ? mark the task COMPLETED : synthesize detected=false + FAILED
    ├─ collectedSubAgentResults += (task to result)      # regardless of detected
    └─ return serialize(task, result) = taskId / detected / findings / proposedRules
  …
  propose_rule_content(suggestedFileName)
    └─ collected non-empty → mergeTaskResults(collected)   # deterministic, ignores the LLM's content
       ├─ output is valid rule-file content: rule lines interleaved with `#` comment
       │  blocks carrying each detection's findings
       ├─ no rule line at all (every task not detected, or nothing drafted) → stage nothing
       └─ CompositeRuleValidator: hard errors → ToolResult.Error, warnings → `# Reviewer notes:`
```

The sub-agent seed instruction (current text):

```
You are a sub-agent running one detection task: <title>.
Cue: <detail>

Detection recipe:
<detection markdown body>

Run the suggested searches to confirm whether the pattern is present in this
project's PSI. If it is, draft the concrete rule proposals for it: confirm the key
name with list_rule_keys, fetch its value-format guide with get_rule_detail(key=...),
and check for existing rules with get_existing_rules_for_key — never propose a
duplicate. When done, call report_findings with detected=true — put the evidence
(located classes, signatures, why the pattern applies) in findings and the drafted
rules in proposedRules (each {key, rules}, where rules is the COMPLETE rule line to
append verbatim — not a summary) — or detected=false with your search notes in
findings if nothing matched. Do NOT call propose_rule_content — the orchestrator
merges the sub-agents' findings and proposals and stages the final proposal once.
```

The `Cue:` line only appears when the task carries a detail. The sub-agent's system
prompt comes separately from `sub-agent-base.md`; the text above is the *user*
message.

**Why the merge uses `#` comments.** `ConfigTextParser` skips lines starting with a
single `#`, so the merged content parses to exactly the drafted rules while the
reasoning stays next to them as comments. That is what makes the staged proposal
saveable as a `.rules` file — the merge is not a findings report. Use a single `#`:
`###` is the directive prefix and is **not** skipped.

---

## 3. Tools

### 3.1 The `AiTool` contract

```kotlin
interface AiTool {
    val name: String
    val description: String
    val kind: ToolKind                        // PERCEPTION | ACTION
    val requiresApproval: Boolean get() = kind == ToolKind.ACTION
    val timeoutMs: Long get() = 30_000L       // 0 disables the timeout
    val parametersSchema: Map<String, Any?>   // JSON schema handed to the LLM
    suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult
}
```

`ToolKind` classifies a tool by whether it can affect anything **outside the
conversation**:

- `PERCEPTION` — no external effect. It reads PSI/config and may update the
  conversation's own bookkeeping (`list_rule_keys` fills the knowledge state;
  `create_task_list` / `update_task` write `taskList` and emit task events). Runs
  automatically, never gated.
- `ACTION` — acts beyond the conversation (writes a file, mutates project state) or
  *is* the terminal staging step. Gated unless `requiresApproval` is overridden.

The dividing line is the external effect, not "does it write anything" — which is why
the task-list tools are perception tools despite mutating agent state.

`ToolContext` carries `project`, `configReader`, `aiSettings`, `ruleFileResolver`,
`workingMemory`, the three gates, the event flow, and `subAgentResult` (non-null only
in sub-agent contexts — it is the slot `report_findings` completes).

`ToolResult` is `Text(value)` (fed back to the LLM), `Error(message)` (recoverable),
or `Stateful(section, entries, receiptNote)` (merged into `KnowledgeState`, with only
a short receipt left in the transcript).

PSI reads inside a tool must be wrapped in `core.internal.threading.read { }` — see
[`AGENTS.md` § Threading Model](../../AGENTS.md#threading-model).

### 3.2 `ToolRegistry` dispatch

- `schemas()` → `AiToolSpec(name, description, parametersSchema as JSON)`, sent as
  the `tools` field of every `AiChatRequest`.
- `dispatch(name, args, ctx)`:
  1. unknown tool → `Error("Unknown tool: …")` (never throws)
  2. log `tool call: <name> args=<json>` at INFO
  3. `ACTION` + `requiresApproval` → await the approval gate; a rejection returns
     `Error("User rejected action: …")`
  4. `timeoutMs <= 0` runs unbounded, otherwise `withTimeout`
  5. any exception → `LOG.warn` + `Error("Tool '<name>' failed: …")`
  6. log the result at INFO, truncating a `Text` body to 500 chars

Tools are keyed by name, and the map is built with `associateBy { it.name }` — so two
tools **may** share a name only if they never appear in the same registry; within one
registry a collision would silently shadow the earlier entry (still advertised by
`schemas()`, but no longer dispatchable). `ToolRegistry` itself still takes the last
entry, so `ToolRegistryNameGuardTest` pins uniqueness inside each registry — a
collision fails the build instead of shipping. Cross-registry reuse is deliberate:
`propose_rule_content` (§3.3) is two classes under one name, and `update_task` is one
class in two registries — what matters is that no name repeats *within* a registry.

### 3.3 The three registries

`RuleTools.kt` is the single source of truth for the tool set. Composition, not
count — the counts are generated into `tools.md`:

| Factory | Composition | Serves |
|---|---|---|
| `standardRuleTools()` | every perception tool, plus `propose_rule_content` (ACTION) | `REACTIVE` |
| `orchestratorToolRegistry(aiService, subAgentTools)` | `update_task`, `run_sub_agent`, `propose_rule_content` | `TASK_LIST_PROGRAMMATIC` |
| `subAgentToolRegistry()` | the perception set for one detection task, plus `report_findings` (ACTION) | `SUB_AGENT` |

Two deliberate asymmetries:

- **`propose_rule_content` is two classes under one name.**
  `ProposeRuleContentTool` (Reactive) validates the LLM's content and stages it;
  `OrchestratorProposeRuleContentTool` ignores the LLM's content and merges the
  collected sub-agent results instead. They never share a registry.
- **The orchestrator has no perception tools at all.** It coordinates; every PSI read
  happens inside a sub-agent. That is also why the sub-agent, not the orchestrator,
  drafts rule values — it is the role holding `get_rule_detail` (value formats) and
  `get_existing_rules_for_key` (duplicate check).

`write_rule_file` exists but is registered nowhere: disk writes go through the
user-confirmed "Save…" flow only.

### 3.4 Generated inventory and CLI mirrors

The full inventory is generated —
[`skills/easy-yapi-assistant/tools.md`](../../skills/easy-yapi-assistant/tools.md):
per tool, its name, kind, approval, timeout, implementation class, registry
membership, and its CLI mirror if it has one. Regenerate with `syncSkillFacts` (§9.5).

Bundled scripts under `skills/easy-yapi-assistant/scripts/` mirror some of those
tools; the script↔tool mapping is declared in `SkillFactsExporter.SCRIPT_TO_TOOL`,
rendered into `tools.md`, and validated at generation time — adding a script without
classifying it fails the build rather than silently vanishing. Two of the scripts
mirror a *registry or prompt layer* rather than a tool; `tools.md` marks which is
which.

The remaining tools have no CLI equivalent because they need IntelliJ (PSI, the
`ApiIndex` cache) or are handled by the external assistant's own file and search
capabilities — [`SKILL.md`](../../skills/easy-yapi-assistant/SKILL.md) documents that
emulation per tool.

---

## 4. Prompts and knowledge assets

### 4.1 The three tiers

```
L0 — in context every turn
  agent-base.md / sub-agent-base.md
  └─ REACTIVE additionally seeds three indexes:
       detection index       id — title: cue
       key-guide index       id — title: cue
       rule-key menu         from RuleKeyCatalog.SOURCES (not filtered by enablement)

L1 → L2 — fetched on demand
  get_detection_prompt(id)   → ai/detection/<id>.md
  get_rule_detail(key)       → ai/key-guides/<key>.md (falls back to the scheme profile)
  get_plugin_doc(name)       → docs/knowledge-base/<name>.md
  list_rule_keys / get_rule_context / get_script_object_api → runtime or generated

L3 — actions (see §2.5 for the gates)
```

Placement rule: used every turn (omitting it breaks a contract) → L0; used to *find*
something → L1; needed only for a specific task → L2; has an effect outside the
conversation → L3.

An L2 body is always **optional**: every registered key already has a machine-readable
description, so a missing `key-guides/` file degrades to the key's scheme profile rather
than to nothing (§9.3). That asymmetry — an absent guide is safe, a wrong guide is
authoritative — is what the per-key-guide rule rests on.

Two base prompts exist because a sub-agent must never see a tool it cannot call: the
platform would surface that as `Unknown tool: <name>`. `sub-agent-base.md` therefore
advertises exactly `subAgentToolRegistry()`, and is deliberately excluded from the T0
parity assertion (sub-agents draft per-key proposals from `get_rule_detail`, not from
the general rule-authoring block).

### 4.2 `PromptCatalog`

- Loads `/ai/catalog-manifest.txt` (one resource path per line, `#` for comments),
  then parses each file's YAML front matter into a `CatalogEntry`
  (`category, id, title, cue, key, scope`).
- The **category comes from the path** — `ai/<category>/…` — so `ai/detection/x.md`
  is category `detection` and `ai/key-guides/x.md` is `key-guides`.
- Front matter requires `id`, `title`, `cue`; key-guides add `key`; any entry may
  carry `channel` / `format` / `framework` scope. Key-guides additionally carry
  `scheme-stamp` — a field `buildEntry` deliberately ignores, read from the raw header
  by `KeyGuideSchemeStampTest` (§9.3).
- API: `list(category)`, `entry(category, id)`, `body(category, id)` (cached),
  `listFor(category, activeChannels, activeFormats, activeFrameworks)`.
- Malformed front matter, missing fields, and missing files are skipped with a
  `LOG.warn`; the catalog never throws. This is why the manifest is guarded — a stale
  entry degrades the agent silently.

### 4.3 `SystemPromptBuilder`

Loads `/ai/agent-base.md` and `/ai/sub-agent-base.md` lazily. If the sub-agent base
prompt is missing it **falls back to the main base prompt** and warns — which would
re-introduce the "Unknown tool" failure, hence the warning.

`build(entryPath, amb)` produces the opening messages listed in §2.1. The ambient
message is a single system line assembled from whatever is non-empty: project name,
edited rule file, other rule files, modules, active frameworks, enabled
channels/formats, and a non-English user language.

### 4.4 Magic instruction templates

The Magic directives live in `ai/magic/` as `{{…}}` templates, not in Kotlin:

| Template | Used for | Placeholders |
|---|---|---|
| `detection-instruction.md` | Route A / Route B stage 2, with a seeded task list | `{{name}}`, `{{manifest}}` |
| `detection-instruction-empty.md` | the no-detection short-circuit | `{{name}}` |
| `review-instruction.md` | Route B stage 1 (review the current file) | `{{name}}`, `{{content}}` |

`MagicInstructionBuilder` only loads and interpolates them; the manifest lines
(`N. id — title`) are the one part composed in code, because they are data rather
than prose. Edit the templates, never the builder. A missing or blank template throws
rather than degrading into an empty directive, and
`MagicInstructionBuilderTest.everyBodySubstitutesItsPlaceholders` fails if a
placeholder is left unsubstituted (which would ship the literal `{{name}}` to the
model).

Nothing under `ai/magic/` is mirrored into the external skill: Magic is an in-plugin
flow, so the skill has no use for these templates.

### 4.5 Asset inventory

The one place that lists what exists where. **Source class**: **A** = code/resource is
the truth (generated, never hand-written); **B** = the document is the truth;
**C** = the same fact in two places (generated + guarded, or one source + sync).

| Asset | Where | Class | Single source → artifact | Sync task | Guard |
|---|---|---|---|---|---|
| Base prompts (main, sub-agent) | `ai/agent-base.md`, `ai/sub-agent-base.md` | B | hand-written twice (the T0 block also lives in `SKILL.md`) | — | `AgentBaseSkillPreambleParityTest`, `SystemPromptBuilderTest` |
| Catalog manifest | `ai/catalog-manifest.txt` | B | — | — | `AgentBaseCatalogIdGuardTest`, `CatalogManifestParityTest` |
| Detection recipes / key guides | `ai/detection/`, `ai/key-guides/` | B | → skill `ai/` | `syncAgentCatalog` (auto) | `EasyYapiAssistantSkillTest`, `KeyGuideSharedSectionParityTest`, `KeyGuideKeyResolvesTest`, `KeyGuideSchemeStampTest` |
| Magic templates | `ai/magic/` | B | not mirrored | — | `MagicInstructionBuilderTest` |
| Knowledge base | `docs/knowledge-base/` | B | → plugin resource + skill `docs/` | `syncKnowledgeBase` (auto) | `EasyYapiAssistantSkillTest` |
| Rule-key catalog | `skills/…/rule-keys.{json,md}` | A | ← `*RuleKeys` objects + implicit keys | `syncRuleKeySchemes` (manual) | `RuleKeySchemeExporterTest`, `EasyYapiAssistantSkillTest` |
| Script-context catalog | `skills/…/rule-contexts.{json,md}` | A | ← script-object signatures | `syncRuleContexts` (manual) | `EasyYapiAssistantSkillTest` |
| Tool / locale / EP fact sheets | `skills/…/{tools,locales,extensions}.md` | A | ← `RuleTools` registries, `BundledLanguageTemplates`, `plugin.xml` | `syncSkillFacts` (manual) | `EasyYapiAssistantSkillTest` |
| Tool descriptions and schemas | `core/ai/tools/*.kt` | A | — | — | compile time |
| Prose: repo conventions, developer guides, user docs, design notes, contributor skills | `AGENTS.md`, `docs/developer/`, `README*.md`, `.spec/`, `.skills/` | B | — | — | — |

Only two kinds of information need a sync task: **A**-class catalogs, and multi-copy
mirrors of a single source. Every other gap is closed with either prose or a guard
test. Which command regenerates which row, and which of them run automatically, is in
§9.5.

Every generated artifact is committed to git: `npx skills add` distributes the
repository as-is and only publishes `skills/`, so a generated file that is not
committed does not exist for the consumer. That constraint is also why the T0 block
is hand-written twice rather than injected at build time — injection could not reduce
the number of committed files, and would turn `SKILL.md` into a half-generated file
that no longer round-trips through an idempotent copy.

### 4.6 Dedup rules

Three hard rules settle "should I copy this or link to it?"

1. **Normative information lives in `AGENTS.md` only** — threading, logging, package
   layout, testing. Everywhere else links; nothing paraphrases.
2. **Task information lives on the task page** — `docs/developer/*` explains steps and
   links to `AGENTS.md` for the rules.
3. **Generated information lives in its generated file** — rule keys, script
   contexts, aliases, locales, tools, EP registrations. Hand-written pages must not
   enumerate them; they point at the file or the tool.

The deliberate exception is content that must survive being read **in isolation**.
`get_rule_detail(key=…)` returns one guide, so the two Postman hook guides both spell
out which hook fires when — a link would move the warning away exactly when the agent
needs it. That copy is guarded by `KeyGuideSharedSectionParityTest`, which pins two
sections byte-for-byte and the remaining shared warnings as substrings that must
appear in **both** guides.

### 4.7 Consumer coverage

| Information | Built-in agent | External skill | Users / contributors |
|---|---|---|---|
| Rule-authoring contract | `agent-base.md` (L0) | `SKILL.md` (L0) | `rule-guide.md` |
| Detection recipes | `get_detection_prompt` | `get_detection_prompt.sh` + `ai/detection/` | — |
| Per-key guides | `get_rule_detail` | `get_key_guide.sh` + `ai/key-guides/` | rule-guide |
| Knowledge base | `get_plugin_doc` | `docs/` mirror | `docs/knowledge-base/` |
| Rule keys, aliases | `list_rule_keys` | `rule-keys.md` / `.json` | `rule-keys.md` |
| Script contexts and object APIs | `get_rule_context`, `get_script_object_api` | `rule-contexts.md` | `rule-contexts.md` |
| Tool inventory | the registry itself | `tools.md` | `tools.md` |
| Bundled locales | `BundledLanguageTemplates` | `locales.md` | `locales.md` |
| Extension points | `get_plugin_doc` (partial) | `extensions.md` | `docs/developer/`, `extensions.md` |
| Project rule files | `read_rule_file` | `read_rule_file.sh` | the files themselves |
| Plugin settings | `get_plugin_doc("settings-guide")` | bundled page | the same page |
| Build and sync tasks | — | — | `CONTRIBUTING.md` |

---

## 5. LLM backend and settings

### 5.1 Components

| Component | Responsibility |
|---|---|
| `AIService` | The only abstraction: `chat(AiChatRequest): AiChatResponse`, `testConnection()` |
| `AiModels` | The DTOs on that boundary: `AiChatRequest`, `AiMessage`, `AiToolSpec`, `AiToolCall`, `AiChatResponse`. No LangChain4j type appears here |
| `AIServiceFactory` | The only place provider-specific LangChain4j classes appear. Anthropic / Gemini / Ollama / Azure OpenAI use their own models; everything else goes through `OpenAiChatModel` with a provider-specific base URL |
| `LangChain4jAIService` | DTO ↔ LangChain4j adaptation. `withTimeout` must wrap `withContext(Dispatchers.IO)` — the blocking SDK call has no suspension point, so the timeout would never fire otherwise |
| `AiProvider` | The provider catalog: each entry carries `displayName`, `defaultBaseUrl`, `defaultModel`, `requiresApiKey`, `openAiCompatible`, `contextWindow` |
| `AiRuntimeConfig` | `provider`, `baseUrl`, `apiKey`, `model`, `requestTimeoutSec`, `maxRequests`, `contextWindow`, `loopSafety`; `load(project)` falls back per field to the provider defaults and returns `null` when a key is required but missing |
| `AiApiKeyStore` | API key in IntelliJ's `PasswordSafe`. The service name deliberately matches the legacy `PasswordStorage` derivation — do not switch to `generateServiceName` |
| `MessageTokens` | `contextWindowToBudget = clamp((window − 4000) × 0.4, 2000, 64000)`; `trimToTokenBudget` keeps the leading system block, the last user message, and the last tool-call/tool-result group intact |
| `TokenSizeUtils` | Parses context-window presets (`8k` … `2m`; decimal multipliers) |
| `ChatTimeoutException` | A **transient** `RuntimeException` wrapping `TimeoutCancellationException`, so a timeout is retryable while cooperative cancellation still propagates |

### 5.2 Where settings live

`AiRuntimeConfig` is the resolved, per-call view. The persisted source is
`core/settings/module/AiSettings.kt`, at **application scope** — one setting for the
whole IDE, not per project:

- Fields are `@StorageScope(Scope.APPLICATION)`; `DefaultSettingBinder` routes them to
  `UnifiedAppSettingsState`, so there is no per-field persistence wiring. Read them via
  `project.settings<AiSettings>()`.
- The AI tab in `SettingsPanels` reads and applies them; each panel is a
  `resetFrom` / `apply` / `isModified` triple, so a field means three touch points.
- `AiRuntimeConfig.load(project)` turns settings into config and applies the per-field
  fallbacks described in §5.1.
- Changing AI settings **rebuilds the session** (`AiAssistantService`), so settings
  changes take effect as a new conversation.

To add a setting, see §9.6.

### 5.3 Credential discovery

`core/ai/credentials/` powers the settings page's "detect credentials" action. It is
never on the agent's path, which is why the `Ambient` privacy rule in §2.4 and this
scanner can hold different views of the environment.

- `CredentialScanner.scan(): DetectionResult` — sealed, so callers must handle every
  case: `Miss`, a single `Hit(provider, sourceLabel, apiKey?, baseUrl?, model?)`, or
  `MultipleFound(primary, others)`. `sourceLabel` never contains the key itself.
- `DefaultCredentialScanner` tries, in order: known environment variables, then CLI
  credential files, then localhost probes. The exact names and paths live in that
  class — they are the list.
- Two injectable seams keep it testable and safe: `CredentialFileSystem` (bounded to
  8 KB, never throws — `null` means "miss") and `LocalhostHttpProbe` (accepts loopback
  hosts only — `localhost` / `127.0.0.1` / `::1`; a non-loopback URL returns `false`
  without a network call).

---

## 6. Chat UI

`AiChatPanel(project, editingFilePath?)` renders every `AgentEvent` and drives the
turn. It runs the turn on `Dispatchers.Default` and touches the UI on
`IdeDispatchers.SwingAny` — a turn must not run on the EDT, or the modal dialogs it
can open would deadlock it.

| Event | Rendered as |
|---|---|
| `Thinking` / `Perceiving` / `Acting` / `Observed` / `Retrying` | Status line + a tool-activity card |
| redundant `Observed` for `create_task_list` / `update_task` | suppressed (the task list is already visible) |
| `ApprovalRequested` / `ClarificationRequested` / `FileReadConsentRequested` | the matching card, completing the gate |
| `Message` / `ProposalReady` | message row / proposal card |
| `Failed` | status line + an error notification |
| `LoopDetected` | recorded for the recovery dialog |
| `Task*` | drives the Todo List panel |
| `TurnComplete` | arms the ReviewGate card when Route B stage 1 just finished |

**Saving a proposal:** the proposal card's "Save…" opens a dialog choosing `Global
(~/.easyapi/)` or `Project (<project>/.easyapi/)`, writes the staged content through
`RuleFileTextIo` (UTF-8 with a BOM, so the file opens as UTF-8 in the IDE instead of
being detected as ISO-8859-1), reloads `ConfigReader`, and opens the file. Older cards
are frozen as `(applied)` / `(outdated)` when a newer proposal arrives.

Turn outcomes are surfaced too: `StepLimitHit` offers Continue/Cancel (Continue
re-runs with `"(continue)"`), and `LoopDetected` offers a retry with an
anti-repetition hint injected.

---

## 7. External skill

```
skills/easy-yapi-assistant/
├── SKILL.md                   # entry point: when to use it, the T0 block (byte-identical
│                              # to agent-base.md), and per-tool emulation guidance
├── docs/                      # knowledge-base mirror                (syncKnowledgeBase)
├── ai/detection|key-guides/   # agent-catalog mirror                 (syncAgentCatalog)
├── rule-keys.{json,md}        # generated rule-key catalog           (syncRuleKeySchemes)
├── rule-contexts.{json,md}    # generated script-context catalog     (syncRuleContexts)
├── tools.md  locales.md  extensions.md   # generated fact sheets      (syncSkillFacts)
└── scripts/*.sh               # CLI mirrors (mapping in tools.md)
```

Install with `npx skills add tangcent/easy-yapi -g -y`. The skill carries the
mechanical facts from generated files and the *emulation* guidance from `SKILL.md`
(how to achieve each tool's job with the assistant's own file and search tools) —
that guidance is hand-written because it is not derivable from code.

---

## 8. Known limitations

Each item points at the section that explains it; nothing here is explained twice.

- **Extension `id` / `enabledByDefault` are not exportable at build time.** They are
  instance properties, and the registries that resolve them (`ChannelRegistry`,
  `FieldFormatChannelRegistry`, `FrameworkRegistry`) are project-scoped services
  reading a project-area extension point. `extensions.md` therefore lists the EP
  declarations and implementation classes; read the defaults off the implementation
  class.
- **`catalog-manifest.txt` is hand-maintained** (§4.2). Adding a detection or key guide
  means adding a line. `CatalogManifestParityTest` catches drift in both directions,
  but it cannot add the line for you.
- **The approval gate is dormant** (§2.5, §3.3). Only `write_rule_file` requires
  approval, and it is not registered — so nothing in the current tool set triggers it.
- **CLI mirrors cover only part of the tool set** (§3.4). The rest need IntelliJ or are
  emulated by the external assistant's own tooling.
- **Loop-safety and retry tuning is not user-configurable** (§2.6), and neither is the
  credential-scan source list (§5.3) — both are code. The defaults in
  `LoopSafetyConfig` are what production uses.

---

## 9. Maintenance playbooks

### 9.1 Adding a tool

1. Implement `AiTool` under `core/ai/tools/`; wrap PSI reads in `read { }`.
2. Register it in the right factory in `RuleTools.kt` — `standardRuleTools()`,
   `orchestratorToolRegistry()`, or `subAgentToolRegistry()`. Watch the name: it must
   not collide with another tool in the same registry (§3.2).
3. If it is a sub-agent tool, **also** update the tool index in
   `ai/sub-agent-base.md`; `SystemPromptBuilderTest` fails when the advertised set
   and the registry disagree.
4. Add tests, then run `syncSkillFacts` (§9.5) so `tools.md` picks it up.

### 9.2 Adding a detection recipe

1. Add `ai/detection/<id>.md` with `id`, `title`, and `cue` front matter (plus scope
   if it is channel/format/framework specific).
2. Add its path to `ai/catalog-manifest.txt` — the two must match exactly.
3. Run `syncAgentCatalog` (§9.5).

### 9.3 Adding a per-key guide

As 9.2 with `ai/key-guides/<key>.md`. The file name, the `id`, and the `key` field are
the same string, and it must be a **registered** key name — implicit keys under
`ImplicitConfigKeys` count. `KeyGuideKeyResolvesTest` fails on a guide that the catalog
cannot parse, whose id is not a registered key, whose `key` field disagrees with its
id, or whose id another guide already claims. Add the `scheme-stamp` field as well;
`KeyGuideSchemeStampTest` prints the value to paste (below).

**No guide is the default, and an absent guide costs nothing.** `get_rule_detail(key=…)`
tries the catalog first and then falls back to the key's scheme profile, so every
registered key is already described — aliases, bindings, availability, output shape,
context kinds, and pointers to `get_script_object_api`. A guide has to earn its place
against that baseline: one that restates it is read *instead of* the rules the model
actually needs.

**A guide is the last resort, and the scheme grows to keep it that way.** Before writing one,
walk this order:

- **A property of the key?** → extend `RuleKeyScheme`, do not write prose. This is the
  established reflex, not a hypothetical: `jsonValue`, `dryRunnable`, `staticConfiguration`,
  `additionalBindings` and `outputShape` were each added because a guide had become the only
  home for them. A property is *small, closed and structured* — a flag, an enum, a shape, a
  binding list, a short member list.
- **Shared across keys, or a standing policy?** → hoist to `rule-guide.md` or
  `agent-base.md`. The "never emit a literal credential" paragraph is copied verbatim into
  eight files today; that is duplication by policy, not by necessity.
- **Everything left over** → guide content, and it must still clear the four conditions below.

The uncomfortable middle is condition 2's payload schema: it *is* a key property, but the
scheme has no field for it, so three keys (`json.additional.field`,
`method.additional.header`, `method.additional.response.header`) carry near-identical guides
for that reason alone. **A fourth such key is the tripwire — add the field, do not add a
fourth copy.**

A guide earns its place only if it carries at least one of these four, none of which any
machine-readable source can hold:

1. **Engine behaviour** — what happens when the rule *runs*: retries and replay, hook
   depth, ordering, what falls back to what. The scheme states the contract; it cannot
   state the engine (e.g. `http.call.after`'s bounded retry).
2. **Key-specific payload schema** — the members a value must carry, or merge-vs-replace
   semantics, where "must be a JSON object" is not enough to write a correct value
   (e.g. `json.additional.field`, `method.additional.header`). Neither the fallback
   profile nor `list_rule_keys` prints the key's `mode` or its JSON-object flag, so
   "one object per line, MERGE" is not otherwise reachable.
3. **A verified trap** — something a competent author gets wrong, **confirmed in the
   code**. State the verification, not the folklore: an unverified trap is how a guide
   becomes wrong and stays wrong. This is not hypothetical — `json.rule.convert` shipped
   two "traps" that the code disproved.
4. **A cross-key relationship or decision procedure** — which sibling fires when, or
   when *not* to propose the rule at all (e.g. `postman.test` vs `postman.prerequest`;
   `markdown.template`'s locale workflow).

Everything else already has a home — link it, never copy it:

| Never put in a guide | Its home |
|---|---|
| `groovy:` / `#regex:` / `@` / `$class:` value syntax | the L0 rule-authoring block (§4.1) |
| aliases, bindings, availability, output shape, context kinds | the scheme profile — the fallback already returns it |
| script-object method signatures | `get_script_object_api` |
| what a key decides / where it applies, in one line | `list_rule_keys` |
| a property the scheme has no field for | extend `RuleKeyScheme` — see the order above |
| a credential / security policy other keys also need | `rule-guide.md` or `agent-base.md`; never copy it per key |
| a recipe that is already a row in `rule-guide.md` | `rule-guide.md` |
| how to run an export, or how to configure a setting | `docs/knowledge-base/` via `get_plugin_doc` |

One duplication is sanctioned (§4.6): a warning whose absence makes the model write a
**wrong** rule may be copied into every guide that needs it. Pin it with a parity test
when two siblings share it, as `KeyGuideSharedSectionParityTest` does for the Postman
pair.

**A guide is pinned to the scheme it was reviewed against.** Its front matter carries
`scheme-stamp`, a digest of its key's live `SchemeEntry` — the same entry `list_rule_keys`
prints and `rule-keys.json` publishes (name, source, summary, context kinds, output shape,
dry-runnable, static-configuration, aliases, type, mode, additional bindings, `jsonValue`,
notes). `KeyGuideSchemeStampTest` fails when the stamp stops matching, so **a key cannot
change without the guide that documents it being re-read**. That guard is the only
mechanical link between the two sides, and it has to be mechanical: nothing else will ever
notice a stale guide, because no test can read prose for correctness.

The remedy is one line, but read the guide first — the stamp fires in both directions:

- **the scheme changed** → the prose may now be wrong;
- **the scheme gained a field** → the guide may now be **redundant**. This is the tripwire
  above firing late: delete what the scheme now carries instead of keeping the guide.

Editing a guide's prose never moves its stamp — the stamp tracks the *scheme*, not the
guide — so refreshing it is a claim that somebody looked at both.

A numeric limit quoted in prose — a retry cap, a size bound — is still a two-place fact
(§4.5 class C): name the constant it mirrors, so the next person to change the code can
grep their way to the guide. The stamp does not cover this case; it pins the scheme, and
prose may cite a constant that has no scheme field behind it.

The `cue` is the one part of a guide that stays in context on every turn (§4.1): write it
as the trigger for fetching that guide, not as a restatement of its title.

### 9.4 Editing prompts

- **`agent-base.md`** — the T0 block is duplicated in `SKILL.md`; update both or
  `AgentBaseSkillPreambleParityTest` fails.
- **`sub-agent-base.md`** — the tool index must match `subAgentToolRegistry()` exactly.
- **`ai/magic/*.md`** — edit the templates, not `MagicInstructionBuilder`; only
  `{{name}}`, `{{manifest}}`, and `{{content}}` exist as placeholders. No sync needed.
- **Detection recipes and key guides** — edit the source under
  `src/main/resources/ai/` and run `syncAgentCatalog` (§9.5).
- A catalog id quoted in `agent-base.md` or in a tool description must resolve through
  `PromptCatalog.entry(…)`; `AgentBaseCatalogIdGuardTest` enforces it.

### 9.5 Syncing generated artifacts

`./gradlew syncSkill` runs all five and is the normal entry point; commit whatever it
changes. Two of them also run in `processResources`, so a build cannot ship a stale
mirror:

| Task | Regenerates | When it runs |
|---|---|---|
| `syncKnowledgeBase` | plugin resource + skill `docs/` mirror | `processResources` **and** `syncSkill` |
| `syncAgentCatalog` | skill `ai/` mirror | `processResources` **and** `syncSkill` |
| `syncRuleKeySchemes` | `rule-keys.{json,md}` | `syncSkill` only |
| `syncRuleContexts` | `rule-contexts.{json,md}` | `syncSkill` only |
| `syncSkillFacts` | `tools.md`, `locales.md`, `extensions.md` | `syncSkill` only |

What to run after changing what: a `*RuleKeys` object or an implicit key →
`syncRuleKeySchemes`, **and** refresh the `scheme-stamp` of every guide whose key you
touched (`KeyGuideSchemeStampTest` fails until you do — see §9.3); a script-object
signature → `syncRuleContexts`; a tool registry, `BundledLanguageTemplates`, or
`plugin.xml` → `syncSkillFacts`; `ai/{detection,key-guides}` or `docs/knowledge-base` →
nothing manual, they refresh in `processResources` (run `syncSkill` if you want the
working copy updated now).

The guard tests fail on a stale copy, but only CI will notice if you skip the manual
three. `CONTRIBUTING.md` carries the command list for contributors.

### 9.6 Adding an AI setting

1. Add the field to `AiSettings` with `@StorageScope(Scope.APPLICATION)`.
   `DefaultSettingBinder` routes it to `UnifiedAppSettingsState` by reflection, so
   there is no per-field persistence wiring to write.
2. Read it in `AiRuntimeConfig.load(project)`, with a per-field fallback to the
   provider default when it is blank or zero.
3. Surface it in the AI tab in `SettingsPanels` — that panel logic is a
   `resetFrom` / `apply` / `isModified` triple, so one field means three touch points.
4. Only if the setting takes over a **legacy persisted key**, add it to
   `SettingsMigrationActivity`.

### 9.7 Adding a locale or an extension point

- **Locale** — add `<locale>.md.tpl` under `src/main/resources/markdown/templates/`
  plus one `BundledLanguageTemplates.LOCALE_TO_RESOURCE` entry, then run
  `syncSkillFacts` (§9.5).
- **Extension point** — add the `<extensionPoints>` declaration *and* the
  implementation entry in `plugin.xml`, then run `syncSkillFacts`. A new
  channel/framework that ships rule keys must also be registered in
  `RuleKeyCatalog.SOURCES` — see
  [`docs/developer/README.md`](README.md#registering-rule-keys).

### 9.8 Deciding where information belongs

```
Needed by the agent on every turn (a contract)?  → L0  ai/agent-base.md or sub-agent-base.md
Only "where do I look"?                          → L1  an index (title lines, index.md)
Derivable from code?                             → generate it (+ a sync task)
Only a specific task needs the body?             → L2  knowledge-base / detection / key-guides
Has an effect outside the conversation?          → L3  a tool (and document the gate)
A rule every contributor must follow?            → AGENTS.md (link it, never restate it)
```

### 9.9 When something looks wrong

| Symptom | Look at |
|---|---|
| The model calls a tool and gets `Unknown tool: <name>` | §2.1 / §4.1 — a base prompt advertises a tool its registry does not have |
| `run_sub_agent` rejects every task id | §2.7 — the rendered manifest never reached the turn's user message |
| A Magic run stages no proposal | §2.8 — the merge produced no rule line (nothing detected, or nothing drafted) |
| The turn ends with `LoopDetected` | §2.6 — read the reason: an identical call repeated, or results stopped changing |
| A tool returned a large catalog but the transcript looks empty | §2.4 / §3.1 — a `Stateful` result is merged into the knowledge state and only a receipt stays in the transcript |
| The conversation resets by itself | §5.2 — changing AI settings rebuilds the session, by design |
