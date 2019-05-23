/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itangcent.idea.utils

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

/**
 * By default ObjectTypeAdapter,all number are deserialized to double.
 * It's not necessary to keep long to long,float to float.
 * But shouldn't deserialize short/int/long to double
 * So fix it.
 */
class NumberFixedObjectTypeAdapter : TypeAdapter<Any> {

    private var gson: Gson? = null

    constructor(gson: Gson) : super() {
        this.gson = gson
    }

    constructor() : super()

    fun setGson(gson: Gson) {
        this.gson = gson
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Any? {
        val token = reader.peek()
        when (token) {
            JsonToken.BEGIN_ARRAY -> {
                val list = ArrayList<Any?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(read(reader))
                }
                reader.endArray()
                return list
            }

            JsonToken.BEGIN_OBJECT -> {
                val map = HashMap<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    map[reader.nextName()] = read(reader)
                }
                reader.endObject()
                return map
            }

            JsonToken.STRING -> return reader.nextString()

            JsonToken.NUMBER -> {
                //read as String
                val numberStr = reader.nextString()

                // deserialized as double if ./e/E be found
                if (numberStr.contains(".") || numberStr.contains("e")
                        || numberStr.contains("E")
                ) {
                    return numberStr.toDouble()
                }

                return try {
                    numberStr.toInt()
                } catch (e: Exception) {
                    numberStr.toLong()
                }
            }

            JsonToken.BOOLEAN -> return reader.nextBoolean()

            JsonToken.NULL -> {
                reader.nextNull()
                return null
            }

            else -> throw IllegalStateException()
        }
    }

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Any?) {
        if (value == null) {
            out.nullValue()
            return
        }

        val typeAdapter = gson!!.getAdapter(value.javaClass) as TypeAdapter<Any>
        if (typeAdapter is NumberFixedObjectTypeAdapter) {
            out.beginObject()
            out.endObject()
            return
        }

        typeAdapter.write(out, value)
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        val FACTORY: TypeAdapterFactory = object : TypeAdapterFactory {
            override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
                return if (type.rawType == Any::class.java) {
                    NumberFixedObjectTypeAdapter(gson) as TypeAdapter<T>
                } else null
            }
        }
    }
}
