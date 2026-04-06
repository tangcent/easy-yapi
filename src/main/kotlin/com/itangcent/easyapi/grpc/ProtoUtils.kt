package com.itangcent.easyapi.grpc

/**
 * Utility object for protobuf-related operations.
 *
 * Provides static helper functions for:
 * - Parsing .proto file text into structured data
 * - Mapping Java types to protobuf types
 * - Mapping protobuf scalar types to FieldDescriptorProto.Type enum names
 * - Qualifying type names with package prefixes
 *
 * This utility is used by [ProtoFileResolver] and [StubClassResolver] for
 * building gRPC method descriptors.
 */
object ProtoUtils {

    /**
     * Parse .proto file text into structured data.
     *
     * Handles:
     * - `package` declarations
     * - `import` statements
     * - `service` blocks with `rpc` methods
     * - `message` blocks with field definitions (including nested messages and enums)
     * - `enum` blocks with value definitions
     *
     * @param text The raw .proto file content
     * @return A [ProtoParseResult] containing the parsed structure
     */
    fun parseProtoText(text: String): ProtoParseResult {
        val lines = text.lines()
        var packageName = ""
        val imports = mutableListOf<String>()
        val services = mutableListOf<ServiceDef>()
        val messages = mutableListOf<MessageDef>()
        val enums = mutableListOf<EnumDef>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("package ") -> {
                    packageName = line.removePrefix("package ").trimEnd(';').trim()
                }

                line.startsWith("import ") -> {
                    val importPath = line.removePrefix("import ")
                        .trimEnd(';').trim()
                        .trim('"')
                    imports.add(importPath)
                }

                line.startsWith("service ") -> {
                    val result = parseServiceBlock(lines, i)
                    services.add(result.first)
                    i = result.second
                    continue
                }

                line.startsWith("message ") -> {
                    val result = parseMessageBlock(lines, i, "")
                    messages.add(result.first)
                    i = result.second
                    continue
                }

                line.startsWith("enum ") -> {
                    val result = parseEnumBlock(lines, i, "")
                    enums.add(result.first)
                    i = result.second
                    continue
                }
            }
            i++
        }

        return ProtoParseResult(packageName, imports, services, messages, enums)
    }

    private fun parseServiceBlock(lines: List<String>, startIndex: Int): Pair<ServiceDef, Int> {
        val headerLine = lines[startIndex].trim()
        val serviceName = headerLine.removePrefix("service ").substringBefore('{').trim()
        val methods = mutableListOf<RpcDef>()

        var depth = 0
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i].trim()
            depth += line.count { it == '{' } - line.count { it == '}' }

            if (i > startIndex) {
                val rpcMatch = RPC_PATTERN.find(line)
                if (rpcMatch != null) {
                    val name = rpcMatch.groupValues[1].trim()
                    val clientStream = rpcMatch.groupValues[2].isNotBlank()
                    val input = rpcMatch.groupValues[3].trim()
                    val serverStream = rpcMatch.groupValues[4].isNotBlank()
                    val output = rpcMatch.groupValues[5].trim()
                    methods.add(RpcDef(name, input, output, clientStream, serverStream))
                }
            }

            if (depth <= 0 && i > startIndex) break
            i++
        }

        return Pair(ServiceDef(serviceName, methods), i)
    }

    private fun parseMessageBlock(
        lines: List<String>,
        startIndex: Int,
        parentPrefix: String
    ): Pair<MessageDef, Int> {
        val headerLine = lines[startIndex].trim()
        val messageName = headerLine.removePrefix("message ").substringBefore('{').trim()
        val fullName = if (parentPrefix.isNotEmpty()) "$parentPrefix.$messageName" else messageName
        val fields = mutableListOf<FieldDef>()
        val nestedMessages = mutableListOf<MessageDef>()
        val nestedEnums = mutableListOf<EnumDef>()

        var depth = 0
        var i = startIndex
        var inBlock = false
        var skipDepthUpdate = false

        while (i < lines.size) {
            val line = lines[i].trim()
            val openBraces = line.count { it == '{' }
            val closeBraces = line.count { it == '}' }

            if (inBlock && depth >= 1) {
                when {
                    line.startsWith("message ") -> {
                        val result = parseMessageBlock(lines, i, fullName)
                        nestedMessages.add(result.first)
                        i = result.second
                        skipDepthUpdate = true
                        continue
                    }
                    line.startsWith("enum ") -> {
                        val result = parseEnumBlock(lines, i, fullName)
                        nestedEnums.add(result.first)
                        i = result.second
                        skipDepthUpdate = true
                        continue
                    }
                    else -> {
                        val fieldMatch = FIELD_PATTERN.find(line)
                        if (fieldMatch != null) {
                            val (label, type, name, number) = fieldMatch.destructured
                            fields.add(FieldDef(name.trim(), type.trim(), number.trim().toIntOrNull() ?: 0, label.trim()))
                        }
                    }
                }
            }

            if (openBraces > 0 && !inBlock) {
                inBlock = true
            }

            if (skipDepthUpdate) {
                skipDepthUpdate = false
            } else {
                depth += openBraces - closeBraces
            }

            if (depth <= 0 && i > startIndex) break
            i++
        }

        return Pair(MessageDef(fullName, fields, nestedMessages, nestedEnums), i)
    }

    private fun parseEnumBlock(
        lines: List<String>,
        startIndex: Int,
        parentPrefix: String
    ): Pair<EnumDef, Int> {
        val headerLine = lines[startIndex].trim()
        val enumName = headerLine.removePrefix("enum ").substringBefore('{').trim()
        val fullName = if (parentPrefix.isNotEmpty()) "$parentPrefix.$enumName" else enumName
        val values = mutableListOf<EnumValueDef>()

        var depth = 0
        var i = startIndex
        var inBlock = false

        while (i < lines.size) {
            val line = lines[i].trim()
            val openBraces = line.count { it == '{' }
            val closeBraces = line.count { it == '}' }

            if (openBraces > 0 && !inBlock) {
                inBlock = true
                val contentAfterBrace = line.substringAfter('{', "")
                extractEnumValues(contentAfterBrace, values)
            } else if (inBlock && depth >= 1) {
                extractEnumValues(line, values)
            }

            depth += openBraces - closeBraces

            if (depth <= 0 && i > startIndex) break
            if (depth <= 0 && inBlock) {
                i++
                break
            }
            i++
        }

        return Pair(EnumDef(fullName, values), i)
    }

    private fun extractEnumValues(text: String, values: MutableList<EnumValueDef>) {
        // Find all enum value patterns in the text (handles multiple values on same line)
        val matches = ENUM_VALUE_PATTERN.findAll(text)
        for (match in matches) {
            val (name, number) = match.destructured
            values.add(EnumValueDef(name.trim(), number.trim().toIntOrNull() ?: 0))
        }
    }

    /**
     * Map Java type names to protobuf type names.
     *
     * Handles:
     * - Primitive types (int, long, boolean, float, double)
     * - Wrapper types (java.lang.Integer, java.lang.Long, etc.)
     * - Common types (String, ByteString, byte[])
     * - Unknown types return their simple name (e.g., "com.example.MyMessage" -> "MyMessage")
     *
     * @param javaType The Java type canonical name or simple name
     * @return The corresponding protobuf type name
     */
    fun mapJavaTypeToProto(javaType: String): String = when (javaType) {
        "java.lang.String", "String" -> "string"
        "int", "java.lang.Integer" -> "int32"
        "long", "java.lang.Long" -> "int64"
        "boolean", "java.lang.Boolean" -> "bool"
        "float", "java.lang.Float" -> "float"
        "double", "java.lang.Double" -> "double"
        "com.google.protobuf.ByteString" -> "bytes"
        "byte[]" -> "bytes"
        else -> {
            val simpleName = javaType.substringAfterLast('.')
            if (simpleName.equals("string", ignoreCase = true)) "string"
            else if (simpleName.equals("integer", ignoreCase = true) || simpleName.equals("int", ignoreCase = true)) "int32"
            else if (simpleName.equals("long", ignoreCase = true)) "int64"
            else if (simpleName.equals("boolean", ignoreCase = true) || simpleName.equals("bool", ignoreCase = true)) "bool"
            else if (simpleName.equals("float", ignoreCase = true)) "float"
            else if (simpleName.equals("double", ignoreCase = true)) "double"
            else simpleName
        }
    }

    /**
     * Map protobuf scalar type names to FieldDescriptorProto.Type enum constant names.
     *
     * Returns null for message or enum types (handled via setTypeName instead).
     *
     * @param protoType The protobuf type name (e.g., "string", "int32", "bool")
     * @return The corresponding Type enum constant name (e.g., "TYPE_STRING", "TYPE_INT32", "TYPE_BOOL"),
     *         or null if the type is not a scalar type
     */
    fun mapProtoType(protoType: String): String? = when (protoType) {
        "double" -> "TYPE_DOUBLE"
        "float" -> "TYPE_FLOAT"
        "int32" -> "TYPE_INT32"
        "int64" -> "TYPE_INT64"
        "uint32" -> "TYPE_UINT32"
        "uint64" -> "TYPE_UINT64"
        "sint32" -> "TYPE_SINT32"
        "sint64" -> "TYPE_SINT64"
        "fixed32" -> "TYPE_FIXED32"
        "fixed64" -> "TYPE_FIXED64"
        "sfixed32" -> "TYPE_SFIXED32"
        "sfixed64" -> "TYPE_SFIXED64"
        "bool" -> "TYPE_BOOL"
        "string" -> "TYPE_STRING"
        "bytes" -> "TYPE_BYTES"
        else -> null
    }

    /**
     * Qualify a type name with a package prefix for use in FileDescriptorProto.
     *
     * Protobuf type references in descriptors must be fully qualified with a leading dot.
     * For example, "MyMessage" in package "com.example" becomes ".com.example.MyMessage".
     *
     * @param typeName The type name (may already be qualified with a leading dot)
     * @param packageName The package name to qualify with
     * @return The qualified type name with leading dot
     */
    fun qualifyTypeName(typeName: String, packageName: String): String {
        if (typeName.startsWith(".")) return typeName
        return if (packageName.isNotEmpty()) ".$packageName.$typeName" else ".$typeName"
    }

    /**
     * Collect all messages (including nested) from a list of top-level messages.
     *
     * @param messages The top-level messages to flatten
     * @return A flat list of all messages including nested ones
     */
    fun flattenMessages(messages: List<MessageDef>): List<MessageDef> {
        val result = mutableListOf<MessageDef>()
        for (msg in messages) {
            result.add(msg)
            result.addAll(flattenMessages(msg.nestedMessages))
        }
        return result
    }

    /**
     * Collect all enums (including nested) from a parse result.
     *
     * @param parseResult The parse result containing messages and enums
     * @return A flat list of all enums including nested ones
     */
    fun flattenEnums(parseResult: ProtoParseResult): List<EnumDef> {
        val result = mutableListOf<EnumDef>()
        result.addAll(parseResult.enums)
        for (msg in parseResult.messages) {
            result.addAll(flattenEnumsInMessage(msg))
        }
        return result
    }

    private fun flattenEnumsInMessage(message: MessageDef): List<EnumDef> {
        val result = mutableListOf<EnumDef>()
        result.addAll(message.nestedEnums)
        for (nested in message.nestedMessages) {
            result.addAll(flattenEnumsInMessage(nested))
        }
        return result
    }

    private val RPC_PATTERN = Regex(
        """rpc\s+(\w+)\s*\(\s*(stream\s+)?(\w[\w.]*)\s*\)\s*returns\s*\(\s*(stream\s+)?(\w[\w.]*)\s*\)"""
    )

    private val FIELD_PATTERN = Regex(
        """^(repeated|required|optional)?\s*([\w.]+)\s+(\w+)\s*=\s*(\d+)\s*;"""
    )

    private val ENUM_VALUE_PATTERN = Regex(
        """^(\w+)\s*=\s*(\d+)\s*;"""
    )
}

