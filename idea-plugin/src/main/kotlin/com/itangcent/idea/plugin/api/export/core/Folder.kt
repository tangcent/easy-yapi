package com.itangcent.idea.plugin.api.export.core

import com.itangcent.common.utils.SimpleExtensible

class Folder : SimpleExtensible {

    val name: String?

    val attr: String?

    constructor(name: String?) {
        this.name = name?.trim()
        this.attr = null
    }

    constructor(name: String?, attr: String?) {
        this.name = name?.trim()
        this.attr = attr
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Folder

        if (name != other.name) return false
        if (attr != other.attr) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (attr?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Folder(name=$name, attr=$attr)"
    }
}