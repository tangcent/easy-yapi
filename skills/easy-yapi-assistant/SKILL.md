---
name: "easy-yapi-assistant"
description: "Help author EasyYapi rule files (.easyapi/ folder). Invoke when the user is editing an EasyYapi rule file or asks to add/modify EasyYapi rules (annotations, field rename, custom headers, etc.)."
---

# EasyYapi Rule Authoring Assistant

This skill is the **external mirror of EasyYapi's built-in rule-authoring
agent**. It helps you write or modify EasyYapi rule files (`.rules` /
`.properties` files in `.easyapi/`) that the EasyYapi IntelliJ plugin reads to
customise API export (YApi/Postman/Markdown), field naming, annotations, and
more.

It runs the **same perceive → reason → act loop** as the built-in agent and
exposes the **same capability surface**: the same rule guide and key catalog,
and CLI equivalents of every built-in perception tool. The only difference is
*how* each capability is delivered — the built-in agent calls IntelliJ PSI
tools; you read files and search the codebase directly.

## When to Use

Invoke this skill when:
- The user opens or edits any file in the project's `.easyapi/` folder or
  the global `~/.easyapi/` folder.
- The user asks to "add a rule", "rename a field", "ignore a class", "add a
  header to every POST", etc. in the EasyYapi context.
- The user mentions `easy.api`, `EasyApi`, `EasyYapi`, or `easyapi` in a request about
  config or rule authoring.

## Bundled Knowledge Base (read these first — they ARE the built-in agent's docs)

This skill ships the **same knowledge-base pages** the plugin bundles for its
built-in agent's `get_plugin_doc` tool, copied verbatim into the bundled
`docs/` folder next to `SKILL.md`. They are kept in sync by the
`syncKnowledgeBase` Gradle task in the easy-yapi repo, so the rule content the
built-in and external agents produce is identical. Always read the relevant
page first — do **not** rely on memory or guess syntax.

| Bundled file | Built-in `get_plugin_doc` name | What it covers |
|--------------|--------------------------------|----------------|
| `docs/rule-guide.md` | `rule-guide` | **The source of truth.** Rule file format, filter syntax, expression prefixes, Groovy binding reference, recipes, the Custom-Pattern Catalog, and the Workflow-Pattern Catalog (cross-endpoint auth/signing/refresh recipes). |
| `docs/index.md` | `index` | Knowledge-base index / topic map. |
| `docs/README.md` | `overview` | Overview of EasyYapi concepts. |
| `docs/settings-guide.md` | `settings-guide` | Plugin settings reference. |
| `docs/usage-guide.md` | `usage-guide` | Usage guidance. |
| `docs/easyapi-script-reference.md` | `easyapi-script-reference` | Scripting reference. |
| `docs/rule-keys.md` | *(built-in `list_rule_keys` tool)* | Complete rule-key catalog (snapshot of `RuleKeys.kt`). |

## Toolset — CLI mirrors of the built-in agent tools

The built-in agent has a fixed set of perception/action tools. You provide
equivalent capability by reading files and searching the codebase. Use the
mapping below so your workflow tracks the built-in agent's.

### EasyYapi-domain tools (bundled as `scripts/`)

These mirror the EasyYapi-specific perception tools. Run them from the project
root (the CWD when the assistant is invoked). They auto-detect the project
root and `~/.easyapi/` so you never hard-code paths.

| Built-in tool | Your equivalent | What it does |
|---------------|-----------------|--------------|
| `list_rule_keys` | Read bundled `docs/rule-keys.md` | Lists every supported rule key (≡ `RuleKeys.kt` snapshot). |
| `get_plugin_doc` | Read the bundled `*.md` pages above | Reads a knowledge-base page. |
| `read_rule_file` | `scripts/read_rule_file.sh <name>` | Reads a rule file by name. Supports `global:` / `project:` scope prefixes. Resolves against tracked `.easyapi/` dirs — never guesses home paths. |
| `get_existing_rules_for_key` | `scripts/get_existing_rules_for_key.sh <key> [<key>...]` | Finds all configured values for a key across project + global rule files. Prints `file:line: <line content>` so you can reason about precedence. |

**Usage examples:**
```bash
# List all rule files the plugin will load
scripts/list_rule_files.sh

# Read a rule file by name (scope-prefixed or bare)
scripts/read_rule_file.sh security.properties
scripts/read_rule_file.sh global:jwt.rules
scripts/read_rule_file.sh project:custom.rules

# Check if a key is already configured (avoid duplicates — Quality Rule 1)
scripts/get_existing_rules_for_key.sh field.name
scripts/get_existing_rules_for_key.sh api.tag method.additional.header
```

