---
name: "easy-yapi-assistant"
description: "Help author EasyApi rule files (.easyapi/ folder). Invoke when the user is editing an EasyApi rule file or asks to add/modify EasyApi rules (annotations, field rename, custom headers, etc.)."
---

# EasyApi Rule Authoring Assistant

This skill helps you write or modify EasyApi rule files for the user's
project. EasyApi rules are key=value entries in `.rules` / `.properties`
files that the EasyApi IntelliJ plugin reads to customise API export
(YApi/Postman/Markdown), field naming, annotations, and more.

## When to Use

Invoke this skill when:
- The user opens or edits any file in the project's `.easyapi/` folder or
  the global `~/.easyapi/` folder.
- The user asks to "add a rule", "rename a field", "ignore a class", "add a
  header to every POST", etc. in the EasyApi context.
- The user mentions `easy.api`, `EasyApi`, or `easyapi` in a request about
  config or rule authoring.

## Self-Contained References (read these first)

This skill ships with **bundled reference files** alongside `SKILL.md` so it
works after `npx skills add tangcent/easy-yapi -g -y` even when the easy-yapi
repo / plugin JAR is not present in the user's project. Always read these
first — do **not** rely on memory or guess syntax:

- `rule-guide.md` — the rule file format, filter syntax, expression prefixes,
  Groovy binding reference, recipes, and the Custom-Pattern Catalog.
- `rule-keys.md` — the complete rule-key catalog (snapshot of `RuleKeys.kt`).

> Repo-only references that are **not** available after install (and must
> not be depended on at runtime): `docs/knowledge-base/*`,
> `src/main/resources/ai/agent-preamble.md`, and
> `src/main/kotlin/.../RuleKeys.kt`. The critical content from the agent
> preamble is inlined below under "Critical Rule File Format" and "Critical
> Quality Rules" so you don't need the original file.

## Bundled Scripts (CLI mirrors of the built-in AI tools)

The skill ships with **3 shell scripts** in `scripts/` that mirror the
perception tools the built-in IntelliJ agent has. Run them from the project
root (the CWD when the assistant is invoked). They auto-detect the project
root and `~/.easyapi/` so you never need to hard-code paths.

Only EasyApi-domain-specific tools are mirrored (locating and resolving
`.easyapi/` rule files across project + global scopes). General codebase
perception — finding classes by annotation or supertype — is left to your
own file/grep capabilities.

| Script | Mirrors built-in tool | What it does |
|--------|-----------------------|--------------|
| `scripts/list_rule_files.sh` | *(ambient: `RuleFileResolver.listRuleFiles`)* | Lists every `.properties` / `.rules` file in `<project>/.easyapi/` and `~/.easyapi/`, labeled `[global]` / `[project]`. |
| `scripts/read_rule_file.sh <name>` | `read_rule_file` | Reads a rule file by name. Supports `global:` / `project:` scope prefixes. Resolves against tracked `.easyapi/` dirs — never guesses home paths. |
| `scripts/get_existing_rules_for_key.sh <key> [<key>...]` | `get_existing_rules_for_key` | Finds all configured values for a key across project + global rule files. Prints `file:line: <line content>` so you can reason about precedence. |

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

> Scripts that are **not** provided (and why):
> - `list_rule_keys` → covered by bundled `rule-keys.md`.
> - `get_plugin_doc` → covered by bundled `rule-guide.md`.
> - `find_classes_by_annotation` / `find_classes_by_supertype` / `get_psi_class_info`
>   / `get_psi_method_info` → general codebase perception. You already have
>   file/grep capabilities to search source for annotations, supertypes, and
>   class/method structure. See Step 4 for the patterns to look for.
> - `list_project_endpoints` → needs IntelliJ's `ApiIndex` cache (not
>   available from CLI).
> - `ask_clarification` → ask the user via your own UI.
> - `propose_rule_content` / `write_rule_file` → you write directly to the
>   `.easyapi/` file (with diff confirmation — Step 7).

