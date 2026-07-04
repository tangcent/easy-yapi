# EasyApi Knowledge Base

Welcome to the EasyApi knowledge base. This is the canonical documentation the
in-IDE AI assistant reads via its `get_plugin_doc` tool, and the page the Rules
tab's **Help** button opens.

## When do you need a custom rule?

**Most projects do not need custom rules.** EasyApi understands standard HTTP
frameworks out of the box — Spring MVC (`@RestController`, `@RequestMapping`,
`@GetMapping`, …), Spring WebFlux, JAX-RS (`@Path`, `@GET`, …), and Feign
clients. If your project uses one of these, export works without any rule
files.

You need a custom rule only when the scanner cannot see something that changes
the **request or response contract invisibly** — for example:

- A `jakarta.servlet.Filter` / `jakarta.servlet.http.HttpFilter` that requires
  a header on every request and rejects calls without it.
- A `@ControllerAdvice` + `ResponseBodyAdvice` that wraps every response in
  `{ "code": 0, "data": …, "msg": "ok" }`.
- A `HandlerMethodArgumentResolver` that injects a parameter the source code
  does not declare (e.g., the current user).
- A custom annotation like `@RequirePermission("admin")` that should become a
  Postman header or an `method.doc`.

The [Custom-Pattern Catalog](rule-guide.md#custom-pattern-catalog) in the Rule
Authoring Guide lists the patterns to look for and the rule recipe to use for
each.

## Pages

| Page | What it covers |
|------|----------------|
| [Index](index.md) | A flat map of every topic → page. Start here when you don't know which page to read. |
| [Rule Authoring Guide](rule-guide.md) | Rule file format, the full rule-key catalog, filter syntax, expression prefixes, recipes, the Custom-Pattern Catalog, and AI-assisted authoring. |
| [Settings Guide](settings-guide.md) | Every field in Settings → EasyApi, grouped by tab, with the underlying `Settings` property name. |
| [Usage Guide](usage-guide.md) | End-user workflows: install, first export, API Dashboard, search, field conversion, pre/post scripts, AI-assisted rule creation. |
| [Script Reference](easyapi-script-reference.md) | The Postman-compatible `pm.*` Groovy API for pre-request / post-response scripts. |

## Where rules live (3.0 model)

EasyApi 3.0 discovers rule files by **folder**, not by an explicit list:

- **Project rules** — `<project>/.easyapi/*.rules` (or `*.properties`). Every
  regular file in this folder is loaded. Legacy `.easy.api.config*` files in
  the project root (and ancestor directories) are still read for backwards
  compatibility, but new files should go in `.easyapi/`.
- **Global rules** — `~/.easyapi/*.rules`. Same model, applied to every
  project on the machine.
- **Built-in rules** — bundled inside the plugin; always loaded.
- **Remote rules** — URLs configured in Settings → Remote.

A file can be **disabled** without being deleted: in the Rules tab, uncheck
the **Enabled** box next to the file. Disabled paths are persisted in
`Settings.disabledAutoRuleFiles` (project) or
`Settings.disabledGlobalRuleFiles` (global).

## AI-assisted rule authoring

The Rules tab has three buttons in its bottom action bar:

- **Chat** — reveals the inline AI chat panel. Type a request in natural
  language; the assistant reads your rules, reasons, and proposes content.
- **Magic** — runs a built-in "review and improve" instruction that also
  asks the assistant to detect custom framework patterns that lack a rule.
- **Help** — opens this page.

The assistant **never writes files without your explicit approval**. Every
state-changing action tool requires an Approve click. See the
[Rule Authoring Guide](rule-guide.md#ai-assisted-rule-creation) for the full
workflow.
