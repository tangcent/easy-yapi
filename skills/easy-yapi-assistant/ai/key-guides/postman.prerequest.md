---
id: postman.prerequest
key: postman.prerequest
title: Postman pre-request script
cue: JavaScript fired BEFORE the request, used to inject headers / compute signatures / mutate pm.request
channel: postman
---

## When to use

`postman.prerequest` fires BEFORE the request is sent. Use it to:

- Inject headers (compute a signature, attach a bearer token).
- Mutate `pm.request` (rewrite URL, add query params).
- Compute request bodies (HMAC canonical string).

## pm.* runtime API (available only inside Postman)

`get_rule_context` never binds `pm.*`: that API exists only when the generated
script runs inside Postman. Common members you can reference in the final
script:
- `pm.request` — the request about to be sent: `.url`, `.method`, `.headers.add({key,value})`, `.body`.
- `pm.environment.set(key, value)` / `get(key)` — per-environment storage
  (use for chained tokens / shared credentials).
- `pm.variables.set(key, value)` — session-scoped variables.
- `pm.sendRequest(...)` — issue a secondary HTTP request before the main one.

## postman.test vs postman.prerequest (#1 mistake)

`postman.test` fires AFTER the response (read `pm.response`,
`pm.environment.set` a token). `postman.prerequest` fires BEFORE the
request (inject headers, compute signatures, mutate `pm.request`).
Swapping them is the most common workflow-rule error: a token extracted
in `prerequest` reads the PREVIOUS response (or none); a header injected
in `test` lands after the request has gone out.

## Bundle integrity (CRITICAL)

Signer + signed-consumer rules MUST be proposed together in a single
`propose_rule_content` call. Proposing half a chain is forbidden — a
consumer header referencing a signature no script computes is a silent
bug.