## Workflow

Work in a **perceive → reason → act** loop, mirroring the built-in agent.

### Step 1: Read the Authoritative Rule Guide (Perceive)

Read the bundled `rule-guide.md` (next to this `SKILL.md`) first. It is the
source of truth for the rule file format, the full rule-key catalog, filter
syntax, expression prefixes, recipes, and the Custom-Pattern Catalog.

### Step 2: Find the Right Rule Key (Perceive)

If the rule key isn't obvious from the guide, scan the bundled `rule-keys.md`
catalog. **Never invent keys not in that catalog** — unknown keys are silently
ignored by the plugin's config loader.

### Step 3: Inspect Existing Rules (Perceive)

Before proposing changes, read any existing rule files in:
- `<project>/.easyapi/` (project-scoped rules — the 3.0 model).
- `~/.easyapi/` (global rules — applied to every project on the machine).
- Legacy `.easy.api.config*` files in the project root (and parent
  directories — EasyApi walks up the tree for backwards compatibility).

**Use the bundled scripts** to inspect rules without guessing paths:

```bash
# List all rule files the plugin will load
scripts/list_rule_files.sh

# Read a specific rule file by name
scripts/read_rule_file.sh security.properties

# Check if a key is already configured (avoid duplicates — Quality Rule 1)
scripts/get_existing_rules_for_key.sh field.name api.tag
```

This avoids duplicating or contradicting existing rules. EasyApi merges rules
in priority order; the project folder overrides the global folder, which
overrides the built-in rules.

### Step 4: Detect Custom Framework Patterns (Perceive)

**Most projects do not need custom rules.** EasyApi understands standard HTTP
frameworks (Spring MVC, WebFlux, JAX-RS, Feign) out of the box. Before
proposing a rule, scan the project for the **Custom-Pattern Catalog** signals
documented in the bundled `rule-guide.md` (section "Custom-Pattern Catalog").

**Use your own file/grep tools** to find inheritance-declared components
(the most common blind spot — annotation-only scans miss them):

```bash
# Find servlet filters (extends OncePerRequestFilter, implements Filter)
rg -t java -t kt "extends\s+OncePerRequestFilter|implements\s+.*Filter"

# Find interceptors
rg -t java -t kt "implements\s+HandlerInterceptor"

# Find response wrappers
rg -t java -t kt "implements\s+ResponseBodyAdvice"

# Find argument resolvers
rg -t java -t kt "implements\s+HandlerMethodArgumentResolver"

# Find custom annotated controllers (resolve imports to confirm the FQN)
rg -t java -t kt "@RestController"
```

Remember to resolve imports / same-package usage to confirm the FQN, and
exclude the supertype itself from results — these are the same nuances you
handle whenever searching any codebase.

Additional signals to scan for:

- `jakarta.servlet.Filter` / `javax.servlet.Filter` / `HandlerInterceptor` /
  `org.springframework.web.server.WebFilter` — implementations that require a
  header on every request.
- `ResponseBodyAdvice` — implementations that wrap every response in an
  envelope like `{ code, data, msg }`.
- `HandlerMethodArgumentResolver` — implementations that inject a parameter
  the source signature does not declare.
- Meta-annotations — a custom annotation composed from `@RequestMapping`.
- Custom security annotations (e.g. `@RequirePermission`) that should become
  an `api.tag` or a Postman header.

For each candidate, ask: *does it change the request/response contract
invisibly?* If yes, apply the catalog recipe. If no, no rule is needed.

### Step 5: Reason — Is a Rule Actually Needed? (Reason)

Confirm a rule is required before drafting. Standard framework behaviour is
already handled automatically — do not re-declare defaults such as
`@Deprecated` status, `@RequestMapping` paths, or `@RequestParam` names. Only
write rules for **invisible contracts** the plugin cannot detect (custom
filters, interceptors, argument resolvers, response wrappers, non-standard
annotations).

