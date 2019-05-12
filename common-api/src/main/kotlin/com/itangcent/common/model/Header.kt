package com.itangcent.common.model

import java.io.Serializable

class Header: Serializable {
    var name: String? = null

    var value: String? = null

    var desc: String? = null

    var required: Boolean? = null

    var example: String? = null
}