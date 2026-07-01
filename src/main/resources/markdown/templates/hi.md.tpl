# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> मूल

**पथ:** {{{api.path}}}

**विधि:** {{{api.method}}}

{{#if api.description}}**विवरण:**

{{{api.description}}}

{{/if}}

> अनुरोध

{{#if api.http.pathParams}}**पथ पैरामीटर:**

| नाम | मान | आवश्यक | विवरण |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**क्वेरी:**

| नाम | मान | आवश्यक | विवरण |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**हेडर:**

| नाम | मान | आवश्यक | विवरण |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**फ़ॉर्म:**

| नाम | मान | आवश्यक | प्रकार | विवरण |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**अनुरोध निकाय:**

| नाम | प्रकार | विवरण |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**अनुरोध उदाहरण:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> प्रतिक्रिया

**निकाय:**

| नाम | प्रकार | विवरण |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**प्रतिक्रिया उदाहरण:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> मूल

**प्रोटोकॉल:** gRPC

**सेवा:** {{{api.grpc.serviceName}}}

**विधि:** {{{api.grpc.methodName}}}

**स्ट्रीमिंग:** {{{api.grpc.streamingType}}}

**पूर्ण पथ:** {{{api.grpc.fullPath}}}

{{#if api.description}}**विवरण:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> अनुरोध

**अनुरोध निकाय:**

| नाम | प्रकार | विवरण |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**अनुरोध उदाहरण:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> प्रतिक्रिया

**निकाय:**

| नाम | प्रकार | विवरण |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**प्रतिक्रिया उदाहरण:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