### General codebase-perception tools (your file/grep capabilities)

The built-in agent has PSI tools to inspect source. You use file reads +
`rg`/`grep` instead. `rg` is assumed available (it ships with most AI coding
assistants); fall back to `grep -rn` if it isn't.

| Built-in tool | Your equivalent | Notes |
|---------------|-----------------|-------|
| `get_psi_class_info` | Read the class source file; or `rg` for its fields/methods | Find the file by simple name first (below), then read it. Resolve the FQN from the package + import. |
| `get_psi_method_info` | Read the method in the class source file | For overloads, disambiguate by parameter count when you read it. |
| `find_classes_by_annotation` | `rg -t java -t kt "<@AnnotationFqn or @Simple>" ` then resolve imports | Always confirm the FQN from the import / package — `@Simple` names collide. |
| `find_classes_by_supertype` | `rg -t java -t kt "extends\\s+<Type>\\|implements\\s+.*<Type>"` then resolve imports | **The most common blind spot** — annotation-only scans miss inheritance-declared components (filters extending `OncePerRequestFilter`). Use BOTH this and the annotation scan. |
| `list_project_endpoints` | *(no CLI equivalent — see below)* | Needs IntelliJ's `ApiIndex` cache, which is unavailable outside the IDE. |

**Standard discovery patterns** (the Custom-Pattern Catalog signals to look
for — full recipes are in bundled `docs/rule-guide.md`):
```bash
# Find servlet filters (extends OncePerRequestFilter, implements Filter)
rg -t java -t kt "extends\s+OncePerRequestFilter|implements\s+.*Filter"

# Find interceptors
rg -t java -t kt "implements\s+HandlerInterceptor"

# Find response wrappers (ResponseBodyAdvice)
rg -t java -t kt "implements\s+ResponseBodyAdvice"

# Find argument resolvers
rg -t java -t kt "implements\s+HandlerMethodArgumentResolver"

# Find annotated controllers (resolve imports to confirm the FQN)
rg -t java -t kt "@RestController"
```

Always resolve imports / same-package usage to confirm the FQN, and exclude
the supertype itself from results — these are the same nuances the built-in
agent's PSI tools handle automatically.

**`list_project_endpoints` has no CLI equivalent** — it needs the plugin's
`ApiIndex` cache, which only exists inside a running IntelliJ. You do not
need the endpoint list to author rules: rules are about the request/response
*contract* (headers, param injection, response unwrapping), which you detect
from source via the discovery patterns above. If the user references a
specific endpoint, read the controller method's source directly.

### Batch mode (mirror the built-in agent's batching)

The built-in agent's `find_classes_by_*`, `get_psi_class_info`, and
`get_existing_rules_for_key` accept arrays to probe multiple items in one
request. Mirror this by batching your searches — e.g. one
`rg -t java -t kt "extends\s+OncePerRequestFilter|implements\s+.*Filter|implements\s+HandlerInterceptor"`
covers filters + interceptors in one pass instead of three, and
`get_existing_rules_for_key.sh field.name field.ignore api.tag` checks three
keys at once. Prefer the combined form.

## Workflow

Work in a **perceive → reason → act** loop, mirroring the built-in agent.

### Step 1: Perceive — read the authoritative guide

Read the bundled `docs/rule-guide.md` first. It is the
source of truth for the rule file format, the full rule-key catalog, filter
syntax, expression prefixes, recipes, and the Custom-Pattern Catalog. If the
topic is settings/usage/scripting rather than rules, read the corresponding
bundled page instead.

### Step 2: Perceive — find the right rule key

If the rule key isn't obvious from the guide, scan the bundled `docs/rule-keys.md`
catalog. **Never invent keys not in that catalog** — unknown keys are silently
ignored by the plugin's config loader.

### Step 3: Perceive — inspect existing rules

Before proposing changes, read any existing rule files in:
- `<project>/.easyapi/` (project-scoped rules — the 3.0 model).
- `~/.easyapi/` (global rules — applied to every project on the machine).
- Legacy `.easy.api.config*` files in the project root (and parent
  directories — EasyYapi walks up the tree for backwards compatibility).

**Use the bundled scripts** to inspect rules without guessing paths:

```bash
# List all rule files the plugin will load
scripts/list_rule_files.sh

# Read a specific rule file by name
scripts/read_rule_file.sh security.properties

# Check if a key is already configured (avoid duplicates — Quality Rule 1)
scripts/get_existing_rules_for_key.sh field.name api.tag
```

