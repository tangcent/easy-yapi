# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> TEMEL

**Yol:** {{{api.path}}}

**Yöntem:** {{{api.method}}}

{{#if api.description}}**Açıklama:**

{{{api.description}}}

{{/if}}

> İSTEK

{{#if api.http.pathParams}}**Yol Parametreleri:**

| ad | değer | zorunlu | açıklama |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Sorgu:**

| ad | değer | zorunlu | açıklama |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Üstbilgiler:**

| ad | değer | zorunlu | açıklama |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Form:**

| ad | değer | zorunlu | tür | açıklama |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**İstek Gövdesi:**

| ad | tür | açıklama |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**İstek Örneği:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> YANIT

**Gövde:**

| ad | tür | açıklama |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**Yanıt Örneği:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> TEMEL

**Protokol:** gRPC

**Hizmet:** {{{api.grpc.serviceName}}}

**Yöntem:** {{{api.grpc.methodName}}}

**Akış:** {{{api.grpc.streamingType}}}

**Tam Yol:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Açıklama:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> İSTEK

**İstek Gövdesi:**

| ad | tür | açıklama |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**İstek Örneği:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> YANIT

**Gövde:**

| ad | tür | açıklama |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**Yanıt Örneği:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
