package com.itangcent.idea.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError


object JacksonUtils : Log() {

    private val objectMapper: ObjectMapper = ObjectMapper()

    init {
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL)
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    fun toJson(bean: Any?): String {
        if (bean == null) {
            return "null"
        }
        return "${bean::class.java.name},${objectMapper.writeValueAsString(bean)}"
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> fromJson(json: String): T? {
        if (json.startsWith("{\"c\":")) {
            //old version cache
            return null
        }
        if (json == "null") {
            return null
        }
        val split = json.indexOf(',')
        return try {
            objectMapper.readValue(
                GsonExUtils.resolveGsonLazily(json.substring(split + 1)),
                Class.forName(json.substring(0, split)) as Class<T>
            )
        } catch (e: Exception) {
            LOG.traceError("failed parse json: [$json]", e)
            null
        }
    }
}