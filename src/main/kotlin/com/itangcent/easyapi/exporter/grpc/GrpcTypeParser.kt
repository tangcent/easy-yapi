package com.itangcent.easyapi.exporter.grpc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel

/**
 * Holds extracted information about a single protobuf message field.
 */
private data class ProtobufField(
    val name: String,
    val typeName: String,
    val type: PsiType?,
    val isRepeated: Boolean = false,
    val isMap: Boolean = false,
    val isMessage: Boolean = false,
    val elementType: PsiType? = null,
    val keyType: String? = null,
    val valueType: PsiType? = null
)

/**
 * Parses Protocol Buffer generated message classes into [ObjectModel] for
 * request/response body documentation.
 *
 * Handles:
 * - Scalar fields → [ObjectModel.Single]
 * - Repeated fields (List&lt;T&gt;) → [ObjectModel.Array]
 * - Map fields (Map&lt;K,V&gt;) → [ObjectModel.MapModel]
 * - Nested message types → recursive [ObjectModel.Object] (up to depth 5)
 *
 * Returns `null` for non-protobuf classes.
 */
class GrpcTypeParser {

    companion object {
        /** Maximum recursion depth to prevent circular references. */
        private const val MAX_DEPTH = 5

        /** Fully qualified names of protobuf message base classes. */
        private val PROTOBUF_MESSAGE_BASES = setOf(
            "com.google.protobuf.GeneratedMessageV3",
            "com.google.protobuf.GeneratedMessage"
        )

        /** Fully qualified name of the MessageOrBuilder interface. */
        private const val MESSAGE_OR_BUILDER_FQN = "com.google.protobuf.MessageOrBuilder"

        /** Getter method names to exclude from field extraction. */
        private val EXCLUDED_GETTERS = setOf(
            "getDefaultInstance",
            "getDefaultInstanceForType",
            "getDescriptor",
            "getDescriptorForType",
            "getParserForType",
            "getSerializedSize",
            "getUnknownFields",
            "getInitializationErrorString",
            "getAllFields",
            "getRepeatedFieldCount",
            "getOneofFieldDescriptor",
            "getField",
            "getClass"
        )

        /** Maps protobuf Java type names to human-readable type names. */
        private val TYPE_MAPPINGS = mapOf(
            "java.lang.String" to "string",
            "int" to "int32",
            "java.lang.Integer" to "int32",
            "long" to "int64",
            "java.lang.Long" to "int64",
            "float" to "float",
            "java.lang.Float" to "float",
            "double" to "double",
            "java.lang.Double" to "double",
            "boolean" to "bool",
            "java.lang.Boolean" to "bool",
            "com.google.protobuf.ByteString" to "bytes",
            "byte[]" to "bytes"
        )
    }

    /**
     * Main entry point — parses a protobuf-generated message class into an [ObjectModel].
     *
     * @param psiClass The class to parse
     * @return An [ObjectModel.Object] with all message fields, or `null` if not a protobuf message
     */
    fun parseMessageType(psiClass: PsiClass): ObjectModel? {
        if (!isProtobufMessage(psiClass)) return null
        return parseMessageTypeInternal(psiClass, depth = 0)
    }

    /**
     * Checks whether [psiClass] is a protobuf-generated message class.
     *
     * A class is considered a protobuf message if it:
     * - Extends `com.google.protobuf.GeneratedMessageV3` or `GeneratedMessage`
     * - Implements `com.google.protobuf.MessageOrBuilder`
     */
    fun isProtobufMessage(psiClass: PsiClass): Boolean {
        val supers = psiClass.supers
        // Check direct superclass chain for GeneratedMessageV3 / GeneratedMessage
        if (supers.any { it.qualifiedName in PROTOBUF_MESSAGE_BASES }) return true
        // Check interfaces for MessageOrBuilder
        if (supers.any { it.qualifiedName == MESSAGE_OR_BUILDER_FQN }) return true
        // Check transitive supers (e.g., extends a class that extends GeneratedMessageV3)
        return psiClass.superTypes.any { superType ->
            val resolved = superType.resolve() ?: return@any false
            resolved.supers.any { it.qualifiedName in PROTOBUF_MESSAGE_BASES }
        }
    }

    /**
     * Extracts protobuf fields from getter methods following the protobuf naming convention.
     *
     * Filters getters by:
     * - Name starts with "get" (but not "getDefault")
     * - Zero parameters
     * - Not in the [EXCLUDED_GETTERS] set
     * - Not ending with "OrBuilder" or "OrBuilderList" (internal protobuf accessors)
     * - Not a "Count" getter for repeated fields (e.g., getXxxCount)
     * - Not a "Bytes" getter for string fields (e.g., getXxxBytes)
     */
    private fun extractFieldsFromGetters(psiClass: PsiClass): List<ProtobufField> {
        val allGetterNames = psiClass.methods
            .filter { it.name.startsWith("get") && it.parameterList.parametersCount == 0 }
            .map { it.name }
            .toSet()

        return psiClass.methods
            .filter { method ->
                val name = method.name
                name.startsWith("get")
                        && !name.startsWith("getDefault")
                        && method.parameterList.parametersCount == 0
                        && name !in EXCLUDED_GETTERS
                        && !name.endsWith("OrBuilder")
                        && !name.endsWith("OrBuilderList")
                        && !isCountGetter(name, allGetterNames)
                        && !isBytesGetter(name, allGetterNames)
            }
            .mapNotNull { getter ->
                val fieldName = getter.name.removePrefix("get")
                    .replaceFirstChar { it.lowercase() }
                val returnType = getter.returnType ?: return@mapNotNull null
                val typeName = returnType.canonicalText

                classifyField(fieldName, typeName, returnType)
            }
    }

