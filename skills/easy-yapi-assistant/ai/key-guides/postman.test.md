---
id: postman.test
key: postman.test
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
