package com.itangcent.common.model

import com.itangcent.common.utils.SimpleExtensible
import java.io.Serializable

class PathParam : SimpleExtensible(), Serializable {
    var name: String? = null

    var value: String? = null

    var desc: String? = null
}
