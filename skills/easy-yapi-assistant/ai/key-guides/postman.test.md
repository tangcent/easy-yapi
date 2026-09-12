---
id: postman.test
key: postman.test
scheme-stamp: f860510c8b69
title: Postman test script
cue: JavaScript assertion attached to an endpoint, fired AFTER the response
channel: postman
---

## When to use

`postman.test` fires AFTER the response is received. Use it to:

- Read `pm.response` and assert on status / body fields.
- `pm.environment.set("token", …)` to extract a token from the response
  for use by subsequent requests (auth-token-chaining producer side).

## pm.* runtime API (available only inside Postman)

`get_rule_context` never binds `pm.*`: that API exists only when the generated
script runs inside Postman. Common members you can reference in the final
script:
- `pm.response` — `.status`, `.code`, `.json()`, `.text()`, `.to.have.status(200)`.
- `pm.expect(…).to.{be,have,equal,…}` — BDD-style assertions.
- `pm.environment.set(key, value)` / `get(key)` — per-environment storage
  (use for chained tokens).
- `pm.variables.set(key, value)` — session-scoped variables.
- `pm.response.to.be` chain — response assertions.

## postman.test vs postman.prerequest (#1 mistake)

`postman.test` fires AFTER the response (read `pm.response`,
`pm.environment.set` a token). `postman.prerequest` fires BEFORE the
request (inject headers, compute signatures, mutate `pm.request`).
Swapping them is the most common workflow-rule error: a token extracted
in `prerequest` reads the PREVIOUS response (or none); a header injected
in `test` lands after the request has gone out.

## Bundle integrity (CRITICAL)

Workflow rules that form a chain (login-script + consumer-header) MUST
be proposed together in a single `propose_rule_content` call. Proposing
half a chain is forbidden — a consumer header that references a token no
script stores is a silent bug.

## Shared-env-var rule

When the producer stores a value with `pm.environment.set("<name>", …)`, the
consuming header rule MUST reference the **same** `<name>` (e.g.
`Bearer ${<name>}`). Tell the user the env var is created in the Postman
Environments panel (or on the first login run) — the bundle is not usable
until it exists. Reuse an existing name when the project already references
one; default to `Authorization` otherwise.

## No hardcoded secrets

Every credential is an env-var reference (`${Authorization}`, `${appSecret}`,
`${apiKey}`). Never emit a literal token, key, or password in rule content.

## Never strip legitimate auth fields

Do NOT pair an auth workflow with a blanket `field.ignore` for `password`,
`secret`, `clientSecret`, or `refreshToken`. A login endpoint **legitimately
requires** `password`; stripping it breaks the exported documentation. See
the agent base rule "Never generate blanket field-ignore rules".

## Script-context isolation (CRITICAL — silent-failure trap)

`postman.test`/`postman.prerequest` rule values MUST be **literal scripts**
(NO `groovy:` prefix). A `groovy:` prefix routes the value to
`Jsr223ScriptParser` at export time, where `pm` is NOT bound — the script
throws and the failure is **silently swallowed**, so no script lands in the
Postman collection. (Conversely, `http.call.before`/`http.call.after` values
MUST use `groovy:` — see `http.call.after`.)

## Anti-duplication

Before proposing any header/script rule, call `get_existing_rules_for_key`
for each key in the bundle; skip any rule already present in any source
(project / global / extension / remote), naming the source it already lives
in. See the agent base quality rule "Check existing rules before writing".
