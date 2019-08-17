package com.itangcent.idea.plugin.api.export

import com.google.inject.ImplementedBy

@ImplementedBy(DefaultLinkResolver::class)
interface LinkResolver {

    fun linkToClass(linkClass: Any): String?

    fun linkToMethod(linkMethod: Any): String?

    fun linkToProperty(linkField: Any): String?
}