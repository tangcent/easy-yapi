package com.itangcent.easyapi.psi.type

/**
 * Constants and utilities for JSON type handling.
 *
 * Provides:
 * - Standard JSON type constants (string, int, boolean, etc.)
 * - Type validation utilities
 * - Java-to-JSON type conversion
 * - PSI-to-JSON type conversion
 *
 * ## Standard Types
 * - Primitive: string, short, int, long, float, double, boolean
 * - Composite: array, object
 * - Special: file, date, datetime
 *
 * ## Usage
 * ```kotlin
 * // Get default value for a type
 * val default = JsonType.defaultValueForType("string") // ""
 *
 * // Convert Java type to JSON type
 * val jsonType = JsonType.fromJavaType("java.lang.String") // "string"
 *
 * // Check if type is a number
 * val isNum = JsonType.isNumber("int") // true
 * ```
 */
object JsonType {
    const val STRING = "string"
    const val SHORT = "short"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val DOUBLE = "double"
    const val BOOLEAN = "boolean"
    const val ARRAY = "array"
    const val OBJECT = "object"
    const val FILE = "file"
    const val DATE = "date"
    const val DATETIME = "datetime"
    
    val ALL_TYPES = setOf(
        STRING, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, ARRAY, OBJECT, FILE, DATE, DATETIME
    )
    
    val NUMBER_TYPES = setOf(SHORT, INT, LONG, FLOAT, DOUBLE)
    
    fun isNumber(type: String?): Boolean = type != null && type in NUMBER_TYPES
    
    fun isPrimitive(type: String?): Boolean = type != null && type != ARRAY && type != OBJECT
    
    fun isValid(type: String?): Boolean = type != null && type in ALL_TYPES
    
    fun defaultValueForType(type: String): Any? {
        return when (type) {
            STRING -> ""
            INT, SHORT -> 0
            LONG -> 0L
            FLOAT -> 0.0f
            DOUBLE -> 0.0
            BOOLEAN -> false
            else -> null
        }
    }
    
    fun fromJavaType(javaType: String?): String {
        if (javaType.isNullOrBlank()) return STRING
        
        val normalized = javaType.lowercase().removePrefix("java.lang.").removePrefix("java.util.")
        
        return when {
            normalized == "string" || normalized == "char" || normalized == "character" -> STRING
            normalized == "boolean" -> BOOLEAN
            normalized == "byte" -> INT
            normalized == "short" -> SHORT
            normalized == "int" || normalized == "integer" -> INT
            normalized == "long" -> LONG
            normalized == "float" -> FLOAT
            normalized == "double" -> DOUBLE
            normalized == "biginteger" || normalized == "java.math.biginteger" -> LONG
            normalized == "bigdecimal" || normalized == "java.math.bigdecimal" -> DOUBLE
            normalized == "date" || normalized == "java.util.date" || normalized == "localdate" || normalized == "java.time.localdate" -> DATE
            normalized == "localdatetime" || normalized == "java.time.localdatetime" || 
                normalized == "timestamp" || normalized == "java.sql.timestamp" -> DATETIME
            normalized == "file" || normalized == "multipartfile" || 
                normalized == "org.springframework.web.multipart.multipartfile" -> FILE
            normalized.contains("multipartfile[]") || 
                normalized.contains("multipartfile>") && normalized.contains("[]") ||
                normalized.contains("part[]") ||
                normalized.contains("part>") && normalized.contains("[]") -> "file[]"
            normalized == "list" || normalized == "arraylist" || normalized == "linkedlist" ||
                normalized == "set" || normalized == "hashset" || normalized == "linkedhashset" ||
                normalized == "collection" || normalized.startsWith("list<") || 
                normalized.startsWith("set<") || normalized.startsWith("collection<") -> {
                    if (normalized.contains("multipartfile") || normalized.contains("part")) {
                        "file[]"
                    } else {
                        ARRAY
                    }
                }
            normalized == "map" || normalized == "hashmap" || normalized == "linkedhashmap" ||
                normalized == "treemap" || normalized.startsWith("map<") -> OBJECT
            normalized.contains("multipartfile") || normalized.contains("part") -> FILE
            normalized.contains("list") || normalized.contains("set") || 
                normalized.contains("collection") || normalized.contains("[]") -> ARRAY
            normalized.contains("map") -> OBJECT
            normalized.contains("int") || normalized.contains("integer") -> INT
            normalized.contains("long") -> LONG
            normalized.contains("float") -> FLOAT
            normalized.contains("double") -> DOUBLE
            normalized.contains("boolean") -> BOOLEAN
            normalized.contains("short") -> SHORT
            normalized.contains("byte") -> INT
            normalized.contains("date") || normalized.contains("time") -> DATETIME
            else -> OBJECT
        }
    }
    
    fun fromPsiType(psiType: com.intellij.psi.PsiType?): String {
        if (psiType == null) return STRING
        
        val canonical = psiType.canonicalText
        
        if (psiType is com.intellij.psi.PsiPrimitiveType) {
            return when (canonical) {
                "boolean" -> BOOLEAN
                "byte" -> INT
                "short" -> SHORT
                "int" -> INT
                "long" -> LONG
                "float" -> FLOAT
                "double" -> DOUBLE
                "char" -> STRING
                "void" -> STRING
                else -> STRING
            }
        }
        
        if (psiType is com.intellij.psi.PsiArrayType) {
            val componentCanonical = psiType.componentType.canonicalText
            if (SpecialTypeHandler.isFileTypeCanonical(componentCanonical)) return "file[]"
            return ARRAY
        }
        
        return fromJavaType(canonical)
    }
}
