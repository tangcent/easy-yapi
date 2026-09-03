---
id: markdown.curl.host
key: markdown.curl.host
title: Markdown cURL host override
cue: host override for the {{{api.http.curl()}}} placeholder in Markdown export
channel: markdown
---

## Value shape

`markdown.curl.host` overrides the host used by the `{{{api.http.curl()}}}`
placeholder in Markdown export. Blank (or unset) → the default
`{{host}}` placeholder is used, which round-trips through environment
rendering later.

This key is read by name via `ConfigReader.getFirst("markdown.curl.host")`
and is **not** declared as a `RuleKey` constant — it is document-level
config, never evaluated by the rule engine against a PSI element. It is
registered as an *implicit* key, so `list_rule_keys` surfaces it too (with
this guide attached as `detailPromptId`).