### Step 6: Draft the New Rule Content (Act)

Propose new rule content in the rule file format documented in the guide:
- `<key>[<filter>]=<value>` (one rule per line; filter optional).
- Groovy scripts for advanced cases (`groovy:` prefix filter, or a multi-line
  groovy value-block — see Critical Quality Rule 2).

### Step 7: Insert and Show the Diff (Act)

Insert the new rules into a file in `.easyapi/` (project) or `~/.easyapi/`
(global). **Always show the user the diff before applying** — EasyApi rules
affect API export across the whole project, so the user must confirm the
change.

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

### Built-in agent toolset (for reference)

The plugin's built-in agent has these tools. You — the external assistant —
provide equivalent capability via your own file/codebase access (read the
project's `.easyapi/` files directly, search the codebase for annotations /
supertypes, etc.):

| Tool | Kind | What it does |
|------|------|--------------|
| `list_rule_keys` | perception | Lists every rule key (≡ bundled `rule-keys.md`). |
| `get_plugin_doc` | perception | Reads a knowledge-base page (≡ bundled `rule-guide.md`). |
| `read_rule_file` | perception | Reads a rule file by name (`security.properties`) or scope-prefixed name (`global:jwt.rules`). Refuses source files. |
| `list_project_endpoints` | perception | Lists cached HTTP endpoints in the project. |
| `get_psi_class_info` | perception | Class info by FQN (fields, methods, annotations). Supports batch via `fqns` array. |
| `get_psi_method_info` | perception | Method info by class FQN + method name. |
| `find_classes_by_annotation` | perception | Classes annotated with given FQN(s). Supports batch. |
| `find_classes_by_supertype` | perception | Classes extending/implementing given supertype(s). Supports batch. Use BOTH this and the annotation tool — inheritance-declared components (filters extending `OncePerRequestFilter`) are invisible to the annotation tool. |
| `get_existing_rules_for_key` | perception | All configured values for a key, with source + priority. Supports batch. |
| `ask_clarification` | perception | Asks structured clarifying questions (single_choice / multi_choice / free_text). |
| `propose_rule_content` | action (staging) | Stages a proposed rule file; user confirms via a "Save…" UI. Validated against the key catalog before staging. |

`write_rule_file` is intentionally NOT registered — the disk write happens
only through the user-confirmed "Save…" UI flow. As the external assistant,
you write directly to the `.easyapi/` file but **must** show the diff and get
the user's confirmation first (Step 7).

## Critical Quality Rules (follow exactly — inlined from the agent preamble)

These mirror the rules the built-in agent enforces via its system prompt.

### 1. Check existing rules before writing (avoid duplicates)

Before proposing any rule for a key, read the existing rule files and check
whether an equivalent rule already exists in **any** source — project
(`.easyapi/`), global (`~/.easyapi/`), or bundled extension (Swagger /
Jackson / etc.).

- If an equivalent rule already exists, do NOT write a duplicate. Tell the
  user where it already lives and skip it.
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
| `path.prefix` | `class.prefix.path` / `endpoint.prefix.path` |

`method.additional.header` and `method.additional.param` values are **JSON
objects** (one per line):
`{"name":"…","value":"…","desc":"…","required":…}`, not `Name:Value`.

## Two-Approach Note for the User

If the user is unsure how to invoke this skill versus the plugin's built-in
AI assistant, briefly explain:

- **Built-in AI assistant (Settings → EasyApi → Rules → Chat / Magic)** — In
  IntelliJ with EasyApi installed, open Settings → EasyApi → Rules, edit a
  rule file, and click **Chat** (reveals the inline AI panel) or **Magic**
  (runs a built-in review-and-detect instruction). The plugin's agent runs a
  perceive→reason→act loop, calls its perception tools to inspect the project,
  and stages a proposal the user reviews and saves. Best for users who want
  everything inside IntelliJ.
