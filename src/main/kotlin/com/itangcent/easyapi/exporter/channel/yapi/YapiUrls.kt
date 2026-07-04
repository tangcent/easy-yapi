package com.itangcent.easyapi.exporter.channel.yapi

object YapiUrls {

    fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    fun cartUrl(baseUrl: String, projectId: String, catId: String): String {
        return "${normalizeBaseUrl(baseUrl)}/project/$projectId/interface/api/cat_$catId"
    }
}
