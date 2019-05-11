package com.itangcent.common.model

import java.io.Serializable

class FormParam : Serializable {

    var name: String? = null

    var value: String? = null

    var desc: String? = null

    var required: Boolean? = null

    /**
     * text/file
     */
    var type: String? = null
}
