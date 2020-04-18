package com.itangcent.http

import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.GsonUtils

fun Cookie.json(): String {
    return GsonUtils.toJson(this.mutable())
}

fun Cookie.mutable(): MutableCookie {
    if (this is MutableCookie) {
        return this
    } else {
        val basicCookie = BasicCookie()
        basicCookie.setName(this.getName())
        basicCookie.setValue(this.getValue())
        basicCookie.setComment(this.getComment())
        basicCookie.setCommentURL(this.getCommentURL())
        basicCookie.setDomain(this.getDomain())
        basicCookie.setExpiryDate(this.getExpiryDate())
        basicCookie.setPath(this.getPath())
        basicCookie.setPorts(this.getPorts())
        basicCookie.setSecure(this.isSecure())
        basicCookie.setVersion(this.getVersion())
        return basicCookie
    }
}

@ScriptTypeName("cookie")
class BasicCookie : MutableCookie {

    private var name: String? = null
    override fun getName(): String? = name
    override fun setName(name: String?) {
        this.name = name
    }

    private var value: String? = null
    override fun getValue(): String? = value
    override fun setValue(value: String?) {
        this.value = value
    }

    private var comment: String? = null
    override fun getComment(): String? = comment
    override fun setComment(comment: String?) {
        this.comment = comment
    }

    private var commentURL: String? = null
    override fun getCommentURL(): String? = commentURL
    override fun setCommentURL(commentURL: String?) {
        this.commentURL = commentURL
    }


    private var domain: String? = null
    override fun getDomain(): String? = domain
    override fun setDomain(domain: String?) {
        this.domain = domain
    }

    private var path: String? = null
    override fun getPath(): String? = path
    override fun setPath(path: String?) {
        this.path = path
    }

    private var version: Int? = null
    override fun getVersion(): Int? = version
    override fun setVersion(version: Int?) {
        this.version = version
    }

    private var secure: Boolean = false
    override fun isSecure(): Boolean = secure
    override fun setSecure(secure: Boolean) {
        this.secure = secure
    }

    private var ports: IntArray? = null
    override fun getPorts(): IntArray? = ports
    override fun setPorts(ports: IntArray?) {
        this.ports = ports
    }

    private var expiryDate: Long? = null
    override fun getExpiryDate(): Long? = expiryDate
    override fun setExpiryDate(expiryDate: Long?) {
        this.expiryDate = expiryDate
    }

    /**
     * Returns `false` if the cookie should be discarded at the end
     * of the "session"; `true` otherwise.
     *
     * @return `false` if the cookie should be discarded at the end
     * of the "session"; `true` otherwise
     */
    override fun isPersistent(): Boolean {
        return expiryDate != null
    }

    companion object {
        fun fromJson(json: String): MutableCookie {
            return GsonUtils.fromJson(json, BasicCookie::class)
        }
    }
}