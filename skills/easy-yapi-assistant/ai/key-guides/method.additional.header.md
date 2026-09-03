---
id: method.additional.header
key: method.additional.header
title: Additional request header
cue: JSON object header attached to every matching endpoint's request
---

## Value format

The value is a single-line JSON object:

```json
{"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

One object per line — multiple headers means multiple
`method.additional.header=…` lines (the key uses `StringRuleMode.MERGE`).

Every credential in a header value is an env-var reference
(`${Authorization}`, `${token}`, `${apiKey}`). Never emit a literal
token, key, or password in rule content.
