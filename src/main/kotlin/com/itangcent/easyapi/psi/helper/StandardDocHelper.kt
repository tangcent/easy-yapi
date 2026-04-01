package com.itangcent.easyapi.psi.helper

import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.itangcent.easyapi.core.threading.IdeDispatchers
import kotlinx.coroutines.withContext
import java.util.*

const val COMMENT_PREFIX = "//"

val BLOCK_COMMENT_REGEX =
    Regex(
        "/\\*+(.*?)\\**/",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )

val DOC_COMMENT_PREFIXES = listOf<String>(
    "*", "///", "//"
)

class StandardDocHelper : DocHelper {

    private suspend fun <T> readAction(block: () -> T): T {
        return withContext(IdeDispatchers.ReadAction) { block() }
    }

    override suspend fun hasTag(psiElement: PsiElement?, tag: String?): Boolean {
        return readAction {
            docComment(psiElement) { docComment ->
                val tags = docComment.findTagByName(tag)
                tags != null
            } ?: false
        }
    }

    override suspend fun findDocByTag(psiElement: PsiElement?, tag: String?): String? {
        return readAction {
            docComment(psiElement) { docComment ->
                val tags = docComment.findTagsByName(tag)
                if (tags.isEmpty()) return@docComment null
                for (paramDocTag in tags) {
                    val text = paramDocTag.docValue()
                    if (text.isNotBlank()) {
                        return@docComment text
                    }
                }
                null
            }
        }
    }

    private fun PsiDocTag.docValue(discardName: Boolean = false): String {
        val lines = this.text.lines()
        if (lines.isEmpty()) return ""
        var ret = lines[0].removePrefix(this.nameElement.text).trimStart()
        if (discardName) {
            this.valueElement?.text?.let {
                ret = ret.removePrefix(it).trimStart()
            }
        }
        if (lines.size == 1) {
            return ret
        }
        for (i in 1 until lines.size) {
            lines[i].trim()
                .removeCommentPrefix()
                .takeIf { it.isNotBlank() }
                ?.let {
                    ret = "$ret\n$it"
                }
        }
        return ret
    }

    private fun String.removeCommentPrefix(): String {
        for (prefix in DOC_COMMENT_PREFIXES) {
            if (this.startsWith(prefix)) {
                return this.removePrefix(prefix).trim()
            }
        }
        return this
    }

    override suspend fun findDocsByTag(psiElement: PsiElement?, tag: String?): List<String>? {
        return readAction {
            docComment(psiElement) { docComment ->
                val tags = docComment.findTagsByName(tag)
                if (tags.isEmpty()) return@docComment null
                val res: LinkedList<String> = LinkedList()
                for (paramDocTag in tags) {
                    val data = paramDocTag.docValue()
                    if (data.isNotBlank()) {
                        res.add(data)
                    }
                }
                res
            }
        }
    }

    override suspend fun findDocsByTagAndName(psiElement: PsiElement?, tag: String, name: String): String? {
        val tagAttr = "@$tag"
        return readAction {
            docComment(psiElement) { docComment ->
                loopTags@ for (paramDocTag in docComment.findTagsByName(tag)) {
                    if (paramDocTag.nameElement.text != tagAttr) {
                        continue@loopTags
                    }
                    if (paramDocTag.valueElement?.text != name) {
                        continue@loopTags
                    }

                    return@docComment paramDocTag.docValue(true)
                }
                null
            }
        }
    }

    override suspend fun getAttrOfDocComment(psiElement: PsiElement?): String? {
        return readAction {
            docComment(psiElement) { docComment ->
                getDocCommentContent(docComment)
            }
        }
    }

    override fun getDocCommentContent(docComment: PsiDocComment): String? {
        val descriptions = docComment.descriptionElements
        val text = descriptions.joinToString(separator = "") { desc -> desc.tinyText() }.trim()
        val lines = text.lines()
        return lines.asSequence()
            .map { it.removePrefix(" ").trimEnd() }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun PsiElement.tinyText(): String {
        val text = this.text
        if (!text.isNullOrBlank()) {
            return text.trimEnd()
        }
        if (text.contains("\n")) {
            return "\n"
        }
        return text
    }

    override suspend fun getSubTagMapOfDocComment(psiElement: PsiElement?, tag: String): Map<String, String?> {
        return readAction {
            docComment(psiElement) { docComment ->
                val subTagMap: HashMap<String, String?> = HashMap()
                for (paramDocTag in docComment.findTagsByName(tag)) {
                    paramDocTag.valueElement?.text?.let {
                        subTagMap[it] = paramDocTag.docValue(true)
                    }
                }
                subTagMap
            } ?: emptyMap()
        }
    }

    override suspend fun getTagMapOfDocComment(psiElement: PsiElement?): Map<String, String?> {
        return readAction {
            docComment(psiElement) { docComment ->
                val tagMap: HashMap<String, String?> = HashMap()
                docComment.tags.forEach { tag ->
                    tagMap[tag.name] = tag.docValue()
                }
                tagMap
            } ?: emptyMap()
        }
    }

    override fun getEolComment(psiElement: PsiElement): String? {
        val text = psiElement.text ?: return null

        if (text.contains(COMMENT_PREFIX)) {
            return psiElement.children
                .asSequence()
                .findEolComment { false }
        }

        psiElement.nextSiblings()
            .findEolComment()
            ?.let {
                return it
            }

        return null
    }

    override suspend fun getAttrOfField(field: PsiField): String {
        val attrInDoc = getAttrOfDocComment(field)
        val eolComment = getEolComment(field)?.takeIf { it != attrInDoc }
        return listOfNotNull(attrInDoc, eolComment)
            .distinct()
            .joinToString("\n")
    }

    private fun Sequence<PsiElement>.findEolComment(
        stopInUnexpectedElement: (PsiElement) -> Boolean = { true }
    ): String? {
        for (next in this) {
            when {
                next.isWhiteSpace() -> {
                    if (next.text?.contains('\n') == true) {
                        return null
                    }
                    continue
                }

                next is PsiComment -> {
                    return next.eolComment()
                }
            }
            if (stopInUnexpectedElement(next)) {
                return null
            }
        }

        return null
    }

    private fun PsiElement.isWhiteSpace() = this is PsiWhiteSpace

    private fun PsiComment.eolComment(): String? {
        if (this.tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
            return text.trim().removePrefix(COMMENT_PREFIX).trimStart()
        }
        return null
    }

    protected fun PsiElement.prevSiblings() = generateSequence { it.prevSibling }

    protected fun PsiElement.nextSiblings() = generateSequence { it.nextSibling }

    protected fun PsiElement.generateSequence(nextFunction: (PsiElement) -> PsiElement?): Sequence<PsiElement> {
        return generateSequence(nextFunction(this), nextFunction)
    }

    private inline fun <T : PsiElement, R> docComment(psiElement: T?, action: (PsiDocComment) -> R): R? {
        if (psiElement is PsiDocCommentOwner) {
            return psiElement.docComment?.let { action(it) }
        }
        return null
    }
}