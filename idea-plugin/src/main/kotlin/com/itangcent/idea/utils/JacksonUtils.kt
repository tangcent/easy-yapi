package com.itangcent.idea.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.databind.type.TypeFactory


object JacksonUtils {

    private val objectMapper: ObjectMapper = ObjectMapper()

    private val javaType: JavaType = TypeFactory.defaultInstance().constructType(Any::class.java)

    init {
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL)
    }

    fun toJson(bean: Any?): String {
        if (bean == null) {
            return "null"
        }
        return "${bean::class.java.name},${objectMapper.writeValueAsString(bean)}"
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> fromJson(json: String): T? {
        if (json == "null") {
            return null
        }
        val split = json.indexOf(',')
        return objectMapper.readValue(
                json.substring(split + 1),
                Class.forName(json.substring(0, split)) as Class<T>)
    }
}

