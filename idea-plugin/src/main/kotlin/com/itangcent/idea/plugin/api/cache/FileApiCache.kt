package com.itangcent.idea.plugin.api.cache

import com.itangcent.common.model.Request

class FileApiCache {
    /**
     * path of file
     */
    var file: String? = null

    /**
     * the lastModified time of file
     */
    var lastModified: Long? = null

    /**
     * md5 of file
     */
    var md5: String? = null

    /**
     * request in file
     */
    var requests: List<RequestWithKey>? = null
}

class RequestWithKey {

    constructor(key: String?, request: Request?) {
        this.key = key
        this.request = request
    }

    constructor()

    /**
     * maybe full name of method
     *
     * @see com.itangcent.intellij.psi.PsiClassUtils.fullNameOfMethod
     */
    var key: String? = null

    /**
     * resource of request was excluded
     */
    var request: Request? = null
}