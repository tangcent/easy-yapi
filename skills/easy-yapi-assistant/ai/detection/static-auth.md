---
id: static-auth
title: Static auth (API key / Basic)
cue: "a filter or annotation reading X-API-Key / Authorization: Basic — static credentials, no login round-trip"
---

A filter / interceptor / annotation reading `X-API-Key` or
`Authorization: Basic` — static credentials supplied per request, with no
login round-trip. Unlike auth-token-chaining, there is no producer
endpoint; the credential is supplied by the caller out-of-band.

## Detection signals

- A filter / interceptor / annotation reading `X-API-Key` or
  `Authorization: Basic` from the request.
- A custom security annotation (`@RequiresApiToken`, `@ApiKey`, `@BasicAuth`)
  marking secured endpoints.
- A Spring Security configuration that protects URL patterns with API-key
  or Basic auth (e.g. `http.httpBasic()` or a custom
  `OncePerRequestFilter` reading `X-API-Key`).

The credential is **static** (per-caller, supplied out-of-band) — there is
no login endpoint that returns a token. This distinguishes static-auth
from auth-token-chaining.

## Discovery

- `find_classes_by_annotation` for security annotations
  (`@RequiresApiToken`, `@ApiKey`, `@BasicAuth`, custom security
  annotations).
- `find_classes_by_supertype` for filters extending `OncePerRequestFilter`
  that read header values (see the `spring-filters-interceptors` detection
  for the supertype list).
- `get_psi_method_info` with `detail="full"` on the filter's
  `shouldNotFilter` / `doFilterInternal` to confirm the scope (which paths
  are excluded from auth).

## Perceive → reason → act

- **Perceive.** Run both discovery tools. For each hit, `get_psi_method_info`
  with `detail="full"` on the filtering method to read which header name is
  read and which paths are excluded. `get_existing_rules_for_key` for
  `method.additional.header` to avoid duplicates.
- **Reason.** Identify the credential env-var name. Reuse an existing
  env-var name when one is already referenced in the project's rules;
  otherwise default to `apiKey` (for `X-API-Key`) or `Authorization` (for
  Basic). Confirm the filter's scope so the rule's filter matches exactly
  the protected controllers — not too broad, not too narrow.
- **Act.** Propose the `method.additional.header` rule(s) in one
  `propose_rule_content` call (filename like `static-auth.rules`). Bundle
  integrity applies: if the same filter also injects a response header
  (e.g. `WWW-Authenticate` on 401), both rules go in the same proposal.

## Recipe (full bundle — propose every line together)

**API key in a header:**
```
method.additional.header={"name":"X-API-Key","value":"${apiKey}","desc":"api key","required":true}
```

**API key in a query param:**
```
method.additional.param={"name":"key","type":"String","value":"${apiKey}","required":true,"desc":"api key"}
```

**HTTP Basic:**
```
method.additional.header={"name":"Authorization","value":"Basic ${basicAuth}","desc":"http basic credentials","required":true}
```

**No script** — the credential is static, so the user supplies it once in the
Postman Environments panel (base64-encode `user:pass` for Basic). Reuse an
existing env-var name when the project already references one; otherwise
default to `apiKey` (API key) or `Authorization` (Basic).

## No hardcoded secrets

Every credential in a workflow rule is an env-var reference
(`${apiKey}`, `${Authorization}`). Never emit a literal token, key, or
password in rule content.
