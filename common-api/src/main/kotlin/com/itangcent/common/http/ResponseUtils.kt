package com.itangcent.common.http

import com.itangcent.common.utils.firstOrNull

object ResponseUtils {

    fun getHeaderFileName(response: HttpResponse): String? {
        val dispositionHeader = response.getHeader("Content-Disposition")
        if (dispositionHeader.isNullOrBlank()) return null
        var fileName = dispositionHeader.substringAfter("filename=", "")
        val candidates = fileName.split("; ")
        for (candidate in candidates) {
            fileName = candidate.substringAfter("filename=")
                    .removeSurrounding("\"")
            if (fileName.isNotBlank()) return fileName
        }

        return null
    }
}

fun HttpResponse.getHeaderFileName(): String? {
    return ResponseUtils.getHeaderFileName(this)
}

fun HttpResponse.getHeader(name: String): String? {
    return this.getHeader()
            ?.stream()
            ?.filter { it.first.equals(name, true) }
            ?.map { it.second }
            ?.firstOrNull()
}