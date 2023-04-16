package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.idea.plugin.api.export.AdditionalField
import com.itangcent.idea.plugin.api.export.core.AdditionalParseHelper
import com.itangcent.utils.ExtensibleKit.fromJson

class YapiAdditionalParseHelper : AdditionalParseHelper {

    override fun parseHeaderFromJson(headerStr: String) = Header::class.fromJson(headerStr, Attrs.DEMO_ATTR)

    override fun parseParamFromJson(paramStr: String) = Param::class.fromJson(paramStr, Attrs.DEMO_ATTR)

    override fun parseFieldFromJson(paramStr: String): AdditionalField =
        AdditionalField::class.fromJson(
            paramStr,
            Attrs.DEMO_ATTR, Attrs.MOCK_ATTR, Attrs.ADVANCED_ATTR
        )
}