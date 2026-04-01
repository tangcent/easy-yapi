package com.itangcent.easyapi.exporter.postman.model

import com.google.gson.*
import java.lang.reflect.Type

/**
 * Custom Gson serializer for [PostmanItem] that correctly distinguishes
 * between folder items and API (request) items in the Postman collection format.
 *
 * Postman identifies folders vs API items by the presence of fields:
 * - Folder: has "item" array, no "request"
 * - API item: has "request" object, no "item"
 *
 * Without this serializer, Gson would serialize empty "item: []" on API items,
 * causing Postman to treat them as empty folders instead of API endpoints.
 */
class PostmanItemSerializer : JsonSerializer<PostmanItem> {
    override fun serialize(src: PostmanItem, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("name", src.name)

        val isApiItem = src.request != null

        if (isApiItem) {
            // API item: serialize request, response, event — but NOT item
            obj.add("request", context.serialize(src.request))

            if (src.response.isNotEmpty()) {
                obj.add("response", context.serialize(src.response))
            } else {
                obj.add("response", JsonArray())
            }

            if (src.event.isNotEmpty()) {
                obj.add("event", context.serialize(src.event))
            }
        } else {
            // Folder item: serialize item (sub-items), description, event — but NOT request
            if (src.description != null) {
                obj.addProperty("description", src.description)
            }

            obj.add("item", context.serialize(src.item))

            if (src.event.isNotEmpty()) {
                obj.add("event", context.serialize(src.event))
            }
        }

        return obj
    }
}

/**
 * Creates a Gson instance configured for proper Postman collection serialization.
 */
fun postmanGson(prettyPrint: Boolean = true): Gson {
    val builder = GsonBuilder()
        .registerTypeAdapter(PostmanItem::class.java, PostmanItemSerializer())
    if (prettyPrint) {
        builder.setPrettyPrinting()
    }
    return builder.create()
}
