package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.MutableConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger


class RecommendConfigReader : ConfigReader {

    @Inject
    @Named("delegate_config_reader")
    val configReader: ConfigReader? = null

    @Inject(optional = true)
    val settingBinder: SettingBinder? = null

    @Inject
    val logger: Logger? = null

    override fun first(key: String): String? {
        return configReader!!.first(key)
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        configReader!!.foreach(keyFilter, action)
    }

    override fun foreach(action: (String, String) -> Unit) {
        configReader!!.foreach(action)
    }

    override fun read(key: String): Collection<String>? {
        return configReader!!.read(key)
    }

    @PostConstruct
    fun init() {
        if (settingBinder?.read()?.useRecommendConfig == true) {
            if (configReader is MutableConfigReader) {
                configReader.loadConfigInfoContent(RECOMMEND_CONFIG)
                logger!!.info("use recommend config")
            } else {
                logger!!.warn("failed to use recommend config")
            }
        }
    }

    companion object {
        const val RECOMMEND_CONFIG = """
            #Get the module from the annotation,group the apis
            module=#module

            #Ignore class/api
            ignore=#ignore

            #deprecated info
            doc.method=groovy:it.hasDoc("deprecated")?("「已废弃」 "+it.doc("deprecated")):null
            doc.method=groovy:it.hasAnn("java.lang.Deprecated")?"\n「已废弃」":null
            doc.method=groovy:it.hasAnn("kotlin.Deprecated")?("\n「已废弃」 " + it.ann("kotlin.Deprecated","message")):null
            doc.method=groovy:it.containingClass().hasDoc("deprecated")?("「已废弃」 "+it.containingClass().doc("deprecated")):null
            doc.method=groovy:it.containingClass().hasAnn("java.lang.Deprecated")?"\n「已废弃」":null
            doc.method=groovy:it.containingClass().hasAnn("kotlin.Deprecated")?("\n「已废弃」 " + it.containingClass().ann("kotlin.Deprecated","message")):null
            doc.field=groovy:it.hasDoc("deprecated")?("「已废弃」 "+it.doc("deprecated")):null
            doc.field=groovy:it.hasAnn("java.lang.Deprecated")?"\n「已废弃」":null
            doc.field=groovy:it.hasAnn("kotlin.Deprecated")?("\n「已废弃」 " + it.ann("kotlin.Deprecated","message")):null

            doc.method[#deprecated]=groovy:"\n「deprecated」" + it.doc("deprecated")
            doc.method[@java.lang.Deprecated]=「deprecated」
            doc.method[@kotlin.Deprecated]=groovy:"\n「deprecated」" + it.ann("kotlin.Deprecated","message")

            doc.method[groovy:it.containingClass().hasDoc("deprecated")]=groovy:"\n「deprecated」" + it.containingClass().doc("deprecated")
            doc.method[groovy:it.containingClass().hasAnn("java.lang.Deprecated")]=「deprecated」
            doc.method[groovy:it.containingClass().hasAnn("kotlin.Deprecated")]=groovy:"\n「deprecated」 " + it.containingClass().ann("kotlin.Deprecated","message")

            doc.field[#deprecated]=groovy:"\n「deprecated」" + it.doc("deprecated")
            doc.field[@java.lang.Deprecated]=「deprecated」
            doc.field[@kotlin.Deprecated]=groovy:"\n「deprecated」" + it.ann("kotlin.Deprecated","message")

            #Additional json parsing rules
            #Support for Jackson annotations
            json.rule.field.name=@com.fasterxml.jackson.annotation.JsonProperty#value
            json.rule.field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value

            #Support for Gson annotations
            json.rule.field.name=@com.google.gson.annotations.SerializedName#value
            json.rule.field.ignore=!@com.google.gson.annotations.Expose#serialize

            #The ObjectId and Date are parsed as strings
            json.rule.convert[org.bson.types.ObjectId]=java.lang.String
            json.rule.convert[java.util.Date]=java.lang.String
            json.rule.convert[java.sql.Timestamp]=java.lang.String

            #resolve HttpEntity/RequestEntity/ResponseEntity/Mono/Flux
            ###set resolveProperty = false
            json.rule.convert[#regex:org.springframework.http.HttpEntity<(.*?)>]=${'$'}{1}
            json.rule.convert[#regex:org.springframework.http.RequestEntity<(.*?)>]=${'$'}{1}
            json.rule.convert[#regex:org.springframework.http.ResponseEntity<(.*?)>]=${'$'}{1}
            json.rule.convert[#regex:reactor.core.publisher.Mono<(.*?)>]=${'$'}{1}
            json.rule.convert[#regex:reactor.core.publisher.Flux<(.*?)>]=java.util.List<${'$'}{1}>
            ###set resolveProperty = true

            #Support for javax.validation annotations
            param.required=@javax.validation.constraints.NotBlank
            field.required=@"javax.validation.constraints.NotBlank
            param.required=@"javax.validation.constraints.NotNull
            field.required=@javax.validation.constraints.NotNull
            param.required=@javax.validation.constraints.NotEmpty
            field.required=@javax.validation.constraints.NotEmpty

            #Support spring file
            type.is_file=groovy:it.isExtend("org.springframework.web.multipart.MultipartFile")
"""
    }
}