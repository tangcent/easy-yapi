package com.itangcent.easyapi.psi.model

/**
 * Handler interface for customizing JSON output from ObjectModel.
 *
 * Provides hooks for each stage of JSON generation:
 * - Object start/end
 * - Field before/after
 * - Array start/end
 * - Array item before/after
 * - Map handling
 * - Single value rendering
 *
 * Implementations can produce different JSON formats:
 * - Standard JSON (RawJsonHandler)
 * - JSON5 with comments (Json5Handler)
 *
 * @see RawJsonHandler for standard JSON output
 * @see Json5Handler for JSON5 with comments
 * @see ObjectModelJsonBuilder for usage
 */
interface ObjectModelJsonHandler {
    fun beforeObjectStart(builder: StringBuilder, indent: Int)
    
    fun afterObjectEnd(builder: StringBuilder, indent: Int)
    
    fun beforeObjectField(
        builder: StringBuilder,
        name: String,
        field: FieldModel,
        fieldIndex: Int,
        totalFields: Int,
        indent: Int
    )
    
    fun afterObjectField(
        builder: StringBuilder,
        name: String,
        field: FieldModel,
        fieldIndex: Int,
        totalFields: Int,
        indent: Int
    )
    
    fun beforeArrayStart(builder: StringBuilder, indent: Int)
    
    fun afterArrayEnd(builder: StringBuilder, indent: Int)
    
    fun beforeArrayItem(
        builder: StringBuilder,
        item: ObjectModel,
        itemIndex: Int,
        totalItems: Int,
        indent: Int
    )
    
    fun afterArrayItem(
        builder: StringBuilder,
        item: ObjectModel,
        itemIndex: Int,
        totalItems: Int,
        indent: Int
    )
    
    fun beforeMapStart(builder: StringBuilder, indent: Int)
    
    fun afterMapEnd(builder: StringBuilder, indent: Int)

    fun beforeMapKey(builder: StringBuilder, indent: Int)

    fun betweenMapKeyAndValue(builder: StringBuilder, indent: Int)

    fun afterMapValue(builder: StringBuilder, indent: Int)
    
    fun handleSingleValue(
        builder: StringBuilder,
        value: ObjectModel.Single,
        indent: Int
    )
}
