package com.itangcent.idea.plugin.format

import com.google.inject.Singleton
import com.itangcent.http.RequestUtils

/**
 * Implementation of [com.itangcent.idea.plugin.format.MessageFormatter]
 * that can write the object as JSON string.
 *
 * @author tangcent
 */
@Singleton
class SimpleJsonFormatter : MessageFormatter {
    override fun format(obj: Any?, desc: String?): String {
        return obj?.let { RequestUtils.parseRawBody(it) } ?: ""
    }
}