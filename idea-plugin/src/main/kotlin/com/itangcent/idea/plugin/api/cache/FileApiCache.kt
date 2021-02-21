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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileApiCache

        if (file != other.file) return false
        if (lastModified != other.lastModified) return false
        if (md5 != other.md5) return false
        if (requests != other.requests) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file?.hashCode() ?: 0
        result = 31 * result + (lastModified?.hashCode() ?: 0)
        result = 31 * result + (md5?.hashCode() ?: 0)
        result = 31 * result + (requests?.hashCode() ?: 0)
        return result
    }


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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestWithKey

        if (key != other.key) return false
        if (request != other.request) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (request?.hashCode() ?: 0)
        return result
    }


}