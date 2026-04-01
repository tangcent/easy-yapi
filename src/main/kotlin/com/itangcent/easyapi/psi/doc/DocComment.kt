package com.itangcent.easyapi.psi.doc

/**
 * Represents a parsed documentation comment.
 *
 * Contains the raw comment text and extracted tags.
 * Used for parsing Javadoc/Kdoc comments from PSI elements.
 *
 * ## Example
 * For a Javadoc comment like:
 * ```
 * /**
 *  * Gets user by ID.
 *  * @param id the user ID
 *  * @return the user
 *  */
 * ```
 *
 * The parsed result would be:
 * ```kotlin
 * DocComment(
 *     text = "/**\n * Gets user by ID.\n * @param id the user ID\n * @return the user\n */",
 *     tags = [
 *         DocTag("param", "id the user ID"),
 *         DocTag("return", "the user")
 *     ]
 * )
 * ```
 *
 * @param text The raw comment text
 * @param tags The parsed documentation tags
 * @see DocTag for tag representation
 * @see com.itangcent.easyapi.psi.adapter.PsiLanguageAdapter for comment parsing
 */
data class DocComment(
    val text: String,
    val tags: List<DocTag> = emptyList()
)

/**
 * Represents a parsed documentation tag.
 *
 * Documentation tags are special annotations in Javadoc/Kdoc
 * like `@param`, `@return`, `@see`, etc.
 *
 * ## Examples
 * - `@param id the user ID` → `DocTag("param", "id the user ID")`
 * - `@return the user` → `DocTag("return", "the user")`
 * - `@deprecated` → `DocTag("deprecated", "")`
 *
 * @param name The tag name (without the @ symbol)
 * @param value The tag value (name + description)
 */
data class DocTag(
    val name: String,
    val value: String
)
