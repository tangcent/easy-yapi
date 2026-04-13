package com.itangcent.easyapi.psi.type

import com.intellij.psi.PsiClass

/**
 * Handler for special types that require custom processing.
 *
 * Handles:
 * - File upload types (MultipartFile, Part, File, etc.)
 * - Date/time types (Date, LocalDate, LocalDateTime, etc.)
 * - Primitive wrapper types (Integer, Long, Boolean, etc.)
 *
 * Provides:
 * - Type detection utilities
 * - Type resolution for special types
 * - Default value generation
 * - Configuration generation
 *
 * ## File Types
 * Treated as binary upload:
 * - `org.springframework.web.multipart.MultipartFile`
 * - `javax.servlet.http.Part`
 * - `java.io.File`
 * - `java.nio.file.Path`
 *
 * ## Date/Time Types
 * Treated as strings:
 * - `java.util.Date`
 * - `java.time.LocalDate`
 * - `java.time.LocalDateTime`
 *
 * @see ResolvedType for the type system
 * @see JsonType for standard JSON types
 */
object SpecialTypeHandler {

    private val FILE_TYPES = setOf(
        "org.springframework.web.multipart.MultipartFile",
        "javax.servlet.http.Part",
        "jakarta.servlet.http.Part",
        "java.io.File",
        "java.nio.file.Path",
        "org.springframework.core.io.Resource",
        "org.springframework.web.multipart.commons.CommonsMultipartFile"
    )

    private val DATE_TIME_AS_STRING = setOf(
        "java.util.Date",
        "java.sql.Date",
        "java.sql.Timestamp",
        "java.time.LocalDate",
        "java.time.LocalDateTime",
        "java.time.LocalTime",
        "java.time.ZonedDateTime",
        "java.time.OffsetDateTime",
        "java.time.Instant",
        "java.util.Calendar",
        "org.joda.time.DateTime",
        "org.joda.time.LocalDate",
        "org.joda.time.LocalDateTime",
        "org.joda.time.LocalTime"
    )

    private val PRIMITIVE_WRAPPER_TYPES = mapOf(
        "java.lang.Boolean" to "boolean",
        "java.lang.Byte" to "byte",
        "java.lang.Character" to "char",
        "java.lang.Short" to "short",
        "java.lang.Integer" to "int",
        "java.lang.Long" to "long",
        "java.lang.Float" to "float",
        "java.lang.Double" to "double"
    )

    private val PRIMITIVE_TYPES = setOf(
        "boolean", "byte", "char", "short", "int", "long", "float", "double"
    )

    fun isFileType(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return FILE_TYPES.contains(qualifiedName)
    }
    
    fun isFileTypeCanonical(canonicalText: String?): Boolean {
        if (canonicalText == null) return false
        if (FILE_TYPES.contains(canonicalText)) return true
        return FILE_TYPES.any { canonicalText == it.substringAfterLast('.') }
    }

    /**
     * Returns the element type name by stripping a trailing `[]` suffix.
     * e.g. `"MultipartFile[]"` → `"MultipartFile"`, `"String"` → `"String"`
     */
    fun singleTypeName(typeName: String): String = typeName.removeSuffix("[]")

    /**
     * Returns true if the given string represents a file upload type in any form:
     * - Fully-qualified class name (e.g. `"org.springframework.web.multipart.MultipartFile"`)
     * - Simple class name (e.g. `"MultipartFile"`, `"Part"`)
     * - JSON type string (e.g. `"file"`, `"file[]"`)
     * - Array variants of any of the above (e.g. `"MultipartFile[]"`)
     *
     * Use this when the input may come from either PSI type resolution or JSON type mapping.
     */
    fun isFileTypeName(typeName: String?): Boolean {
        if (typeName.isNullOrBlank()) return false
        val t = singleTypeName(typeName.trim())
        return t == "file" || t == "__file__" || isFileTypeCanonical(t)
    }

    fun isDateTimeAsString(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return DATE_TIME_AS_STRING.contains(qualifiedName)
    }

    fun isPrimitiveWrapper(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return PRIMITIVE_WRAPPER_TYPES.containsKey(qualifiedName)
    }

    fun isPrimitive(typeName: String?): Boolean {
        if (typeName == null) return false
        return PRIMITIVE_TYPES.contains(typeName)
    }

    fun isSpecialType(qualifiedName: String?): Boolean {
        return isFileType(qualifiedName) || isDateTimeAsString(qualifiedName) || isPrimitiveWrapper(qualifiedName)
    }

    fun getSimpleTypeName(qualifiedName: String?): String? {
        if (qualifiedName == null) return null
        
        if (isFileType(qualifiedName)) {
            return "file"
        }
        
        if (isDateTimeAsString(qualifiedName)) {
            return "string"
        }
        
        PRIMITIVE_WRAPPER_TYPES[qualifiedName]?.let { return it }
        
        return null
    }

    fun resolveSpecialType(psiClass: PsiClass): ResolvedType? {
        val qualifiedName = psiClass.qualifiedName ?: return null
        
        if (isFileType(qualifiedName)) {
            return ResolvedType.UnresolvedType("__file__")
        }
        
        if (isDateTimeAsString(qualifiedName)) {
            return ResolvedType.UnresolvedType(qualifiedName)
        }
        
        val simpleTypeName = getSimpleTypeName(qualifiedName) ?: return null
        
        return when (simpleTypeName) {
            "file" -> ResolvedType.UnresolvedType("__file__")
            "string" -> ResolvedType.UnresolvedType("java.lang.String")
            "boolean" -> ResolvedType.PrimitiveType(PrimitiveKind.BOOLEAN)
            "byte" -> ResolvedType.PrimitiveType(PrimitiveKind.BYTE)
            "char" -> ResolvedType.PrimitiveType(PrimitiveKind.CHAR)
            "short" -> ResolvedType.PrimitiveType(PrimitiveKind.SHORT)
            "int" -> ResolvedType.PrimitiveType(PrimitiveKind.INT)
            "long" -> ResolvedType.PrimitiveType(PrimitiveKind.LONG)
            "float" -> ResolvedType.PrimitiveType(PrimitiveKind.FLOAT)
            "double" -> ResolvedType.PrimitiveType(PrimitiveKind.DOUBLE)
            else -> null
        }
    }

    fun getDefaultValueForSpecialType(qualifiedName: String?): Any? {
        if (qualifiedName == null) return null
        
        if (isFileType(qualifiedName)) {
            return "(binary)"
        }
        
        if (isDateTimeAsString(qualifiedName)) {
            return ""
        }
        
        return when (qualifiedName) {
            "java.lang.Boolean" -> false
            "java.lang.Byte" -> 0.toByte()
            "java.lang.Character" -> '\u0000'
            "java.lang.Short" -> 0.toShort()
            "java.lang.Integer" -> 0
            "java.lang.Long" -> 0L
            "java.lang.Float" -> 0.0f
            "java.lang.Double" -> 0.0
            else -> null
        }
    }

    fun getAllFileTypePatterns(): List<String> {
        return FILE_TYPES.map { "json.rule.convert[$it]=__file__" }
    }

    fun getAllDateTimePatterns(): List<String> {
        return DATE_TIME_AS_STRING.map { "json.rule.convert[$it]=java.lang.String" }
    }

    fun getRecommendedConfig(): String {
        val lines = mutableListOf<String>()
        lines.add("#File types will be treated as file upload")
        lines.addAll(getAllFileTypePatterns())
        lines.add("")
        lines.add("#Date/Time types will be treated as strings")
        lines.addAll(getAllDateTimePatterns())
        return lines.joinToString("\n")
    }
}
