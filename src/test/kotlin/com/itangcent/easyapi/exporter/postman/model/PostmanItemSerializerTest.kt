package com.itangcent.easyapi.exporter.postman.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class PostmanItemSerializerTest {

    private val gson = postmanGson(prettyPrint = false)

    @Test
    fun testSerialize_apiItem() {
        val item = PostmanItem(
            name = "Get Users",
            request = PostmanRequest(
                method = "GET",
                url = PostmanUrl(raw = "http://localhost/users")
            )
        )
        val json = gson.toJson(item)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertEquals("Get Users", obj.get("name").asString)
        assertTrue(obj.has("request"))
        assertFalse(obj.has("item"))
    }

    @Test
    fun testSerialize_folderItem() {
        val child = PostmanItem(
            name = "Get User",
            request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "/users/1"))
        )
        val folder = PostmanItem(name = "Users", item = listOf(child))
        val json = gson.toJson(folder)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertEquals("Users", obj.get("name").asString)
        assertTrue(obj.has("item"))
        assertFalse(obj.has("request"))
    }

    @Test
    fun testSerialize_apiItem_withResponse() {
        val item = PostmanItem(
            name = "Get Users",
            request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "/users")),
            response = listOf(
                PostmanResponse(name = "200 OK", code = 200, body = "{}")
            )
        )
        val json = gson.toJson(item)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.has("response"))
        assertEquals(1, obj.getAsJsonArray("response").size())
    }

    @Test
    fun testSerialize_apiItem_emptyResponse() {
        val item = PostmanItem(
            name = "Get Users",
            request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "/users"))
        )
        val json = gson.toJson(item)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.has("response"))
        assertEquals(0, obj.getAsJsonArray("response").size())
    }

    @Test
    fun testSerialize_apiItem_withEvent() {
        val item = PostmanItem(
            name = "Test",
            request = PostmanRequest(method = "POST", url = PostmanUrl(raw = "/test")),
            event = listOf(
                PostmanEvent("prerequest", PostmanScript(exec = listOf("console.log('pre')")))
            )
        )
        val json = gson.toJson(item)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.has("event"))
    }

    @Test
    fun testSerialize_folder_withDescription() {
        val folder = PostmanItem(name = "Folder", description = "A folder")
        val json = gson.toJson(folder)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertEquals("A folder", obj.get("description").asString)
    }

    @Test
    fun testSerialize_folder_noDescription() {
        val folder = PostmanItem(name = "Folder")
        val json = gson.toJson(folder)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertFalse(obj.has("description"))
    }

    @Test
    fun testPostmanGson_prettyPrint() {
        val pretty = postmanGson(prettyPrint = true)
        val item = PostmanItem(name = "Test", request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "/t")))
        val json = pretty.toJson(item)
        assertTrue(json.contains("\n"))
    }

    @Test
    fun testPostmanGson_noPrettyPrint() {
        val compact = postmanGson(prettyPrint = false)
        val item = PostmanItem(name = "Test", request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "/t")))
        val json = compact.toJson(item)
        assertFalse(json.contains("\n"))
    }
}
