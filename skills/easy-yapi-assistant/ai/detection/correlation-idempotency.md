---
id: correlation-idempotency
title: Correlation & idempotency headers
cue: X-Request-Id, X-Correlation-Id, Idempotency-Key headers injected per request
---

Per-request injection of correlation / idempotency headers —
`X-Request-Id`, `X-Correlation-Id`, `Idempotency-Key`. A filter or
interceptor generates the value (UUID, counter) and adds it to the
outgoing request; the export must surface the header so the consumer
knows the contract.

## Detection signals

- A filter / interceptor / web filter that sets `X-Request-Id`,
  `X-Correlation-Id`, `Idempotency-Key`, `X-Trace-Id`, or similar
  correlation/tracing headers on the request or response.
- A custom annotation (`@Correlated`, `@Idempotent`) marking endpoints
  that require the header.
- Constants/fields named `REQUEST_ID_HEADER`, `CORRELATION_ID_HEADER`,
  `IDEMPOTENCY_KEY_HEADER` — strong signal of a per-request injection.

## Discovery

- `find_classes_by_supertype` for filters / interceptors extending
  `OncePerRequestFilter` / implementing `HandlerInterceptor` (Spring) or
  `ContainerRequestFilter` / `ContainerResponseFilter` (JAX-RS). See the
  `spring-filters-interceptors` / `jaxrs-filters` detections for the full
  supertype lists.
- `find_classes_by_annotation` for `@WebFilter` and any custom correlation
  / idempotency annotations.
- `get_psi_method_info` with `detail="full"` on the filter to read the
  header name being set and the value source (UUID? counter? request
  attribute?).

## Perceive → reason → act

- **Perceive.** Run both discovery tools. For each hit, `get_psi_method_info`
  with `detail="full"` on the filtering method to read the header name(s)
  and value source. `get_existing_rules_for_key` for
  `method.additional.header` and `method.additional.response.header` to
  avoid duplicates.
- **Reason.** Confirm the header name(s). If multiple correlation headers
  are present (e.g. both `X-Request-Id` and `X-Correlation-Id`), bundle
  them in one proposal. Reuse an existing env-var name when one is already
  referenced in the project's rules; otherwise the value is typically a
  literal or a script reference (`groovy: UUID.randomUUID().toString()`).
  For idempotency headers, the consumer typically generates the value
  client-side, so the rule's `value` is a script reference, not a literal.
- **Act.** Propose the `method.additional.header` rule(s) in one
  `propose_rule_content` call (filename like `correlation.rules`). If the
  filter also injects the header into the response (echo-back for tracing),
  bundle a `method.additional.response.header` rule in the same call.

## Recipe (full bundle — propose every line together)

**Correlation ID** (global, pre-request):
```
postman.prerequest=pm.request.headers.upsert("X-Request-Id", java.util.UUID.randomUUID().toString())
```

**Idempotency key** (scoped to mutating methods — never unscoped):
```
postman.prerequest[groovy: it.methodType().name() == "POST" || it.methodType().name() == "PUT"]=pm.request.headers.upsert("Idempotency-Key", java.util.UUID.randomUUID().toString())
```

Uses `pm.request.headers.upsert(...)` (add-or-replace, case-insensitive), not
`.add(...)` (which would duplicate). If the filter also echoes the header on
the response, bundle a `method.additional.response.header` rule in the same
proposal.

## Script-context isolation (CRITICAL)

`postman.prerequest` rule values MUST be **literal scripts** (NO `groovy:`
prefix). A `groovy:` prefix routes the value to `Jsr223ScriptParser` at export
time, where `pm` is NOT bound — the script throws and the failure is silently
swallowed, so no script lands in the Postman collection.
