---
id: json.rule.convert
key: json.rule.convert
scheme-stamp: 8db2148ee976
title: Type conversion rule
cue: Rewrite or unwrap a resolved type before export (generic envelopes, enum representations)
---

## Value format

`json.rule.convert[<filter>]=<replacement>` rewrites the type the exporter
resolves for the matched element. The rule scope is a **class**, so the
filter selects the type being converted; the value is the replacement type
or a script returning one:

```
json.rule.convert[#regex:com\.example\.common\.ApiResult<(.*?)>]=${1}
json.rule.convert[$class:com.example.Status]=groovy: '{"name":"' + it.name() + '","value":' + it.ann("com.example.Code")?.value() + '}'
```

## `${1}` is a capture reference — leave it alone

Write `${1}` as-is. It is resolved from the groups captured by the `#regex:`
filter when the rule is evaluated, and config loading passes an unresolvable
`${…}` through verbatim — so the plain one-line form above works.

Do **not** wrap the line in `###set resolveProperty=false … true` toggles to
"protect" the capture; that is not needed and only adds noise. The toggles
would matter only if the file had already set `###set ignoreUnresolved=true`,
which *drops* unresolvable placeholders instead of passing them through.

## Unwrapping does not move the `@return` doc

`json.rule.convert` swaps the type; it does not touch documentation. A
method's `@return` javadoc is attached to a **field of the response type** —
by default the inferred generic field (`data` on `Result<T>`), or the field
named by `method.return.main`.

So after `ApiResult<T>` → `T`, the envelope is gone:

- never point `method.return.main` at an envelope field (`data`) — it no
  longer exists on the unwrapped type, and the rule then does nothing;
- either name a field of the **unwrapped** type, or omit
  `method.return.main` and let the generic field be inferred;
- if the `@return` text should stay on the envelope's payload field, do not
  unwrap at all.

Call `get_existing_rules_for_key` for this key first — an envelope unwrap is
often already configured in another source.

## Related keys

- `method.return.main` — which field of the response type receives the method's `@return` doc.
- `json.additional.field` — inject a field into the serialized response.