    /**
     * Maps a protobuf Java type name to a human-readable type name.
     *
     * For example:
     * - `"java.lang.String"` → `"string"`
     * - `"int"` → `"int32"`
     * - `"com.google.protobuf.ByteString"` → `"bytes"`
     *
     * Unknown types are returned as-is.
     */
    fun mapProtobufType(typeName: String): String {
        return TYPE_MAPPINGS[typeName] ?: typeName
    }

    // ── Private helpers ──────────────────────────────────────────────

    private fun parseMessageTypeInternal(psiClass: PsiClass, depth: Int): ObjectModel? {
        if (depth >= MAX_DEPTH) return null

        val fields = extractFieldsFromGetters(psiClass)
        if (fields.isEmpty()) return null

        val fieldModels = linkedMapOf<String, FieldModel>()
        for (field in fields) {
            val model = resolveFieldModel(field, depth)
            fieldModels[field.name] = FieldModel(
                model = model,
                required = false // protobuf3 fields are all optional
            )
        }

        return ObjectModel.Object(fields = fieldModels)
    }

    private fun resolveFieldModel(field: ProtobufField, depth: Int): ObjectModel {
        return when {
            field.isRepeated -> {
                val itemModel = resolveElementModel(field.elementType, depth)
                ObjectModel.Array(item = itemModel)
            }
            field.isMap -> {
                val keyModel = ObjectModel.Single(mapProtobufType(field.keyType ?: "string"))
                val valueModel = resolveElementModel(field.valueType, depth)
                ObjectModel.MapModel(keyType = keyModel, valueType = valueModel)
            }
            field.isMessage -> {
                resolveNestedMessage(field.type, depth)
                    ?: ObjectModel.Single(mapProtobufType(field.typeName))
            }
            else -> ObjectModel.Single(mapProtobufType(field.typeName))
        }
    }

    private fun resolveElementModel(type: PsiType?, depth: Int): ObjectModel {
        if (type == null) return ObjectModel.Single("unknown")
        val psiClass = PsiTypesUtil.getPsiClass(type)
        if (psiClass != null && isProtobufMessage(psiClass)) {
            return parseMessageTypeInternal(psiClass, depth + 1)
                ?: ObjectModel.Single(mapProtobufType(type.canonicalText))
        }
        return ObjectModel.Single(mapProtobufType(type.canonicalText))
    }

    private fun resolveNestedMessage(type: PsiType?, depth: Int): ObjectModel? {
        if (type == null) return null
        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return null
        if (!isProtobufMessage(psiClass)) return null
        return parseMessageTypeInternal(psiClass, depth + 1)
    }

    private fun classifyField(fieldName: String, typeName: String, returnType: PsiType): ProtobufField {
        // Check for repeated fields (List<T>)
        if (typeName.startsWith("java.util.List")) {
            val elementType = extractTypeArgument(returnType, 0)
            return ProtobufField(
                name = fieldName.removeSuffix("List"),
                typeName = typeName,
                type = returnType,
                isRepeated = true,
                elementType = elementType
            )
        }

        // Check for map fields (Map<K,V>)
        if (typeName.startsWith("java.util.Map")) {
            val keyType = extractTypeArgument(returnType, 0)
            val valueType = extractTypeArgument(returnType, 1)
            return ProtobufField(
                name = fieldName.removeSuffix("Map"),
                typeName = typeName,
                type = returnType,
                isMap = true,
                keyType = keyType?.canonicalText,
                valueType = valueType
            )
        }

        // Check for nested message types
        val psiClass = PsiTypesUtil.getPsiClass(returnType)
        val isMessage = psiClass != null && isProtobufMessage(psiClass)

        return ProtobufField(
            name = fieldName,
            typeName = typeName,
            type = returnType,
            isMessage = isMessage
        )
    }

    private fun extractTypeArgument(type: PsiType, index: Int): PsiType? {
        if (type !is PsiClassType) return null
        val typeArgs = type.parameters
        return if (index < typeArgs.size) typeArgs[index] else null
    }

    /**
     * Checks if a getter name is a "Count" getter for a repeated field.
     * e.g., `getItemsCount` when `getItemsList` exists.
     */
    private fun isCountGetter(name: String, allGetterNames: Set<String>): Boolean {
        if (!name.endsWith("Count")) return false
        val baseName = name.removeSuffix("Count")
        return "${baseName}List" in allGetterNames
    }

    /**
     * Checks if a getter name is a "Bytes" getter for a string field.
     * e.g., `getNameBytes` when `getName` exists.
     */
    private fun isBytesGetter(name: String, allGetterNames: Set<String>): Boolean {
        if (!name.endsWith("Bytes")) return false
        val baseName = name.removeSuffix("Bytes")
        return baseName in allGetterNames
    }
}
