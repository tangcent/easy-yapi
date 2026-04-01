package com.itangcent.easyapi.psi.helper

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.javadoc.PsiDocComment
import kotlinx.coroutines.runBlocking

/**
 * Blocking wrapper for [DocHelper].
 *
 * Provides synchronous versions of DocHelper methods using `runBlocking`.
 * Use only when you must call suspend functions from non-suspend contexts.
 *
 * **Warning**: Using this in UI threads may cause freezes.
 * Prefer using [DocHelper] directly in coroutines when possible.
 *
 * @see DocHelper for the underlying suspend-based interface
 */
class BlockingDocHelper(private val delegate: DocHelper) {

    fun getTagMapOfDocComment(psiElement: PsiElement?): Map<String, String?> {
        return runBlocking { delegate.getTagMapOfDocComment(psiElement) }
    }

    fun getSubTagMapOfDocComment(psiElement: PsiElement?, tag: String): Map<String, String?> {
        return runBlocking { delegate.getSubTagMapOfDocComment(psiElement, tag) }
    }

    fun getAttrOfDocComment(psiElement: PsiElement?): String? {
        return runBlocking { delegate.getAttrOfDocComment(psiElement) }
    }

    fun getDocCommentContent(docComment: PsiDocComment): String? {
        return delegate.getDocCommentContent(docComment)
    }

    fun findDocsByTagAndName(psiElement: PsiElement?, tag: String, name: String): String? {
        return runBlocking { delegate.findDocsByTagAndName(psiElement, tag, name) }
    }

    fun findDocsByTag(psiElement: PsiElement?, tag: String?): List<String>? {
        return runBlocking { delegate.findDocsByTag(psiElement, tag) }
    }

    fun findDocByTag(psiElement: PsiElement?, tag: String?): String? {
        return runBlocking { delegate.findDocByTag(psiElement, tag) }
    }

    fun hasTag(psiElement: PsiElement?, tag: String?): Boolean {
        return runBlocking { delegate.hasTag(psiElement, tag) }
    }

    fun getEolComment(psiElement: PsiElement): String? {
        return delegate.getEolComment(psiElement)
    }

    fun getAttrOfField(field: PsiField): String? {
        return runBlocking { delegate.getAttrOfField(field) }
    }
}

/**
 * Blocking wrapper for [AnnotationHelper].
 *
 * Provides synchronous versions of AnnotationHelper methods using `runBlocking`.
 * Use only when you must call suspend functions from non-suspend contexts.
 *
 * **Warning**: Using this in UI threads may cause freezes.
 * Prefer using [AnnotationHelper] directly in coroutines when possible.
 *
 * @see AnnotationHelper for the underlying suspend-based interface
 */
class BlockingAnnotationHelper(private val delegate: AnnotationHelper) {

    fun hasAnn(element: PsiElement, annFqn: String): Boolean {
        return runBlocking { delegate.hasAnn(element, annFqn) }
    }

    fun findAnnMap(element: PsiElement, annFqn: String): Map<String, Any?>? {
        return runBlocking { delegate.findAnnMap(element, annFqn) }
    }

    fun findAnnMaps(element: PsiElement, annFqn: String): List<Map<String, Any?>>? {
        return runBlocking { delegate.findAnnMaps(element, annFqn) }
    }

    fun findAttr(element: PsiElement, annFqn: String, attr: String): Any? {
        return runBlocking { delegate.findAttr(element, annFqn, attr) }
    }

    fun findAttrAsString(element: PsiElement, annFqn: String, attr: String): String? {
        return runBlocking { delegate.findAttrAsString(element, annFqn, attr) }
    }
}