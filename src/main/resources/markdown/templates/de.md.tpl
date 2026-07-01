# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> GRUNDLAGEN

**Pfad:** {{{api.path}}}

**Methode:** {{{api.method}}}

{{#if api.description}}**Beschreibung:**

{{{api.description}}}

{{/if}}

> ANFRAGE

{{#if api.http.pathParams}}**Pfad-Parameter:**

| Name | Wert | erforderlich | Beschreibung |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Abfrage:**

| Name | Wert | erforderlich | Beschreibung |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Header:**

| Name | Wert | erforderlich | Beschreibung |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Formular:**

| Name | Wert | erforderlich | Typ | Beschreibung |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Anfragekörper:**

| Name | Typ | Beschreibung |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**Anfrage-Beispiel:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> ANTWORT

**Antwortkörper:**

| Name | Typ | Beschreibung |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**Antwort-Beispiel:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> GRUNDLAGEN

**Protokoll:** gRPC

**Dienst:** {{{api.grpc.serviceName}}}

**Methode:** {{{api.grpc.methodName}}}

**Streaming:** {{{api.grpc.streamingType}}}

**Vollständiger Pfad:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Beschreibung:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> ANFRAGE

**Anfragekörper:**

| Name | Typ | Beschreibung |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**Anfrage-Beispiel:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> ANTWORT

**Antwortkörper:**

| Name | Typ | Beschreibung |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**Antwort-Beispiel:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
