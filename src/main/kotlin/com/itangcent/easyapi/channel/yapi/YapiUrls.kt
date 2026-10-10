package com.itangcent.easyapi.channel.yapi

object YapiUrls {

    fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    fun cartUrl(baseUrl: String, projectId: String, catId: String): String {
        return "${normalizeBaseUrl(baseUrl)}/project/$projectId/interface/api/cat_$catId"
    }

    /**
     * Builds the YAPI route of a single API, so a success notification can jump straight to the
     * endpoint that was just exported instead of the category listing.
     */
    fun apiUrl(baseUrl: String, projectId: String, apiId: String): String {
        return "${normalizeBaseUrl(baseUrl)}/project/$projectId/interface/api/$apiId"
    }
}
