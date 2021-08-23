package com.itangcent.idea.utils

import com.google.gson.TypeAdapter
import com.google.gson.internal.LazilyParsedNumber
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

/**
 * support LazilyParsedNumber
 * write as raw number instead of {"value":number}
 */
class LazilyParsedNumberTypeAdapter : TypeAdapter<LazilyParsedNumber>() {

    @Throws(IOException::class)
    override fun read(reader: JsonReader): LazilyParsedNumber? {
        return when (reader.peek()) {
            JsonToken.STRING -> LazilyParsedNumber(reader.nextString())
            JsonToken.NUMBER -> {
                LazilyParsedNumber(reader.nextString())
            }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> throw IllegalStateException()
        }
    }

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: LazilyParsedNumber?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.jsonValue(value.toString())
    }
}