- **This skill (external assistant)** — Use your existing AI coding assistant
  (Trae, Cursor, Cline, Continue, etc.) which already has access to the
  project's files. The skill bundles the same rule guide and key catalog and
  gives the assistant the same workflow. Best for users already invested in
  an external AI workflow.

Both approaches share the same rule file format and key catalog (the built-in
agent reads the repo's `docs/knowledge-base/rule-guide.md`; this skill ships
an identical copy as bundled `rule-guide.md`), so the rule content they
produce is consistent.

## What This Skill Does NOT Do

- It cannot call the plugin's runtime AI tools (`list_rule_keys`,
  `get_psi_class_info`, etc.). You — the external assistant — provide the
  file/codebase access via your own capabilities (read the project's
  `.easyapi/` files directly, grep the codebase for annotations/supertypes).
- It does not configure or test AI providers. The built-in assistant's
  configuration lives in IntelliJ Settings → EasyApi → AI.
- It does not modify the EasyApi plugin itself or its bundled config.

## Reference Pointers

**Bundled with this skill (available after `npx skills add` — read these):**
- `rule-guide.md` — rule file format, filter syntax, recipes, Custom-Pattern
  Catalog, Groovy binding reference.
- `rule-keys.md` — complete rule-key catalog (snapshot of `RuleKeys.kt`).
- `scripts/` — 3 CLI tools mirroring the built-in AI perception tools (see
  "Bundled Scripts" section above).

**Repo-only (NOT available after install — do not depend on at runtime):**
- `docs/knowledge-base/rule-guide.md` — the source the bundled copy is
  generated from.
- `docs/knowledge-base/{README,index,settings-guide,usage-guide,easyapi-script-reference}.md`
- `src/main/kotlin/com/itangcent/easyapi/rule/RuleKeys.kt` — the source the
  bundled `rule-keys.md` is generated from.
- `src/main/resources/ai/agent-preamble.md` — the built-in agent's system
  prompt; its critical content is inlined above (Critical Rule File Format +
  Critical Quality Rules).
- `src/main/kotlin/com/itangcent/easyapi/ai/tools/` — the built-in agent's
  tool implementations.

Plugin home: https://github.com/tangcent/easy-yapi

## Correct Example

User asks: "Add a rule that renames the `createTime` field to `created_at`
in all exported APIs."

Workflow:
1. Read the bundled `rule-guide.md` — find the field-rename section / the
   `field.name` key.
2. Check the bundled `rule-keys.md` — confirm the key is `field.name` (alias
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

- **Do not** invent rule keys not present in the bundled `rule-keys.md`.
  Unknown keys are silently ignored by the plugin's config loader.
- **Do not** use the `filter?key=value` form, the `~` regex prefix, or the
  bare `class:` prefix — they are invalid. Use `key[filter]=value`,
  `#regex:`, and `$class:` respectively.
- **Do not** use the non-existent keys `api.header` or `path.prefix` — use
  `method.additional.header` / `class.prefix.path` / `endpoint.prefix.path`.
- **Do not** generate blanket `field.ignore` rules from field-name patterns
  (password / secret / token) — see Quality Rule 3.
- **Do not** re-declare framework defaults (Spring MVC / JAX-RS / Feign
  behaviour is handled automatically).
- **Do not** write Groovy scripts that touch the filesystem or network —
  rule scripts run in a sandboxed Groovy shell with restricted access.
- **Do not** modify the plugin's bundled `src/main/resources/extensions/*.config`
  files. Those are the plugin's own; user rules go in `.easyapi/` or
  `~/.easyapi/`.
- **Do not** reference repo-only paths (`docs/knowledge-base/*`,
  `src/main/resources/ai/agent-preamble.md`, `src/main/kotlin/.../RuleKeys.kt`)
  at runtime — they are absent after install. Use the bundled copies.
