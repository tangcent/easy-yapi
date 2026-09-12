---
id: hmac-signing
title: HMAC request signing
cue: javax.crypto.Mac / HmacSHA256 in a filter, appSecret / appKey config
---

`javax.crypto.Mac` / `HmacSHA256` in a filter or interceptor, plus
`appSecret` / `appKey` configuration. The signer computes a signature
over the request (method, path, body, timestamp) and adds it as a header;
the consumer side must replicate the signature exactly. This is the most
contract-sensitive workflow pattern — a byte-wrong signature silently
rejects every request.

## Detection signals

- `javax.crypto.Mac` / `HmacSHA256` / `HmacSHA1` / `HmacMD5` referenced
  in a filter or interceptor class.
- `appSecret` / `appKey` / `secretKey` / `accessKey` configuration fields
  read in a filter.
- A signature header name like `X-Signature`, `X-Sign`, `Signature`,
  `X-HMAC-Signature` set on the request.
- A canonical-string construction method (building a string from method,
  path, body, timestamp before signing).

## Discovery

- `find_classes_by_supertype` for filters / interceptors (see the
  `spring-filters-interceptors` / `jaxrs-filters` detections for the
  supertype lists).
- `get_psi_method_info` with `detail="full"` on the signer's signing
  method to read the canonical-string construction (header order matters);
  `get_psi_class_info` to find the secret fields (`appSecret`, `appKey`).
- `get_existing_rules_for_key` for `method.additional.header` and
  `postman.prerequest` to avoid duplicates.

## Perceive → reason → act

- **Perceive.** Run the discovery tools above. For each hit, read the
  signing method body via `get_psi_method_info` with `detail="full"` to
  extract:
  - The signature header name (e.g. `X-Signature`).
  - The canonical-string inputs (method? path? body? timestamp? nonce?).
  - The canonical-string order (header order matters byte-for-byte).
  - The hash algorithm (`HmacSHA256`, `HmacSHA1`, etc.).
  - The encoding of the signature (hex? base64?).
  - The secret field names (`appSecret`, `appKey`).
- **Reason.** Identify the signature header name and the canonical-string
  inputs. The Postman pre-request script must replicate the canonical
  string byte-for-byte (watch for trailing newlines, header casing,
  URL encoding). Confirm the env-var names for the secrets — reuse
  existing names where present; otherwise default to `${appSecret}` /
  `${appKey}`.
- **Act.** Propose the bundle (signer pre-request script + consumer
  header) in one `propose_rule_content` call (filename like
  `hmac-signing.rules`). Bundle integrity applies — a consumer header
  referencing a signature no script computes is a silent bug.

## Recipe (full bundle — propose every line together)

**Pre-request signing script** (computes the HMAC, attaches the signature
header):
```
postman.prerequest[groovy: it.containingClass()?.qualifiedName().startsWith("com.example.api.")]=def mac = javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec("${appSecret}".getBytes("UTF-8"), "HmacSHA256")); def stringToSign = pm.request.url + "\n" + pm.request.body; def raw = mac.doFinal(stringToSign.getBytes("UTF-8")); def sig = raw.collect { String.format("%02x", it) }.join(); pm.request.headers.upsert("X-Signature", sig)
```

**No hardcoded secret** — `${appSecret}` is always an env-var reference.
⚠ For non-trivial signing (AWS SigV4, etc.) treat this as a scaffold and call
`ask_clarification` for the canonical-string / algorithm variant.

## Bundle integrity (CRITICAL)

Signer + signed-consumer rules MUST be proposed together in a single
`propose_rule_content` call. Proposing half a chain is forbidden.

## No hardcoded secrets

Every credential in a workflow rule is an env-var reference
(`${appSecret}`, `${appKey}`). Never emit a literal secret in rule
content.

## Script-context isolation (CRITICAL)

`postman.prerequest` rule values MUST be **literal scripts** (NO
`groovy:` prefix). A `groovy:` prefix routes the value to
`Jsr223ScriptParser` at export time, where `pm` is NOT bound — the
script throws and the failure is silently swallowed, so no script
lands in the Postman collection.
