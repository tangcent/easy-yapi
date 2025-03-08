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

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    override fun getName(): String? = name

    override fun setName(name: String?) {
        this.name = name
    }

    private var value: String? = null

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    override fun getValue(): String? = value
    override fun setValue(value: String?) {
        this.value = value
    }

    private var comment: String? = null

    /**
     * Returns the comment describing the purpose of this cookie, or
     * {@code null} if no such comment has been defined.
     * Compatible only.Obsolete.
     * @return comment
     */
    @Deprecated("Obsolete")
    override fun getComment(): String? = comment

    @Deprecated("Obsolete")
    override fun setComment(comment: String?) {
        this.comment = comment
    }

    private var commentURL: String? = null
    @Deprecated("Obsolete")
    override fun getCommentURL(): String? = commentURL
    @Deprecated("Obsolete")
    override fun setCommentURL(commentURL: String?) {
        this.commentURL = commentURL
    }


    private var domain: String? = null

    /**
     * Returns domain attribute of the cookie. The value of the Domain
     * attribute specifies the domain for which the cookie is valid.
     *
     * @return the value of the domain attribute.
     */
    override fun getDomain(): String? = domain
    override fun setDomain(domain: String?) {
        this.domain = domain
    }

    private var path: String? = null

    /**
     * Returns the path attribute of the cookie. The value of the Path
     * attribute specifies the subset of URLs on the origin server to which
     * this cookie applies.
     *
     * @return The value of the path attribute.
     */
    override fun getPath(): String? = path

    override fun setPath(path: String?) {
        this.path = path
    }

    private var version: Int? = null

    /**
     * Returns the version of the cookie specification to which this
     * cookie conforms.
     * Compatible only.Obsolete.
     *
     * @return the version of the cookie.
     */
    @Deprecated("Obsolete")
    override fun getVersion(): Int? = version
    @Deprecated("Obsolete")
    override fun setVersion(version: Int?) {
        this.version = version
    }

    private var secure: Boolean = false

    /**
     * Indicates whether this cookie requires a secure connection.
     *
     * @return  {@code true} if this cookie should only be sent
     *          over secure connections, {@code false} otherwise.
     */
    override fun isSecure(): Boolean = secure
    override fun setSecure(secure: Boolean) {
        this.secure = secure
    }

    private var ports: IntArray? = null
    @Deprecated("Obsolete")
    override fun getPorts(): IntArray? = ports
    @Deprecated("Obsolete")
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