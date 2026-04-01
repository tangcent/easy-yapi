package com.itangcent.easyapi.psi.model

/**
 * Utility extensions for [ObjectModel].
 */
object ObjectModelUtils {

    /**
     * Adds or appends a comment to a specific field within an [ObjectModel.Object],
     * supporting dotted paths (e.g., "data", "data.items").
     *
     * Used by `method.return.main` rule to attach `@return` doc comments
     * to a designated field in the response model.
     *
     * @param model The root object model
     * @param fieldPath Dot-separated field path (e.g., "data" or "data.name")
     * @param comment The comment text to attach
     * @return The updated model with the comment applied, or null if the field path was not found
     */
    fun addFieldComment(model: ObjectModel, fieldPath: String, comment: String): ObjectModel? {
        val obj = model.asObject() ?: return null
        if (fieldPath.contains(".")) {
            val head = fieldPath.substringBefore('.')
            val rest = fieldPath.substringAfter('.')
            val fieldModel = obj.fields[head] ?: return null
            val updatedInner = addFieldComment(fieldModel.model, rest, comment) ?: return null
            val updatedFields = obj.fields.toMutableMap()
            updatedFields[head] = fieldModel.copy(model = updatedInner)
            return ObjectModel.Object(updatedFields)
        }
        val fieldModel = obj.fields[fieldPath] ?: return null
        val existingComment = fieldModel.comment
        val newComment = if (existingComment.isNullOrBlank()) comment
        else "$existingComment\n$comment"
        val updatedFields = obj.fields.toMutableMap()
        updatedFields[fieldPath] = fieldModel.copy(comment = newComment)
        return ObjectModel.Object(updatedFields)
    }

    /**
     * Finds the name of the first field marked as generic (type parameter) in an [ObjectModel.Object].
     *
     * Useful for auto-detecting the "main" field in wrapper types like `Result<T>`
     * where the generic field (e.g., `data: T`) is the primary payload.
     *
     * @param model The object model to search
     * @return The field name, or null if no generic field is found
     */
    fun findGenericFieldName(model: ObjectModel): String? {
        val obj = model.asObject() ?: return null
        return obj.fields.entries.firstOrNull { it.value.generic }?.key
    }
}
