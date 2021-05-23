package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Singleton
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.utils.ExtensibleKit.fromJson

@Singleton
class DefaultAdditionalParseHelper : AdditionalParseHelper {

    override fun parseHeaderFromJson(headerStr: String): Header = Header::class.fromJson(headerStr)

    override fun parseParamFromJson(paramStr: String): Param = Param::class.fromJson(paramStr)
}