EasyYapi merges rules in priority order; the project folder overrides the
global folder, which overrides the built-in rules.

### Step 4: Perceive — detect custom framework patterns

**Most projects do not need custom rules.** EasyYapi understands standard HTTP
frameworks (Spring MVC, WebFlux, JAX-RS, Feign) out of the box. Before
proposing a rule, scan the project for the **Custom-Pattern Catalog** signals
documented in the bundled `docs/rule-guide.md` (section "Custom-Pattern Catalog").

Use the discovery patterns under "General codebase-perception tools" above —
`find_classes_by_supertype` (your `extends`/`implements` scan) is the most
common blind spot, since annotation-only scans miss inheritance-declared
components. For each candidate, ask: *does it change the request/response
contract invisibly?* If yes, apply the catalog recipe. If no, no rule is
needed.

### Step 5: Reason — is a rule actually needed?

Confirm a rule is required before drafting. Standard framework behaviour is
already handled automatically — do not re-declare defaults such as
`@Deprecated` status, `@RequestMapping` paths, or `@RequestParam` names. Only
write rules for **invisible contracts** the plugin cannot detect (custom
filters, interceptors, argument resolvers, response wrappers, non-standard
annotations).

If the request is ambiguous, ask the user a short clarifying question with
concrete options (single/multi choice) so they can answer quickly — mirroring
the built-in agent's `ask_clarification`. Fall back to a plain-text question
only when you can't enumerate options.

### Step 6: Act — draft the new rule content

Propose new rule content in the rule file format documented in the guide:
- `<key>[<filter>]=<value>` (one rule per line; filter optional).
- Groovy scripts for advanced cases (`groovy:` prefix filter, or a multi-line
  groovy value-block — see Critical Quality Rule 2).

### Step 7: Act — insert and show the diff

Insert the new rules into a file in `.easyapi/` (project) or `~/.easyapi/`
(global). **Always show the user the diff before applying** — EasyYapi rules
affect API export across the whole project, so the user must confirm the
change. This mirrors the built-in agent's `propose_rule_content` →
user-confirmed "Save…" flow; the only difference is you write the file
directly instead of staging it through a UI.

### Multi-app namespacing