/**
 * Parsed result from a .proto file.
 *
 * @param packageName The package declaration (empty if not specified)
 * @param imports List of import paths
 * @param services List of service definitions
 * @param messages List of top-level message definitions
 * @param enums List of top-level enum definitions
 */
data class ProtoParseResult(
    val packageName: String,
    val imports: List<String>,
    val services: List<ServiceDef>,
    val messages: List<MessageDef>,
    val enums: List<EnumDef> = emptyList()
)

/**
 * Represents a gRPC service definition parsed from a .proto file.
 *
 * @param name The service name
 * @param methods List of RPC method definitions
 */
data class ServiceDef(val name: String, val methods: List<RpcDef>)

/**
 * Represents an RPC method definition within a service.
 *
 * @param name The method name
 * @param inputType The input message type name
 * @param outputType The output message type name
 * @param clientStreaming Whether the client streams requests
 * @param serverStreaming Whether the server streams responses
 */
data class RpcDef(
    val name: String,
    val inputType: String,
    val outputType: String,
    val clientStreaming: Boolean = false,
    val serverStreaming: Boolean = false
)

/**
 * Represents a protobuf message definition parsed from a .proto file.
 *
 * @param name The message name (fully qualified including parent prefixes for nested messages)
 * @param fields List of field definitions
 * @param nestedMessages List of nested message definitions
 * @param nestedEnums List of nested enum definitions
 */
data class MessageDef(
    val name: String,
    val fields: List<FieldDef>,
    val nestedMessages: List<MessageDef> = emptyList(),
    val nestedEnums: List<EnumDef> = emptyList()
)

/**
 * Represents a field within a protobuf message.
 *
 * @param name The field name
 * @param type The field type (scalar type name or message type name)
 * @param number The field number
 * @param label The field label ("repeated", "required", "optional", or empty for proto3)
 */
data class FieldDef(val name: String, val type: String, val number: Int, val label: String)

/**
 * Represents a protobuf enum definition parsed from a .proto file.
 *
 * @param name The enum name (fully qualified including parent prefixes for nested enums)
 * @param values List of enum value definitions
 */
data class EnumDef(val name: String, val values: List<EnumValueDef>)

/**
 * Represents a value within a protobuf enum.
 *
 * @param name The value name
 * @param number The value number
 */
data class EnumValueDef(val name: String, val number: Int)
