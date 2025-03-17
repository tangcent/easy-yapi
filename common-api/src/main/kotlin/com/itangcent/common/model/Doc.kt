package com.itangcent.common.model

import com.itangcent.common.utils.SimpleExtensible
import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Instances of the [Doc] represent a document(Http or RPC) in the project.
 */
open class Doc : SimpleExtensible(), Serializable {

    /**
     * The element associated the origin code.
     */
    @Transient
    @Expose(serialize = false, deserialize = false)
    var resource: Any? = null

    /**
     * Returns the name of the doc.
     */
    var name: String? = null

    /**
     * Returns the description of the doc.
     * Explain what this document represented in a human readable way.
     */
    var desc: String? = null
}