package com.itangcent.easyapi.psi.helper

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.javadoc.PsiDocComment

/**
 * Interface for extracting documentation from PSI elements.
 * 
 * Provides methods for parsing Javadoc comments and extracting
 * tags, attributes, and descriptions from code elements.
 * 
 * Implementations handle the complexity of parsing different
 * documentation formats (Javadoc, KDoc, etc.).
 */
interface DocHelper {
    /**
     * Extracts all tags and their values from a PSI element's documentation.
     * 
     * @param psiElement The element to extract documentation from
     * @return Map of tag names to their values
     */
    suspend fun getTagMapOfDocComment(psiElement: PsiElement?): Map<String, String?>

    /**
     * Extracts sub-tags from a specific tag in a PSI element's documentation.
     * 
     * @param psiElement The element to extract documentation from
     * @param tag The parent tag name
     * @return Map of sub-tag names to their values
     */
    suspend fun getSubTagMapOfDocComment(psiElement: PsiElement?, tag: String): Map<String, String?>

    /**
     * Extracts the main description/attribute from a PSI element's documentation.
     * 
     * @param psiElement The element to extract documentation from
     * @return The description text, or null if not available
     */
    suspend fun getAttrOfDocComment(psiElement: PsiElement?): String?

    /**
     * Gets the text content of a documentation comment.
     * 
     * @param docComment The documentation comment
     * @return The text content without Javadoc markers
     */
    fun getDocCommentContent(docComment: PsiDocComment): String?

    /**
     * Finds documentation by tag name and parameter name.
     * 
     * @param psiElement The element to search
     * @param tag The tag name (e.g., "param")
     * @param name The parameter name
     * @return The documentation text, or null if not found
     */
    suspend fun findDocsByTagAndName(psiElement: PsiElement?, tag: String, name: String): String?

    /**
     * Finds all documentation values for a specific tag.
     * 
     * @param psiElement The element to search
     * @param tag The tag name
     * @return List of documentation values for the tag
     */
    suspend fun findDocsByTag(psiElement: PsiElement?, tag: String?): List<String>?

    /**
     * Finds the first documentation value for a specific tag.
     * 
     * @param psiElement The element to search
     * @param tag The tag name
     * @return The first documentation value, or null if not found
     */
    suspend fun findDocByTag(psiElement: PsiElement?, tag: String?): String?

    /**
     * Checks if a PSI element has a specific documentation tag.
     * 
     * @param psiElement The element to check
     * @param tag The tag name to look for
     * @return true if the tag exists
     */
    suspend fun hasTag(psiElement: PsiElement?, tag: String?): Boolean

    /**
     * Gets the end-of-line comment for a PSI element.
     * 
     * @param psiElement The element to get comment for
     * @return The comment text, or null if not available
     */
    fun getEolComment(psiElement: PsiElement): String?

    /**
     * Gets the description/attribute for a field.
     * Combines Javadoc and end-of-line comments.
     * 
     * @param field The field to get description for
     * @return The field description, or null if not available
     */
    suspend fun getAttrOfField(field: PsiField): String?
}