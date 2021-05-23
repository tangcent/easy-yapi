package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param

@ImplementedBy(DefaultAdditionalParseHelper::class)
interface AdditionalParseHelper {

    fun parseHeaderFromJson(headerStr: String): Header

    fun parseParamFromJson(paramStr: String): Param
}