package com.itangcent.idea.plugin.api.cache

import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.*
import com.itangcent.utils.setExts

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

    override fun toString(): String {
        return "FileApiCache(file=$file, lastModified=$lastModified, md5=$md5, requests=$requests)"
    }
}

class RequestWithKey {

    constructor(key: String?, request: Request?) {
        this.key = key
        if (request == null) return
        this.path = request.path?.urls()
        this.method = request.method
        this.headers = request.headers
        this.paths = request.paths
        this.querys = request.querys
        this.formParams = request.formParams
        this.bodyType = request.bodyType
        this.body = request.body
        this.bodyAttr = request.bodyAttr
        this.response = request.response
        this.name = request.name
        this.desc = request.desc
        this.ext = request.exts()
    }

    constructor()

    /**
     * maybe full name of method
     *
     * @see com.itangcent.intellij.psi.PsiClassUtils.fullNameOfMethod
     */
    var key: String? = null

    var path: Array<String>? = null

    /**
     * The HTTP method.
     *
     * @see HttpMethod
     */
    var method: String? = null

    /**
     * All of the headers.
     */
    var headers: MutableList<Header>? = null

    var paths: MutableList<PathParam>? = null

    var querys: MutableList<Param>? = null

    var formParams: MutableList<FormParam>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    /**
     * The description of [body] if it is present.
     */
    var bodyAttr: String? = null

    var response: MutableList<Response>? = null

    /**
     * Returns the name of the doc.
     */
    var name: String? = null

    /**
     * Returns the description of the doc.
     * Explain what this document represented in a human readable way.
     */
    var desc: String? = null

    var ext: Map<String, Any?>? = null

    fun request(): Request {
        val request = Request()
        request.path = this.path?.let { URL.of(*it) }
        request.method = this.method
        request.headers = this.headers
        request.paths = this.paths
        request.querys = this.querys
        request.formParams = this.formParams
        request.bodyType = this.bodyType
        request.body = this.body
        request.bodyAttr = this.bodyAttr
        request.response = this.response
        request.name = this.name
        request.desc = this.desc
        this.ext?.let { request.setExts(it) }
        return request
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestWithKey

        if (key != other.key) return false
        if (path != null) {
            if (other.path == null) return false
            if (!path!!.contentEquals(other.path!!)) return false
        } else if (other.path != null) return false
        if (method != other.method) return false
        if (headers != other.headers) return false
        if (paths != other.paths) return false
        if (querys != other.querys) return false
        if (formParams != other.formParams) return false
        if (bodyType != other.bodyType) return false
        if (body != other.body) return false
        if (bodyAttr != other.bodyAttr) return false
        if (response != other.response) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (ext != other.ext) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (path?.contentHashCode() ?: 0)
        result = 31 * result + (method?.hashCode() ?: 0)
        result = 31 * result + (headers?.hashCode() ?: 0)
        result = 31 * result + (paths?.hashCode() ?: 0)
        result = 31 * result + (querys?.hashCode() ?: 0)
        result = 31 * result + (formParams?.hashCode() ?: 0)
        result = 31 * result + (bodyType?.hashCode() ?: 0)
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (bodyAttr?.hashCode() ?: 0)
        result = 31 * result + (response?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (desc?.hashCode() ?: 0)
        result = 31 * result + (ext?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "RequestWithKey(key=$key, path=${path?.contentToString()}, method=$method, headers=$headers, paths=$paths, querys=$querys, formParams=$formParams, bodyType=$bodyType, body=$body, bodyAttr=$bodyAttr, response=$response, name=$name, desc=$desc, ext=$ext)"
    }
}