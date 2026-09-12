# EasyApi AI Tools — inventory

Auto-generated from the tool registries in `core/ai/tools/RuleTools.kt` (`SkillFactsExporter`). Do **not** edit by hand — run `./gradlew syncSkillFacts`.

**Kind** — `PERCEPTION` is read-only and runs automatically; `ACTION` changes state. **Approval** — whether the call goes through the user approval gate.

## Registries

| Registry | Entry path | Tools |
|----------|------------|-------|
| `standardRuleTools()` | `REACTIVE` | 19 |
| `orchestratorToolRegistry(...)` | `TASK_LIST_PROGRAMMATIC` | 3 |
| `subAgentToolRegistry()` | `SUB_AGENT` | 9 |

## Tools (22)

### `ask_clarification`

- **Kind:** PERCEPTION · **Timeout:** 300s · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.AskClarificationTool`
- Ask the user one or more clarifying questions when the request is ambiguous. Provide concrete options for single_choice/multi_choice questions so the user can answer with a click. Returns the user's answers. This tool is NOT terminal — continue once you have them.
- **CLI mirror:** —

### `create_task_list`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.CreateTaskListTool`
- Commit to a concrete task list before working a complex (Magic) task. Pass an ordered list of tasks, each with a short stable id (e.g. "s1", "s2"), a one-line title, and an optional 1-2 sentence detail. All tasks start PENDING; use update_task to mark them in_progress / completed / failed / skipped as you work. Use this ONLY for Magic tasks with ≥2 distinct steps; plain chat does not need a task list.
- **CLI mirror:** —

### `find_classes_by_annotation`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationTool`
- Find project classes annotated with the given annotation(s) — each by simple or fully qualified name (e.g. "RestController"). Pass `annotation` for one or `annotations` (array) for batch. Returns FQNs of matching classes (JSON array for one; name-to-array map for batch). A simple name probes every matching annotation class.
- **CLI mirror:** —

### `find_classes_by_name`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.FindClassesByNameTool`
- Resolve class simple names to their fully qualified names. Pass `name` (string) for one, or `names` (array) to batch-resolve multiple. Returns a JSON array of FQNs for a single name, or a JSON object mapping each name to its array for batch. Use this when you only know a class's simple name (e.g. "AuthResponse") and need the FQN to pass to get_psi_class_info. If the input contains a dot it is treated as an FQN and looked up directly. Optional `context` (file path or class FQN) prefers matches reachable from that file's imports.
- **CLI mirror:** —

### `find_classes_by_supertype`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.FindClassesBySupertypeTool`
- Find project classes that extend or implement the given supertype(s) (class or interface) — each by simple or fully qualified name (e.g. "OncePerRequestFilter"). Pass `supertype` for one or `supertypes` (array) for batch. Returns FQNs of inheritors, excluding the supertype itself (JSON array for one; name-to-array map for batch). Use for inheritance-declared components: servlet filters extending OncePerRequestFilter, interceptors implementing HandlerInterceptor, argument resolvers implementing HandlerMethodArgumentResolver.
- **CLI mirror:** —

### `get_detection_prompt`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetDetectionPromptTool`
- Fetch the full detection recipe for one detection family by id (e.g. "static-auth", "auth-token-chaining", "spring-filters-interceptors"). Returns the recipe as Markdown. Use this when the user's request touches a detection family before proposing rules for it.
- **CLI mirror:** `scripts/get_detection_prompt.sh`

### `get_existing_rules_for_key`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetExistingRulesForKeyTool`
- Get all configured values for one or more rule keys, with their source and priority. Pass `key` (string) for a single key or `keys` (array of strings) to batch-check multiple keys. Returns a JSON array {sourceId, priority, value} for a single key, or a JSON object mapping each key to its array for batch mode.
- **CLI mirror:** `scripts/get_existing_rules_for_key.sh`

### `get_module_dependency_graph`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetModuleDependencyGraphTool`
- Return the workspace module dependency graph (module-name -> depends-on module-names). Call only when the ambient `modules:` hint shows more than one API-bearing module, to decide which modules form one app (connected components). Above ~24 nodes a summary ('N modules in K clusters') is returned instead.
- **CLI mirror:** —

### `get_plugin_doc`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetPluginDocTool`
- Read a plugin documentation page from the EasyApi knowledge base. Parameter `name` is one of overview \| index \| rule-guide \| settings-guide \| usage-guide \| postman-script-reference. postman-script-reference documents the Postman-compatible pm.* Groovy API — use it only when authoring postman.* scripts. Returns the doc text (Markdown).
- **CLI mirror:** —

### `get_psi_class_info`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetPsiClassInfoTool`
- Get info about Java/Kotlin class(es) by simple or fully qualified name (e.g. "AuthResponse"). Pass `className` for one, or `classNames` (array) for batch. Returns JSON {name, fqn, modifiers, annotations, fields, methods}. An ambiguous simple name errors with the candidate FQNs — pass `context` (file path / class FQN) or retry with a listed FQN.
- **CLI mirror:** —

### `get_psi_method_info`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetPsiMethodInfoTool`
- Get info about a method in a class. Class name — simple or fully qualified — in `className`; method name in `methodName` (optional `paramCount` narrows overloads). Returns JSON {className, name, signature, annotations, parameters, docComment, returnType, returnTypeFqn}. detail="full" includes the method `body` (truncated to `maxBodyChars`).
- **CLI mirror:** —

### `get_rule_context`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetRuleContextTool`
- Return structured script context for one EasyAPI rule key: execution mode, bindings, and object references. Common/shared objects are only referenced by id (deduplicated); fetch their full method signatures via get_script_object_api([ids]). Use before authoring Groovy or Postman scripts. Aliases are accepted but resolved to the canonical key — always write the canonical key name in the rule file. Pass expand=true to get the full inline profile (debugging only).
- **CLI mirror:** `scripts/get_key_context.sh`

