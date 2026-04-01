package com.itangcent.easyapi.exporter.swagger

object SwaggerConfigLoader {
    
    fun getSwagger2Config(): Map<String, String> {
        return mapOf(
            "param.doc" to "@io.swagger.annotations.ApiParam#value",
            "param.default.value" to "@io.swagger.annotations.ApiParam#defaultValue",
            "param.required" to "@io.swagger.annotations.ApiParam#required",
            "param.ignore" to "@io.swagger.annotations.ApiParam#hidden",
            
            "class.doc" to "@io.swagger.annotations.Api#value",
            "class.doc" to "@io.swagger.annotations.Api#tags",
            "ignore" to "@io.swagger.annotations.Api#hidden",
            
            "class.doc" to "@io.swagger.annotations.ApiModel#value",
            "class.doc" to "@io.swagger.annotations.ApiModel#description",
            
            "json.rule.field.name" to "@io.swagger.annotations.ApiModelProperty#name",
            "field.ignore" to "@io.swagger.annotations.ApiModelProperty#hidden",
            "field.doc" to "@io.swagger.annotations.ApiModelProperty#value",
            "field.doc" to "@io.swagger.annotations.ApiModelProperty#notes",
            "field.required" to "@io.swagger.annotations.ApiModelProperty#required",
            
            "method.doc" to "@io.swagger.annotations.ApiOperation#value",
            "api.tag" to "@io.swagger.annotations.ApiOperation#tags"
        )
    }
    
    fun getSwagger3Config(): Map<String, String> {
        return mapOf(
            "ignore" to "@io.swagger.v3.oas.annotations.Hidden",
            "field.ignore" to "@io.swagger.v3.oas.annotations.Hidden",
            "param.ignore" to "@io.swagger.v3.oas.annotations.Hidden",
            
            "api.name" to "@io.swagger.v3.oas.annotations.Operation#summary",
            "method.doc" to "@io.swagger.v3.oas.annotations.Operation#summary",
            "method.doc" to "@io.swagger.v3.oas.annotations.Operation#description",
            "method.default.http.method" to "@io.swagger.v3.oas.annotations.Operation#method",
            "api.tag" to "@io.swagger.v3.oas.annotations.Operation#tags",
            
            "api.tag" to "@io.swagger.v3.oas.annotations.tags.Tag#name",
            
            "param.required" to "groovy:it.ann(\"io.swagger.v3.oas.annotations.media.Schema\",\"requiredMode\")?.endsWith(\"REQUIRED\")",
            "param.doc" to "@io.swagger.v3.oas.annotations.media.Schema#description",
            "param.ignore" to "@io.swagger.v3.oas.annotations.media.Schema#hidden",
            "field.required" to "groovy:it.ann(\"io.swagger.v3.oas.annotations.media.Schema\",\"requiredMode\")?.endsWith(\"REQUIRED\")",
            "field.name" to "@io.swagger.v3.oas.annotations.media.Schema#name",
            "field.doc" to "@io.swagger.v3.oas.annotations.media.Schema#description",
            "field.ignore" to "@io.swagger.v3.oas.annotations.media.Schema#hidden",
            
            "param.required" to "@io.swagger.v3.oas.annotations.Parameter#required",
            "param.doc" to "@io.swagger.v3.oas.annotations.Parameter#description"
        )
    }
    
    fun getAllConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()
        config.putAll(getSwagger2Config())
        config.putAll(getSwagger3Config())
        return config
    }
}
