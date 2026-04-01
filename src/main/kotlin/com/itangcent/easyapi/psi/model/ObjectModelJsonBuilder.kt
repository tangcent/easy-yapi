package com.itangcent.easyapi.psi.model

/**
 * Builds JSON strings from ObjectModel structures.
 *
 * Uses a handler-based approach to support different JSON formats:
 * - Standard JSON (via RawJsonHandler)
 * - JSON5 with comments (via Json5Handler)
 *
 * Handles circular references by tracking visited objects via their unique id.
 *
 * @param handler The handler for JSON generation
 * @param maxVisits Maximum times an object can be visited (for circular reference handling)
 * @see ObjectModelJsonHandler for handler interface
 * @see ObjectModelJsonConverter for convenience methods
 */
class ObjectModelJsonBuilder(
    private val handler: ObjectModelJsonHandler,
    private val maxVisits: Int = 2
) {
    
    fun build(model: ObjectModel?): String {
        if (model == null) return "{}"
        val builder = StringBuilder()
        val visitCounts = HashMap<Int, Int>()
        buildValue(model, builder, 0, visitCounts)
        return builder.toString()
    }
    
    private fun buildValue(
        model: ObjectModel,
        builder: StringBuilder,
        indent: Int,
        visitCounts: HashMap<Int, Int>
    ) {
        when (model) {
            is ObjectModel.Single -> handler.handleSingleValue(builder, model, indent)
            is ObjectModel.Object -> buildObject(model, builder, indent, visitCounts)
            is ObjectModel.Array -> buildArray(model, builder, indent, visitCounts)
            is ObjectModel.MapModel -> buildMap(model, builder, indent, visitCounts)
        }
    }
    
    private fun buildObject(
        model: ObjectModel.Object,
        builder: StringBuilder,
        indent: Int,
        visitCounts: HashMap<Int, Int>
    ) {
        val count = visitCounts.getOrDefault(model.id, 0)
        if (count >= maxVisits) {
            handler.beforeObjectStart(builder, indent)
            handler.afterObjectEnd(builder, indent)
            return
        }
        visitCounts[model.id] = count + 1

        handler.beforeObjectStart(builder, indent)
        
        val fields = model.fields.entries.toList()
        fields.forEachIndexed { index, (name, field) ->
            handler.beforeObjectField(builder, name, field, index, fields.size, indent)
            buildValue(field.model, builder, indent + 1, visitCounts)
            handler.afterObjectField(builder, name, field, index, fields.size, indent)
        }
        
        handler.afterObjectEnd(builder, indent)
        visitCounts[model.id] = count
    }
    
    private fun buildArray(
        model: ObjectModel.Array,
        builder: StringBuilder,
        indent: Int,
        visitCounts: HashMap<Int, Int>
    ) {
        handler.beforeArrayStart(builder, indent)
        handler.beforeArrayItem(builder, model.item, 0, 1, indent)
        buildValue(model.item, builder, indent + 1, visitCounts)
        handler.afterArrayItem(builder, model.item, 0, 1, indent)
        handler.afterArrayEnd(builder, indent)
    }
    
    private fun buildMap(
        model: ObjectModel.MapModel,
        builder: StringBuilder,
        indent: Int,
        visitCounts: HashMap<Int, Int>
    ) {
        handler.beforeMapStart(builder, indent)
        handler.beforeMapKey(builder, indent)
        buildValue(model.keyType, builder, indent + 1, visitCounts)
        handler.betweenMapKeyAndValue(builder, indent)
        buildValue(model.valueType, builder, indent + 1, visitCounts)
        handler.afterMapValue(builder, indent)
        handler.afterMapEnd(builder, indent)
    }
}
