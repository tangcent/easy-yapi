# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> INFORMAZIONI DI BASE

**Percorso:** {{{api.path}}}

**Metodo:** {{{api.method}}}

{{#if api.description}}**Descrizione:**

{{{api.description}}}

{{/if}}

> RICHIESTA

{{#if api.http.pathParams}}**Parametri Percorso:**

| nome | valore | obbligatorio | descrizione |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Query:**

| nome | valore | obbligatorio | descrizione |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Intestazioni:**

| nome | valore | obbligatorio | descrizione |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Modulo:**

| nome | valore | obbligatorio | tipo | descrizione |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Corpo della Richiesta:**

| nome | tipo | descrizione |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**Esempio di Richiesta:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> RISPOSTA

**Corpo della Risposta:**

| nome | tipo | descrizione |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**Esempio di Risposta:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> INFORMAZIONI DI BASE

**Protocollo:** gRPC

**Servizio:** {{{api.grpc.serviceName}}}

**Metodo:** {{{api.grpc.methodName}}}

**Streaming:** {{{api.grpc.streamingType}}}

**Percorso Completo:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Descrizione:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> RICHIESTA

**Corpo della Richiesta:**

| nome | tipo | descrizione |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**Esempio di Richiesta:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> RISPOSTA

**Corpo della Risposta:**

| nome | tipo | descrizione |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**Esempio di Risposta:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
