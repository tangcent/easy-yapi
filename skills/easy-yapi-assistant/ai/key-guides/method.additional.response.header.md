---
id: method.additional.response.header
key: method.additional.response.header
title: Additional response header
cue: JSON object header attached to every matching endpoint's response
---

## Value format

The value is a single-line JSON object:

```json
{"name":"X-RateLimit-Remaining","value":"100","desc":"","required":false}
```

One object per line — multiple headers means multiple
`method.additional.response.header=…` lines (the key uses
`StringRuleMode.MERGE`).

Every credential in a header value is an env-var reference
(`${traceId}`, `${apiKey}`). Never emit a literal token, key, or
password in rule content.
