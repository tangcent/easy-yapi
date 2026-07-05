# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> PODSTAWOWE

**Ścieżka:** {{{api.path}}}

**Metoda:** {{{api.method}}}

{{#if api.description}}**Opis:**

{{{api.description}}}

{{/if}}

> ŻĄDANIE

{{#if api.http.pathParams}}**Parametry ścieżki:**

| nazwa | wartość | wymagane | opis |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Zapytanie:**

| nazwa | wartość | wymagane | opis |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Nagłówki:**

| nazwa | wartość | wymagane | opis |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Formularz:**

| nazwa | wartość | wymagane | typ | opis |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Treść żądania:**

| nazwa | typ | opis |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Przykład żądania:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> ODPOWIEDŹ

**Treść:**

| nazwa | typ | opis |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Przykład odpowiedzi:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> PODSTAWOWE

**Protokół:** gRPC

**Usługa:** {{{api.grpc.serviceName}}}

**Metoda:** {{{api.grpc.methodName}}}

**Strumieniowanie:** {{{api.grpc.streamingType}}}

**Pełna ścieżka:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Opis:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> ŻĄDANIE

**Treść żądania:**

| nazwa | typ | opis |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Przykład żądania:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> ODPOWIEDŹ

**Treść:**

| nazwa | typ | opis |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Przykład odpowiedzi:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
