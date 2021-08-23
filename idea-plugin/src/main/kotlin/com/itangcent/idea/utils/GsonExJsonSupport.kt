package com.itangcent.idea.utils

import com.itangcent.utils.JsonSupport

class GsonExJsonSupport : JsonSupport {
    override fun toJson(obj: Any?): String {
        return GsonExUtils.toJson(obj)
    }
}