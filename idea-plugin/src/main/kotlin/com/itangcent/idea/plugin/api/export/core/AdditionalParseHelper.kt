package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.idea.plugin.api.export.AdditionalField

@ImplementedBy(DefaultAdditionalParseHelper::class)
interface AdditionalParseHelper {

    fun parseHeaderFromJson(headerStr: String): Header

    fun parseParamFromJson(paramStr: String): Param

    fun parseFieldFromJson(paramStr: String): AdditionalField
}