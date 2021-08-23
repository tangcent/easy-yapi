package com.itangcent.utils

import com.itangcent.common.utils.GsonUtils

object DefaultJsonSupport : JsonSupport {
    override fun toJson(obj: Any?): String {
        return GsonUtils.toJson(obj)
    }
}