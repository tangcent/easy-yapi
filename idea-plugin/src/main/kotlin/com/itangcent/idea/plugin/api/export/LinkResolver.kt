package com.itangcent.idea.plugin.api.export

interface LinkResolver {

    fun linkToClass(linkClass: Any): String?

    fun linkToMethod(linkMethod: Any): String?

    fun linkToProperty(linkField: Any): String?
}