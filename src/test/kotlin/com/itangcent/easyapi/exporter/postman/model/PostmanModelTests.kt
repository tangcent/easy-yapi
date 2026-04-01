package com.itangcent.easyapi.exporter.postman.model

import org.junit.Assert.*
import org.junit.Test

class PostmanVariableTest {

    @Test
    fun testPostmanVariableCreation() {
        val variable = PostmanVariable(
            key = "baseUrl",
            value = "http://api.example.com",
            type = "string",
            description = "API base URL"
        )
        assertEquals("baseUrl", variable.key)
        assertEquals("http://api.example.com", variable.value)
        assertEquals("string", variable.type)
        assertEquals("API base URL", variable.description)
    }

    @Test
    fun testPostmanVariableWithDefaults() {
        val variable = PostmanVariable(key = "token", value = "abc123")
        assertEquals("token", variable.key)
        assertEquals("abc123", variable.value)
        assertEquals("string", variable.type)
        assertNull(variable.description)
    }

    @Test
    fun testPostmanVariableEquality() {
        val var1 = PostmanVariable("key", "value", "string", "desc")
        val var2 = PostmanVariable("key", "value", "string", "desc")
        assertEquals(var1, var2)
    }

    @Test
    fun testPostmanVariableCopy() {
        val original = PostmanVariable("key", "value")
        val copy = original.copy(value = "newValue")
        assertEquals("newValue", copy.value)
        assertEquals("value", original.value)
    }
}

class PostmanResponseTest {

    @Test
    fun testPostmanResponseCreation() {
        val response = PostmanResponse(
            name = "Success",
            status = "OK",
            code = 200,
            body = "{\"id\": 1}",
            _postman_previewlanguage = "json"
        )
        assertEquals("Success", response.name)
        assertEquals("OK", response.status)
        assertEquals(200, response.code)
        assertEquals("{\"id\": 1}", response.body)
        assertEquals("json", response._postman_previewlanguage)
    }

    @Test
    fun testPostmanResponseWithDefaults() {
        val response = PostmanResponse(name = "Default")
        assertEquals("Default", response.name)
        assertNull(response.originalRequest)
        assertNull(response.status)
        assertNull(response.code)
        assertTrue(response.header.isEmpty())
        assertNull(response.body)
        assertEquals("json", response._postman_previewlanguage)
        assertTrue(response.cookie.isEmpty())
    }

    @Test
    fun testPostmanResponseEquality() {
        val resp1 = PostmanResponse("Test", code = 200)
        val resp2 = PostmanResponse("Test", code = 200)
        assertEquals(resp1, resp2)
    }
}

class CollectionInfoTest {

    @Test
    fun testCollectionInfoCreation() {
        val info = CollectionInfo(
            name = "My API",
            schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
            description = "API collection",
            _postman_id = "12345"
        )
        assertEquals("My API", info.name)
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json", info.schema)
        assertEquals("API collection", info.description)
        assertEquals("12345", info._postman_id)
    }

    @Test
    fun testCollectionInfoWithDefaults() {
        val info = CollectionInfo(name = "Test API")
        assertEquals("Test API", info.name)
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json", info.schema)
        assertNull(info.description)
        assertNull(info._postman_id)
    }

    @Test
    fun testCollectionInfoEquality() {
        val info1 = CollectionInfo("API", _postman_id = "123")
        val info2 = CollectionInfo("API", _postman_id = "123")
        assertEquals(info1, info2)
    }
}

class PostmanCollectionTest {

    @Test
    fun testPostmanCollectionCreation() {
        val info = CollectionInfo(name = "Test API")
        val collection = PostmanCollection(
            info = info,
            item = emptyList(),
            event = emptyList(),
            variable = emptyList()
        )
        assertEquals(info, collection.info)
        assertTrue(collection.item.isEmpty())
        assertTrue(collection.event.isEmpty())
        assertTrue(collection.variable.isEmpty())
    }

    @Test
    fun testPostmanCollectionWithItems() {
        val info = CollectionInfo(name = "Test API")
        val item = PostmanItem(name = "Get Users")
        val collection = PostmanCollection(info = info, item = listOf(item))
        assertEquals(1, collection.item.size)
        assertEquals("Get Users", collection.item[0].name)
    }
}

class PostmanItemTest {

    @Test
    fun testPostmanItemAsFolder() {
        val childItem = PostmanItem(name = "Get User")
        val folder = PostmanItem(
            name = "Users",
            item = listOf(childItem)
        )
        assertEquals("Users", folder.name)
        assertEquals(1, folder.item.size)
        assertNull(folder.request)
    }

    @Test
    fun testPostmanItemAsRequest() {
        val request = PostmanRequest(
            method = "GET",
            url = PostmanUrl(raw = "http://api.example.com/users")
        )
        val item = PostmanItem(
            name = "Get Users",
            request = request
        )
        assertEquals("Get Users", item.name)
        assertNotNull(item.request)
        assertEquals("GET", item.request?.method)
        assertTrue(item.item.isEmpty())
    }
}

class PostmanRequestTest {