### `get_rule_detail`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetRuleDetailTool`
- Fetch the full detail for one rule key (by `key`) or every key matching a scope (by `channel` / `format` / `framework`). At least one argument is required. `key` takes precedence over scope args. Returns Markdown; a key with no guide file returns its self-describing scheme profile.
- **CLI mirror:** `scripts/get_key_guide.sh`

### `get_script_object_api`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.GetScriptObjectApiTool`
- Fetch the full method signatures for one or more shared script objects by id (e.g. "logger", "session", "request", "response", "class", "method"). Returns a JSON object mapping id -> {type, description, methods}. Call get_rule_context(key=...) first to discover which objects are referenced.
- **CLI mirror:** —

### `list_project_endpoints`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.ListProjectEndpointsTool`
- List HTTP API endpoints cached for the project. Returns a JSON array of {className, name, httpMethod, path}. v1 returns HTTP only (gRPC filtered). Returns "cache not ready" if the initial scan hasn't completed; retry on the next turn.
- **CLI mirror:** —

### `list_rule_keys`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool`
- List all known EasyAPI rule keys (canonical names only — aliases are compatibility-only and must not be written into rule files). Entries: name, source, summary, outputShape, contexts. Call get_rule_context(key=…) for the complete bindings and script object references, and get_rule_detail(key=…) for the full guide when one exists.
- **CLI mirror:** —

### `propose_rule_content`

- **Kind:** ACTION · **Approval:** not required · **Timeout:** 30s (default) · **Registries:** `orchestrator`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.OrchestratorProposeRuleContentTool`
- Stage the merged rule content from all sub-agent findings. Call this ONCE after every task is closed (completed or failed). The tool automatically merges the collected sub-agent findings and their drafted rules — you do NOT need to compose the merged content yourself. Pass a suggestedFileName for the proposed rule file. If no sub-agent findings were collected (all failed or none spawned), pass a fallback content as well. If the merged content has no rules (no pattern detected, or nothing drafted), the tool stages no proposal and the turn ends without prompting the user.
- **CLI mirror:** —

### `propose_rule_content`

- **Kind:** ACTION · **Approval:** not required · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.ProposeRuleContentTool`
- Stage a proposed rule file. The agent calls this when it has produced final rule content for the user. Does NOT write to disk — only stages the proposal. The chat UI surfaces a 'Save…' button for the user to confirm. Content is validated against the rule key catalog before staging; invalid proposals are rejected with specific errors so you can correct and retry.
- **CLI mirror:** —

### `read_rule_file`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.ReadRuleFileTool`
- Read the UTF-8 contents of a RULE FILE. Pass a bare filename (e.g. "security.properties") or a scope-prefixed name ("global:jwt.rules" / "project:custom.rules"); the tool resolves it against the tracked.easyapi/ rule folders. An absolute path inside a tracked folder is also accepted; a relative path resolves against the project root. You do NOT know the user's home directory — never hard-code "/Users/<name>" or a literal "~"; address files by name. An out-of-scope path asks the user for one-time consent. NOT for source code — to inspect a Java/Kotlin class, use get_psi_class_info with its FQN instead.
- **CLI mirror:** `scripts/read_rule_file.sh`

### `report_findings`

- **Kind:** ACTION · **Approval:** not required · **Timeout:** 30s (default) · **Registries:** `sub-agent`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.ReportFindingsTool`
- Report the detection findings for this sub-agent's task and end the sub-agent turn. Pass `detected=true` if the pattern was found in the project's PSI (with evidence in `findings` and any concrete rule proposals in `proposedRules`), or `detected=false` if the search came up empty. Sub-agent-only — never call from the orchestrator or Reactive chat.
- **CLI mirror:** —

### `run_sub_agent`

- **Kind:** ACTION · **Approval:** not required · **Timeout:** no timeout · **Registries:** `orchestrator`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.RunSubAgentTool`
- Spawn a sub-agent to run one detection task in isolation, and wait for its findings. Pass the task id from the seeded task list. The sub-agent perceives the project's PSI, decides whether the pattern is present, and reports back via report_findings. This tool automatically records the task status (completed if the sub-agent ran successfully — whether or not it detected the pattern; failed on error) and ticks the checklist card in the UI, so you do NOT need to call update_task afterwards for the status. Orchestrator-only — never call from a sub-agent or Reactive chat.
- **CLI mirror:** —

### `update_task`

- **Kind:** PERCEPTION · **Timeout:** 30s (default) · **Registries:** `standard`, `orchestrator`
- **Impl:** `com.itangcent.easyapi.core.ai.tools.UpdateTaskTool`
- Update the status of one task in the active task list. Pass the task id (as given to create_task_list), the new status (one of "in_progress", "completed", "failed", "skipped"), and an optional reason (only used for "failed"). Marks the task and ticks the checklist card in the UI. Use this ONLY for Magic tasks with an active task list.
- **CLI mirror:** —

## CLI mirrors

| Script | Mirrors |
|--------|---------|
| `scripts/get_detection_prompt.sh` | `get_detection_prompt` |
| `scripts/get_existing_rules_for_key.sh` | `get_existing_rules_for_key` |
| `scripts/get_key_context.sh` | `get_rule_context` |
| `scripts/get_key_guide.sh` | `get_rule_detail` |
| `scripts/read_rule_file.sh` | `read_rule_file` |

Scripts without a tool counterpart (they mirror a prompt layer, not a tool): `list_detections.sh`, `list_rule_files.sh`.

