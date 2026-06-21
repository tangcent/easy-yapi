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
 * @see ObjectModelJsonHandler for handler interface
 * @see ObjectModelJsonConverter for convenience methods
 */
class ObjectModelJsonBuilder(
    private val handler: ObjectModelJsonHandler
) {
    
    fun build(model: ObjectModel?): String {
        if (model == null) return "{}"
        val builder = StringBuilder()
        val tracker = ObjectModelVisitTracker()
        buildValue(model, builder, 0, tracker)
        return builder.toString()
    }
    
    private fun buildValue(
        model: ObjectModel,
        builder: StringBuilder,
        indent: Int,
        tracker: ObjectModelVisitTracker
    ) {
        when (model) {
            is ObjectModel.Single -> handler.handleSingleValue(builder, model, indent)
            is ObjectModel.Object -> buildObject(model, builder, indent, tracker)
            is ObjectModel.Array -> buildArray(model, builder, indent, tracker)
            is ObjectModel.MapModel -> buildMap(model, builder, indent, tracker)
        }
    }
    
    private fun buildObject(
        model: ObjectModel.Object,
        builder: StringBuilder,
        indent: Int,
        tracker: ObjectModelVisitTracker
    ) {
        if (!tracker.tryEnter(model)) {
            handler.beforeObjectStart(builder, indent)
            handler.afterObjectEnd(builder, indent)
            return
        }

        try {
            handler.beforeObjectStart(builder, indent)
            
            val fields = model.fields.entries.toList()
            fields.forEachIndexed { index, (name, field) ->
                handler.beforeObjectField(builder, name, field, index, fields.size, indent)
                buildValue(field.model, builder, indent + 1, tracker)
                handler.afterObjectField(builder, name, field, index, fields.size, indent)
            }
            
            handler.afterObjectEnd(builder, indent)
        } finally {
            tracker.exit(model)
        }
    }
    
    private fun buildArray(
        model: ObjectModel.Array,
        builder: StringBuilder,
        indent: Int,
        tracker: ObjectModelVisitTracker
    ) {
        handler.beforeArrayStart(builder, indent)
        handler.beforeArrayItem(builder, model.item, 0, 1, indent)
        buildValue(model.item, builder, indent + 1, tracker)
        handler.afterArrayItem(builder, model.item, 0, 1, indent)
        handler.afterArrayEnd(builder, indent)
    }
    
    private fun buildMap(
        model: ObjectModel.MapModel,
        builder: StringBuilder,
        indent: Int,
        tracker: ObjectModelVisitTracker
    ) {
        handler.beforeMapStart(builder, indent)
        handler.beforeMapKey(builder, indent)
        buildValue(model.keyType, builder, indent + 1, tracker)
        handler.betweenMapKeyAndValue(builder, indent)
        buildValue(model.valueType, builder, indent + 1, tracker)
        handler.afterMapValue(builder, indent)
        handler.afterMapEnd(builder, indent)
    }
}
