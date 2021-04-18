package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.idea.plugin.api.export.core.DefaultLinkResolver

@ImplementedBy(DefaultLinkResolver::class)
interface LinkResolver {

    fun linkToClass(linkClass: Any): String?

    fun linkToMethod(linkMethod: Any): String?

    fun linkToProperty(linkField: Any): String?
}