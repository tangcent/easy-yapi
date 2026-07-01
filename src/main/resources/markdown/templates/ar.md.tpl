# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> الأساسي

**المسار:** {{{api.path}}}

**الطريقة:** {{{api.method}}}

{{#if api.description}}**الوصف:**

{{{api.description}}}

{{/if}}

> الطلب

{{#if api.http.pathParams}}**معاملات المسار:**

| الاسم | القيمة | مطلوب | الوصف |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**الاستعلام:**

| الاسم | القيمة | مطلوب | الوصف |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**الرؤوس:**

| الاسم | القيمة | مطلوب | الوصف |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**النموذج:**

| الاسم | القيمة | مطلوب | النوع | الوصف |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**نص الطلب:**

| الاسم | النوع | الوصف |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**مثال الطلب:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> الاستجابة

**النص:**

| الاسم | النوع | الوصف |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**مثال الاستجابة:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> الأساسي

**البروتوكول:** gRPC

**الخدمة:** {{{api.grpc.serviceName}}}

**الطريقة:** {{{api.grpc.methodName}}}

**البث:** {{{api.grpc.streamingType}}}

**المسار الكامل:** {{{api.grpc.fullPath}}}

{{#if api.description}}**الوصف:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> الطلب

**نص الطلب:**

| الاسم | النوع | الوصف |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**مثال الطلب:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> الاستجابة

**النص:**

| الاسم | النوع | الوصف |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**مثال الاستجابة:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
