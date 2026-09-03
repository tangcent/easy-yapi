---
id: markdown.template
key: markdown.template
title: Markdown template & locale
cue: markdown.template (local file or remote URL) and markdown.template.language (BCP-47 locale tag)
---

## Value shape

Two related keys drive Markdown export:

- `markdown.template` — accepts either a local file path OR a remote
  `http(s)://` URL. The resolver auto-detects by the `http(s)://`
  prefix. The separate `.file` and `.url` keys were merged into this
  single key for simpler configuration.
- `markdown.template.language` — a BCP-47 locale tag (e.g. `zh-CN`,
  `ja`) selecting a localized template. A single-line rule.

## Locale proposal workflow

When the ambient `user language` is non-English AND no
`markdown.template.language` rule is already in effect AND the user's
request touches Markdown export or asks for localized docs, propose
`markdown.template.language=<tag>` (a single-line rule).

- Do **not** author a full custom template when a bundled template
  covers the locale — `zh-CN` ships bundled; `en` (or unset) uses the
  default English template and needs no rule. A bundled template is
  always preferred over a hand-authored one because it tracks
  structural changes to the default.
- If no bundled template exists for the locale, tell the user, fall
  back to English for this export, and suggest the "Copy default
  template" affordance in the Markdown export panel so they can author
  a localized copy as a starting point.
- This proposal flows through `propose_rule_content` like any other
  rule — the user reviews and saves. Never write the rule silently.
