package com.itangcent.idea.plugin.api.export

import com.itangcent.common.utils.SimpleExtensible

class Folder : SimpleExtensible {

    var name: String? = null

    var attr: String? = null

    constructor(name: String?) {
        this.name = name?.trim()
    }

    constructor(name: String?, attr: String?) {
        this.name = name?.trim()
        this.attr = attr
    }
}