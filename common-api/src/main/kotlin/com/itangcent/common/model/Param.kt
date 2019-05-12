package com.itangcent.common.model

import java.io.Serializable

class Param: Serializable {
    var name: String? = null

    var value: Any? = null

    var desc: String? = null

    var required: Boolean? = null
}
