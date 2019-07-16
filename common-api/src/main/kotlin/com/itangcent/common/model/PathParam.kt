package com.itangcent.common.model

import java.io.Serializable

class PathParam : Extensible(), Serializable {
    var name: String? = null

    var value: String? = null

    var desc: String? = null
}
