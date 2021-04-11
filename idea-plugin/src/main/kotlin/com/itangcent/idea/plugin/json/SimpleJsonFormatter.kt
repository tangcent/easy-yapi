package com.itangcent.idea.plugin.json

import com.google.inject.Singleton
import com.itangcent.http.RequestUtils

@Singleton
class SimpleJsonFormatter : JsonFormatter {
    override fun format(obj: Any?, desc: String?): String {
        return obj?.let { RequestUtils.parseRawBody(it) } ?: ""
    }
}