When a workspace hosts more than one app (each IntelliJ Module /
`spring.application.name` is a candidate app — detect by scanning the
project's module structure and `application.yml` files), namespace every
per-app env var by a resolved key so exports don't collide. Apply this
**even for a single-app workspace** — a single app today doesn't mean a
single app forever, and bare `{{host}}` / `${Authorization}` would collide
if the user exports a different app later into the same Postman environment.

- **Resolve the namespace key** in order: (1) Module name (inferred from
  the source file's IntelliJ module / directory), normalized lower-case
  with spaces/underscores → hyphens, characters outside `[a-z0-9-]`
  stripped, capped at 40 chars; (2) `spring.application.name` read
  directly from the app's `application.yml`/`application-*.yml`/
  `application.properties` (fall through if absent); (3) ask the user a
  short clarifying question on collision or unresolved ambiguity.
- **Namespace every env var**: host `{{<key>}}`, bearer `{{<key>-token}}`,
  login `{{<key>-username}}`/`{{<key>-password}}` (lower-case by default;
  UPPER when an existing rule already uses it — reuse the existing casing
  rather than renaming). The producer's stored name and the consumer's
  referenced name MUST be identical (bundle integrity still holds).
- **Split bundles per app**: propose one bundle per app, each complete on
  its own (producer script + consumer header + host + env var, all sharing
  the same key); for consumers you can't confidently assign to an app
  (shared/common modules), ask the user with concrete app options.
- **Record the resolution branch** (module name / `spring.application.name` /
  user-clarified) and the resulting key in the proposal shown to the user,
  so they can correct a wrong guess before saving.
- **Fetch the full recipe** from the bundled `docs/rule-guide.md` (the
  "Multi-Application Namespace" section) — don't reproduce it from memory.
  Note: the v1 runtime converts unresolved `${...}` placeholders in
  **header values** only; do not promise body-level namespacing.

## Critical Rule File Format (follow exactly — inlined from the agent preamble)

Each line is `<key>[<filter>]=<value>` or `<key>=<value>` (no filter). The
filter goes **INSIDE `[...]` AFTER the key** — NEVER before it. There is no
`filter?key=value` form.

```
api.tag[$class:com.example.UserController]=user
method.additional.header={"name":"Authorization","value":"Bearer ${token}","desc":"","required":true}
```

Valid filter prefixes (and ONLY these):

- `$class:<FQN>` — exact class-name match. **Wildcards are NOT supported.**
  For package/pattern matching use `groovy:` (e.g.
  `groovy: it.containingClass()?.name()?.startsWith("com.example.web.")`).
- `@<AnnotationFqn>` — annotation presence.
- `#regex:<pattern>` — regex match; captured groups available as `${1}`,
  `${2}` in the value.
- `#<tag>` — JavaDoc/KDoc tag.
- `!<expr>` — negation.
- `groovy:<script>` — truthy script result = match.

There is **no `~` prefix** and **no bare `class:` prefix** — the older
`class:com.example.Foo` and `~regex` forms are invalid; use `$class:` and
`#regex:` respectively.

## Critical Quality Rules (follow exactly — inlined from the agent preamble)

These mirror the rules the built-in agent enforces via its system prompt.

### 1. Check existing rules before writing (avoid duplicates)

Before proposing any rule for a key, read the existing rule files and check
whether an equivalent rule already exists in **any** source — project
(`.easyapi/`), global (`~/.easyapi/`), or bundled extension (Swagger /
Jackson / etc.).

- If an equivalent rule already exists, do NOT write a duplicate. Tell the
  user where it already lives and skip it.
- If a broader rule already covers your case (e.g. a `groovy:` filter matching
  a package prefix, and you were about to add one for a sub-package), do NOT
  add a narrower duplicate unless it overrides with a different value.
- Extension-source rules (Swagger annotations, Jackson modules, etc.) are
  already in effect. Never re-declare what the extension already provides
  (e.g. `api.status[@java.lang.Deprecated]=deprecated` is handled by the
  built-in extension — do not write it).

### 2. Prefer groovy value-blocks for complex conditional logic

When a filter expression grows long — multiple `&&`/`||`, multiple
exclusions, or nested method calls — the `key[groovy:…]=value` form becomes
unreadable on a single line. Switch to the **groovy value-block** form: the
value itself is a multi-line groovy script that returns the value when the
condition holds, or `null` when it doesn't. See the rule guide's Groovy
Binding Reference for the `it` object API.

**Bad (unreadable single-line filter):**
```
method.additional.header[groovy: it.containingClass()?.name()?.startsWith("com.example.merchant.") && it.containingClass()?.name() != "com.example.merchant.AuthController"]={"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

**Good (multi-line groovy value-block):**
```
method.additional.header=groovy:```
def cls = it.containingClass()?.name()
if (cls?.startsWith("com.example.merchant.")
    && cls != "com.example.merchant.AuthController") {
    return '{"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}'
}
return null
```
```

Rules of thumb:
- **≤ 1 condition** → inline `<key>[<filter>]=<value>` is fine.
- **≥ 2 conditions or exclusions** → use a groovy value-block.
- The script must `return` the value (string) or `return null` to skip.

### 3. Never generate blanket field-ignore rules

Do NOT generate `field.ignore` rules based on field-name patterns like
`.*password.*`, `.*secret.*`, `.*token.*`. These fields are often a
**legitimate part of the API definition** — a login endpoint requires
`password`, an OAuth endpoint requires `clientSecret`, a token-refresh
endpoint requires `refreshToken`. Stripping them silently breaks the exported
documentation.

Sensitive-field handling is a **project policy** decision, not a
code-detection decision. If the user explicitly asks for it, you may add it
— but never invent it on your own, and always warn the user that it may hide
fields that some endpoints legitimately require.

### 4. Don't re-declare framework defaults

Standard Spring MVC / WebFlux / JAX-RS / Feign endpoints need no rules —
the plugin detects them out of the box. `@Deprecated` status, `@RequestMapping`
paths, `@RequestParam` names, etc. are all handled automatically. Only write
rules for **invisible contracts** the plugin cannot detect (custom filters,
interceptors, argument resolvers, response wrappers, non-standard annotations).

## Common Key-Name Mistakes (do not use these)

These keys do **not** exist — use the correct alternative:

| Does NOT exist | Use instead |
|----------------|-------------|
| `api.header` | `method.additional.header` |
| `api.header.additional` | `method.additional.header` |
| `path.prefix` | `class.prefix.path` / `endpoint.prefix.path` |

`method.additional.header` and `method.additional.param` values are **JSON
objects** (one per line):
`{"name":"…","value":"…","desc":"…","required":…}`, not `Name:Value`.

## Two-Approach Note for the User

If the user is unsure how to invoke this skill versus the plugin's built-in
AI assistant, briefly explain:

- **Built-in AI assistant (Settings → EasyYapi → Rules → Chat / Magic)** — In
  IntelliJ with EasyYapi installed, open Settings → EasyYapi → Rules, edit a
  rule file, and click **Chat** (reveals the inline AI panel) or **Magic**
  (runs a built-in review-and-detect instruction). The plugin's agent runs a
  perceive→reason→act loop, calls its PSI perception tools to inspect the
  project, and stages a proposal the user reviews and saves. Best for users
  who want everything inside IntelliJ.
- **This skill (external assistant)** — Use your existing AI coding assistant
  (Trae, Cursor, Cline, Continue, etc.) which already has access to the
  project's files. The skill bundles the **same** knowledge-base pages and
  gives the assistant the same workflow, mapping each built-in PSI tool to a
  CLI equivalent. Best for users already invested in an external AI workflow.

Both approaches share the same knowledge base (the built-in agent reads it
from the plugin JAR via `get_plugin_doc`; this skill ships a verbatim copy,
kept in sync by the `syncKnowledgeBase` Gradle task), so the rule content
they produce is consistent.

## What This Skill Does NOT Do

- It cannot call the plugin's runtime AI tools directly. You — the external
  assistant — provide equivalent capability via the bundled scripts (rule
  files + existing keys) and your own file/grep access (PSI inspection +
  class discovery).
- It cannot enumerate the project's cached HTTP endpoints
  (`list_project_endpoints`) — that requires the plugin's `ApiIndex` cache,
  which only exists inside a running IntelliJ. You don't need it: rules are
  about contracts detectable from source.
- It does not configure or test AI providers. The built-in assistant's
  configuration lives in IntelliJ Settings → EasyYapi → AI.
- It does not modify the EasyYapi plugin itself or its bundled config.

## Reference Pointers

**Bundled with this skill (available after `npx skills add` — read these):**
- `docs/rule-guide.md` — rule file format, filter syntax, recipes, Custom-Pattern
  Catalog, Workflow-Pattern Catalog, Groovy binding reference.
- `docs/rule-keys.md` — complete rule-key catalog (snapshot of `RuleKeys.kt`).
- `docs/index.md`, `docs/README.md`, `docs/settings-guide.md`, `docs/usage-guide.md`,
  `docs/easyapi-script-reference.md` — the rest of the knowledge base.
- `scripts/` — CLI tools mirroring the built-in AI perception tools (see
  "EasyYapi-domain tools" above).

Plugin home: https://github.com/tangcent/easy-yapi

## Correct Example

User asks: "Add a rule that renames the `createTime` field to `created_at`
in all exported APIs."

Workflow:
1. Read the bundled `docs/rule-guide.md` — find the field-rename section / the
   `field.name` key.
2. Check the bundled `docs/rule-keys.md` — confirm the key is `field.name` (alias
   `json.rule.field.name`), mode `replace`.
3. **Run `scripts/get_existing_rules_for_key.sh field.name`** — confirm no
   `field.name` rule already covers this.
4. Open (or create) `<project>/.easyapi/field.rules`.
5. Draft (using the correct `key[filter]=value` format; a field rename map
   is a JSON object value with no filter):
   ```
   field.name={"createTime":"created_at"}
   ```
6. Show the user the diff and apply on confirmation.

## Forbidden Patterns

- **Do not** invent rule keys not present in the bundled `docs/rule-keys.md`.
  Unknown keys are silently ignored by the plugin's config loader.
- **Do not** use the `filter?key=value` form, the `~` regex prefix, or the
  bare `class:` prefix — they are invalid. Use `key[filter]=value`,
  `#regex:`, and `$class:` respectively.
- **Do not** use the non-existent keys `api.header`, `api.header.additional`,
  or `path.prefix` — use `method.additional.header` / `class.prefix.path` /
  `endpoint.prefix.path`.
- **Do not** generate blanket `field.ignore` rules from field-name patterns
  (password / secret / token) — see Quality Rule 3.
- **Do not** re-declare framework defaults (Spring MVC / JAX-RS / Feign
  behaviour is handled automatically).
- **Do not** write Groovy scripts that touch the filesystem or network —
  rule scripts run in a sandboxed Groovy shell with restricted access.
- **Do not** modify the plugin's bundled `src/main/resources/extensions/*.config`
  files. Those are the plugin's own; user rules go in `.easyapi/` or
  `~/.easyapi/`.
