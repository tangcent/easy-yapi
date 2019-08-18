package com.itangcent.common.model

import java.io.Serializable

open class Doc : Extensible(), Serializable {

    var resource: Any? = null

    var name: String? = null

    var desc: String? = null
}