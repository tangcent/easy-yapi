---
id: json.additional.field
key: json.additional.field
title: Additional JSON field
cue: JSON object field injected into the serialized response (e.g. computed totals, envelope metadata)
---

## Value format

The value is a single-line JSON object describing the additional field
to inject into the serialized response:

```json
{"name":"totalAmount","value":"${total}","desc":"computed","type":"number"}
```

One object per line — multiple fields means multiple
`json.additional.field=…` lines (the key uses `StringRuleMode.MERGE`).

Every credential in a field value is an env-var reference
(`${appSecret}`, `${apiKey}`). Never emit a literal token, key, or
password in rule content.
