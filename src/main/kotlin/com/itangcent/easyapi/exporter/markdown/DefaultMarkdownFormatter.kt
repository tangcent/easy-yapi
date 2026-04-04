package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter

/**
 * Default implementation of MarkdownFormatter.
 * 
 * This formatter generates comprehensive Markdown documentation including:
 * - Endpoint grouping by folder
 * - Request parameters (path, query, headers, form)
 * - Request body with optional JSON demo
 * - Response body documentation
 * 
 * @param outputDemo Whether to include JSON demo snippets in the output
 */
/**
 * Default implementation of Markdown formatter for API documentation.
 * 
 * This formatter generates comprehensive Markdown documentation including:
 * - API endpoint details (path, method, description)
 * - Request parameters (path, query, headers, form, body)
 * - Response body structure
 * - Optional JSON demo examples
 * 
 * Output is organized by folder/module with tables for parameters.
 * 
 * @param outputDemo Whether to include JSON demo examples in the output
 */
class DefaultMarkdownFormatter(
    private val outputDemo: Boolean = true
) : MarkdownFormatter {
    
    /**
     * Formats a list of API endpoints as Markdown documentation.
     * Groups endpoints by folder and generates detailed documentation for each.
     * 
     * @param endpoints The list of API endpoints to format
     * @param moduleName The name of the module for the document title
     * @return A Markdown-formatted string
     */
    /**
     * Formats a list of API endpoints as Markdown documentation.
     * Groups endpoints by folder and generates detailed documentation for each.
     * 
     * @param endpoints The list of API endpoints to format
     * @param moduleName The name of the module for the document title
     * @return A Markdown-formatted string
     */
    override suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): String {
        val sb = StringBuilder()
        sb.append("# ").append(moduleName).append('\n')
        
        val grouped = endpoints.groupBy { it.folder ?: "" }
        
        for ((folder, list) in grouped) {
            if (folder.isNotBlank()) {
                sb.append('\n').append("## ").append(folder).append('\n')
            }
            
            for (ep in list) {
                formatEndpoint(sb, ep)
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Formats a single API endpoint as a Markdown section.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint to format
     */
    /**
     * Formats a single API endpoint as a Markdown section.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint to format
     */
    private fun formatEndpoint(sb: StringBuilder, ep: ApiEndpoint) {
        sb.append('\n').append("---\n")
        sb.append("### ").append(ep.name ?: "${ep.method.name} ${ep.path}").append('\n')
        
        sb.append('\n').append("> BASIC").append('\n').append('\n')
        sb.append("**Path:** ").append(ep.path).append('\n').append('\n')
        sb.append("**Method:** ").append(ep.method.name).append('\n').append('\n')
        
        ep.description?.takeIf { it.isNotBlank() }?.let {
            sb.append("**Desc:**").append('\n').append('\n')
            sb.append(it).append('\n').append('\n')
        }
        
        formatRequest(sb, ep)
        
        formatResponse(sb, ep)
    }
    
    /**
     * Formats the request section of an endpoint.
     * Includes path params, query params, headers, form data, and body.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint
     */
    /**
     * Formats the request section of an endpoint.
     * Includes path params, query params, headers, form data, and body.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint
     */
    private fun formatRequest(sb: StringBuilder, ep: ApiEndpoint) {
        sb.append('\n').append("> REQUEST").append('\n').append('\n')
        
        val pathParams = ep.parameters.filter { it.binding == ParameterBinding.Path }
        if (pathParams.isNotEmpty()) {
            sb.append("**Path Params:**").append('\n').append('\n')
            formatParamsTable(sb, pathParams)
        }
        
        val queryParams = ep.parameters.filter { it.binding == ParameterBinding.Query }
        if (queryParams.isNotEmpty()) {
            sb.append("**Query:**").append('\n').append('\n')
            formatQueryTable(sb, queryParams)
        }
        
        if (ep.headers.isNotEmpty()) {
            sb.append("**Headers:**").append('\n').append('\n')
            formatHeadersTable(sb, ep.headers)
        }
        
        val formParams = ep.parameters.filter { it.binding == ParameterBinding.Form }
        if (formParams.isNotEmpty()) {
            sb.append("**Form:**").append('\n').append('\n')
            formatFormTable(sb, formParams)
        }
        
        if (ep.body != null) {
            sb.append("**Request Body:**").append('\n').append('\n')
            formatBodyTable(sb, ep.body)
            
            if (outputDemo) {
                sb.append('\n').append("**Request Demo:**").append('\n').append('\n')
                sb.append("```json").append('\n')
                sb.append(ObjectModelJsonConverter.toJson(ep.body))
                sb.append('\n').append("```").append('\n')
            }
        }
        
        if (pathParams.isEmpty() && queryParams.isEmpty() && ep.headers.isEmpty() && formParams.isEmpty() && ep.body == null) {
            sb.append('\n')
        }
    }
    
    /**
     * Formats the response section of an endpoint.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint
     */
    /**
     * Formats the response section of an endpoint.
     * 
     * @param sb The StringBuilder to append to
     * @param ep The API endpoint
     */
    private fun formatResponse(sb: StringBuilder, ep: ApiEndpoint) {
        if (ep.responseBody == null) return
        
        sb.append('\n').append("> RESPONSE").append('\n').append('\n')
        
        sb.append("**Body:**").append('\n').append('\n')
        formatBodyTable(sb, ep.responseBody)
        
        if (outputDemo) {
            sb.append('\n').append("**Response Demo:**").append('\n').append('\n')
            sb.append("```json").append('\n')
            sb.append(ObjectModelJsonConverter.toJson(ep.responseBody))
            sb.append('\n').append("```").append('\n')
        }
    }
    
    /**
     * Formats a table for path parameters.
     * 
     * @param sb The StringBuilder to append to
     * @param params The list of path parameters
     */
    private fun formatParamsTable(sb: StringBuilder, params: List<ApiParameter>) {
        sb.append("| name | value | required | desc |").append('\n')
        sb.append("| ------------ | ------------ | ------------ | ------------ |").append('\n')
        
        for (p in params) {
            sb.append("| ")
                .append(escape(p.name)).append(" | ")
                .append(escape(p.defaultValue ?: "")).append(" | ")
                .append(if (p.required) "YES" else "NO").append(" | ")
                .append(escape(p.description ?: "")).append(" |")
                .append('\n')
        }
        sb.append('\n')
    }
    
    /**
     * Formats a table for query parameters.
     * 
     * @param sb The StringBuilder to append to
     * @param params The list of query parameters
     */
    private fun formatQueryTable(sb: StringBuilder, params: List<ApiParameter>) {
        sb.append("| name | value | required | desc |").append('\n')
        sb.append("| ------------ | ------------ | ------------ | ------------ |").append('\n')
        
        for (p in params) {
            sb.append("| ")
                .append(escape(p.name)).append(" | ")
                .append(escape(p.defaultValue ?: "")).append(" | ")
                .append(if (p.required) "YES" else "NO").append(" | ")
                .append(escape(p.description ?: "")).append(" |")
                .append('\n')
        }
        sb.append('\n')
    }
    
    /**
     * Formats a table for HTTP headers.
     * 
     * @param sb The StringBuilder to append to
     * @param headers The list of headers
     */
    private fun formatHeadersTable(sb: StringBuilder, headers: List<ApiHeader>) {
        sb.append("| name | value | required | desc |").append('\n')
        sb.append("| ------------ | ------------ | ------------ | ------------ |").append('\n')
        
        for (h in headers) {
            sb.append("| ")
                .append(escape(h.name)).append(" | ")
                .append(escape(h.value ?: "")).append(" | ")
                .append(if (h.required) "YES" else "NO").append(" | ")
                .append(escape(h.description ?: "")).append(" |")
                .append('\n')
        }
        sb.append('\n')
    }
    
    /**
     * Formats a table for form data parameters.
     * 
     * @param sb The StringBuilder to append to
     * @param params The list of form parameters
     */
    private fun formatFormTable(sb: StringBuilder, params: List<ApiParameter>) {
        sb.append("| name | value | required | type | desc |").append('\n')
        sb.append("| ------------ | ------------ | ------------ | ------------ | ------------ |").append('\n')
        
        for (p in params) {
            sb.append("| ")
                .append(escape(p.name)).append(" | ")
                .append(escape(p.defaultValue ?: "")).append(" | ")
                .append(if (p.required) "YES" else "NO").append(" | ")
                .append(escape(p.type.name.lowercase())).append(" | ")
                .append(escape(p.description ?: "")).append(" |")
                .append('\n')
        }
        sb.append('\n')
    }
    
    /**
     * Formats a table describing the structure of a request/response body.
     * 
     * @param sb The StringBuilder to append to
     * @param model The object model representing the body structure
     */
    private fun formatBodyTable(sb: StringBuilder, model: ObjectModel?) {
        if (model == null) return
        
        sb.append("| name | type | desc |").append('\n')
        sb.append("| ------------ | ------------ | ------------ |").append('\n')
        
        formatObjectModelRecursive(sb, model, 0)
        sb.append('\n')
    }
    
    /**
     * Recursively formats an object model into table rows.
     * Handles objects, arrays, single values, and maps.
     * 
     * @param sb The StringBuilder to append to
     * @param model The object model to format
     * @param depth The current nesting depth for indentation
     */
    private fun formatObjectModelRecursive(sb: StringBuilder, model: ObjectModel, depth: Int) {
        when (model) {
            is ObjectModel.Object -> {
                for ((fieldName, fieldModel) in model.fields) {
                    formatFieldRow(sb, fieldName, fieldModel, depth)
                }
            }
            is ObjectModel.Array -> {
                formatArrayItemRecursive(sb, model.item, "[0]", depth)
            }
            is ObjectModel.Single -> {
                sb.append("| | ").append(escape(model.type)).append(" | |").append('\n')
            }
            is ObjectModel.MapModel -> {
                sb.append("| key | ").append(formatType(model.keyType)).append(" | |").append('\n')
                sb.append("| value | ").append(formatType(model.valueType)).append(" | |").append('\n')
            }
        }
    }
    
    /**
     * Recursively formats array items with a prefix.
     * 
     * @param sb The StringBuilder to append to
     * @param item The array item model
     * @param prefix The prefix for field names (e.g., "[0]")
     * @param depth The current nesting depth
     */
    private fun formatArrayItemRecursive(sb: StringBuilder, item: ObjectModel, prefix: String, depth: Int) {
        when (item) {
            is ObjectModel.Object -> {
                for ((fieldName, fieldModel) in item.fields) {
                    formatFieldRow(sb, "$prefix.$fieldName", fieldModel, depth)
                }
            }
            is ObjectModel.Array -> {
                formatArrayItemRecursive(sb, item.item, "$prefix[0]", depth)
            }
            is ObjectModel.Single -> {
                sb.append("| ").append(escape(prefix)).append(" | ")
                    .append(escape(item.type)).append("[] | |")
                    .append('\n')
            }
            is ObjectModel.MapModel -> {
                sb.append("| ").append(escape(prefix)).append(".key | ")
                    .append(formatType(item.keyType)).append(" | |")
                    .append('\n')
                sb.append("| ").append(escape(prefix)).append(".value | ")
                    .append(formatType(item.valueType)).append(" | |")
                    .append('\n')
            }
        }
    }
    
    /**
     * Formats a single field row in a body table.
     * Includes indentation for nested fields.
     * 
     * @param sb The StringBuilder to append to
     * @param fieldName The name of the field
     * @param fieldModel The field model containing type and description
     * @param depth The current nesting depth
     */
    private fun formatFieldRow(sb: StringBuilder, fieldName: String, fieldModel: FieldModel, depth: Int) {
        val indent = if (depth > 0) {
            "&ensp;&ensp;".repeat(depth) + "&#124;─"
        } else {
            ""
        }
        
        val type = formatType(fieldModel.model)
        val desc = buildFieldDescription(fieldModel)
        
        sb.append("| ")
            .append(indent).append(escape(fieldName)).append(" | ")
            .append(escape(type)).append(" | ")
            .append(escape(desc)).append(" |")
            .append('\n')
        
        when (val nestedModel = fieldModel.model) {
            is ObjectModel.Object -> {
                for ((nestedFieldName, nestedFieldModel) in nestedModel.fields) {
                    formatFieldRow(sb, nestedFieldName, nestedFieldModel, depth + 1)
                }
            }
            is ObjectModel.Array -> {
                when (val item = nestedModel.item) {
                    is ObjectModel.Object -> {
                        for ((nestedFieldName, nestedFieldModel) in item.fields) {
                            formatFieldRow(sb, nestedFieldName, nestedFieldModel, depth + 1)
                        }
                    }
                    else -> {
                        // For simple array types, no nested rows needed
                    }
                }
            }
            else -> {
                // Simple types don't need nested rows
            }
        }
    }
    
    private fun buildFieldDescription(fieldModel: FieldModel): String {
        val parts = mutableListOf<String>()
        
        fieldModel.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        
        fieldModel.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val optionDesc = options.joinToString("<br>") { opt ->
                if (opt.desc.isNullOrBlank()) {
                    "${opt.value}"
                } else {
                    "${opt.value} :${opt.desc}"
                }
            }
            parts.add(optionDesc)
        }
        
        return parts.joinToString("<br>")
    }
    
    private fun formatType(model: ObjectModel): String {
        return when (model) {
            is ObjectModel.Single -> model.type
            is ObjectModel.Array -> "${formatType(model.item)}[]"
            is ObjectModel.Object -> "object"
            is ObjectModel.MapModel -> "map"
        }
    }
    
    private fun escape(text: String): String {
        return MarkdownEscapeUtils.escape(text)
    }
}