    @Test
    fun testPostmanRequestCreation() {
        val request = PostmanRequest(
            method = "POST",
            header = listOf(PostmanHeader("Content-Type", "application/json")),
            body = PostmanBody(mode = "raw", raw = "{\"name\": \"test\"}"),
            url = PostmanUrl(raw = "http://api.example.com/users"),
            description = "Create user"
        )
        assertEquals("POST", request.method)
        assertEquals(1, request.header.size)
        assertNotNull(request.body)
        assertEquals("Create user", request.description)
    }
}

class PostmanUrlTest {

    @Test
    fun testPostmanUrlCreation() {
        val url = PostmanUrl(
            raw = "http://api.example.com/users/123",
            host = listOf("api", "example", "com"),
            path = listOf("users", "123"),
            query = listOf(PostmanQuery("filter", "active")),
            variable = listOf(PostmanPathVariable("id", "123"))
        )
        assertEquals("http://api.example.com/users/123", url.raw)
        assertEquals(3, url.host.size)
        assertEquals(2, url.path.size)
        assertEquals(1, url.query.size)
        assertEquals(1, url.variable.size)
    }
}

class PostmanHeaderTest {

    @Test
    fun testPostmanHeaderCreation() {
        val header = PostmanHeader(
            key = "Authorization",
            value = "Bearer token123",
            type = "secret",
            description = "Auth token",
            name = "Authorization"
        )
        assertEquals("Authorization", header.key)
        assertEquals("Bearer token123", header.value)
        assertEquals("secret", header.type)
        assertEquals("Auth token", header.description)
        assertEquals("Authorization", header.name)
    }

    @Test
    fun testPostmanHeaderWithDefaults() {
        val header = PostmanHeader(key = "Content-Type", value = "application/json")
        assertEquals("Content-Type", header.key)
        assertEquals("application/json", header.value)
        assertEquals("text", header.type)
        assertNull(header.description)
        assertNull(header.name)
    }
}

class PostmanBodyTest {

    @Test
    fun testPostmanBodyRaw() {
        val body = PostmanBody(
            mode = "raw",
            raw = "{\"name\": \"test\"}",
            options = mapOf("raw" to mapOf("language" to "json"))
        )
        assertEquals("raw", body.mode)
        assertEquals("{\"name\": \"test\"}", body.raw)
        assertNotNull(body.options)
    }

    @Test
    fun testPostmanBodyFormdata() {
        val body = PostmanBody(
            mode = "formdata",
            formdata = listOf(
                PostmanFormParam("file", "", "file", src = "/path/to/file")
            )
        )
        assertEquals("formdata", body.mode)
        assertEquals(1, body.formdata?.size)
    }
}

class PostmanQueryTest {

    @Test
    fun testPostmanQueryCreation() {
        val query = PostmanQuery(
            key = "page",
            value = "1",
            equals = true,
            description = "Page number",
            disabled = false
        )
        assertEquals("page", query.key)
        assertEquals("1", query.value)
        assertTrue(query.equals)
        assertEquals("Page number", query.description)
        assertFalse(query.disabled!!)
    }
}

class PostmanFormParamTest {

    @Test
    fun testPostmanFormParamText() {
        val param = PostmanFormParam(
            key = "username",
            value = "john",
            type = "text"
        )
        assertEquals("username", param.key)
        assertEquals("john", param.value)
        assertEquals("text", param.type)
    }

    @Test
    fun testPostmanFormParamFile() {
        val param = PostmanFormParam(
            key = "avatar",
            value = "",
            type = "file",
            src = "/path/to/avatar.jpg"
        )
        assertEquals("avatar", param.key)
        assertEquals("file", param.type)
        assertEquals("/path/to/avatar.jpg", param.src)
    }
}

class PostmanPathVariableTest {

    @Test
    fun testPostmanPathVariableCreation() {
        val pathVar = PostmanPathVariable(
            key = "id",
            value = "123",
            description = "User ID"
        )
        assertEquals("id", pathVar.key)
        assertEquals("123", pathVar.value)
        assertEquals("User ID", pathVar.description)
    }
}

class PostmanEventTest {

    @Test
    fun testPostmanEventCreation() {
        val event = PostmanEvent(
            listen = "prerequest",
            script = PostmanScript(
                type = "text/javascript",
                exec = listOf("pm.environment.set('token', 'abc');")
            )
        )
        assertEquals("prerequest", event.listen)
        assertEquals("text/javascript", event.script.type)
        assertEquals(1, event.script.exec.size)
    }
}

class PostmanScriptTest {

    @Test
    fun testPostmanScriptCreation() {
        val script = PostmanScript(
            type = "text/javascript",
            exec = listOf("console.log('test');", "pm.test('Status is 200', function() {});")
        )
        assertEquals("text/javascript", script.type)
        assertEquals(2, script.exec.size)
    }

    @Test
    fun testPostmanScriptWithDefaults() {
        val script = PostmanScript()
        assertEquals("text/javascript", script.type)
        assertTrue(script.exec.isEmpty())
    }
}
