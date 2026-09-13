---
id: http.call.after
key: http.call.after
scheme-stamp: 10c3635ddd18
title: HTTP client post-call hook
cue: Groovy hook run after an HTTP response — inspect status, refresh tokens, request a bounded retry
---

## When to use

`http.call.after` runs **inside the plugin's HTTP client** (the in-IDE request
runner), not in Postman. Use it to react to a response: detect a `401`, refresh
a token, and ask the client to replay the request. `http.call.before` is its
pre-request sibling — the plugin-HTTP-client counterpart of
`postman.prerequest`.

## Bindings

- `request` — the request wrapper; `request.setHeader(name, value)` mutates it
  before the retry is re-sent.
- `response` — the response wrapper; `.code()`, `.body()` (both are methods —
  call them with parentheses).
- `httpClient` — issue a sub-request:
  `httpClient.newRequest(url).post().form(k, v).execute()`.
- `logger` — `logger.warn(...)`.
- `response.discard()` — **request a bounded retry**: the interceptor re-sends
  the (mutated) request. Retry limit: **3**, enforced by
  `HttpClientScriptInterceptor`.
- Cross-request storage: `session.set(...)` / `localStorage.set(...)` — never
  `pm.environment.set(...)` (that binding exists only inside Postman).

## Script-context isolation (CRITICAL — silent-failure trap)

`http.call.before`/`http.call.after` rule values MUST use the `groovy:` prefix
— they are evaluated by `Jsr223ScriptParser` at call time. `pm.*` is NOT bound
there; referencing it throws and the failure is silently swallowed. Conversely,
`postman.test`/`postman.prerequest` values must be **literal scripts** with NO
`groovy:` prefix (see `postman.test`).

## Recipe — 401 refresh + retry (no `pm.*`)

```
http.call.after=groovy: if (response.code() == 401 && httpClient) { try { def resp = httpClient.newRequest("https://api.example.com/refresh").post().form("grant_type", "refresh_token").execute(); if (resp?.code() == 200 && resp.body) { def newToken = new groovy.json.JsonSlurper().parseText(resp.body).access_token; if (newToken) { request.setHeader("Authorization", "Bearer " + newToken); response.discard() } } } catch (e) { logger.warn("401 refresh failed: " + e.message) } }
```

- Use `form(...)` (not `body(...)`) so the refresh call sends
  `application/x-www-form-urlencoded`.
- `request.setHeader(...)` + `response.discard()` is sufficient — the retry
  re-sends the mutated request wrapper.
- Keep the refresh endpoint itself **script-free** (the recursion guard limits
  sub-request hooks to depth < 2) and wrap the body in `try/catch`.
