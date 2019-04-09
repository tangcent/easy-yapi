package com.itangcent.idea.plugin.api.cache

import com.itangcent.common.model.Request

class FileApiCache {
    /**
     * path of file
     */
    public var file: String? = null

    /**
     * the lastModified time of file
     */
    public var lastModified: Long? = null

    /**
     * md5 of file
     */
    public var md5: String? = null

    /**
     * request in file
     */
    public var requests: List<RequestWithKey>? = null
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
    public var key: String? = null

    /**
     * resource of request was excluded
     */
    public var request: Request? = null
}