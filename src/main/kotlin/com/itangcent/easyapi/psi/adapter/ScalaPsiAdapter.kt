package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.doc.DocComment
import com.itangcent.easyapi.psi.doc.DocTag

/**
 * PSI adapter for Scala source files.
 *
 * Handles PSI element resolution for Scala classes. Delegates to
 * [JavaPsiAdapter] for operations where Scala PSI is compatible
 * with Java PSI (methods, fields, annotations, enum constants).
 *
 * ## Scala-specific differences from Java PSI
 *
 * - **resolveClass**: Scala files use `ScalaFileImpl` (not `PsiJavaFile`),
 *   so the Java adapter's `PsiJavaFile` branch won't match. This adapter
 *   uses reflection to access `ScalaFileImpl.classes` for file-level resolution.
 *
 * - **resolveDocComment**: Scala uses `ScDocComment` (not standard `PsiDocComment`).
 *   While `ScDocComment` implements `PsiDocComment`, the standard API doesn't
 *   correctly parse Scaladoc-specific syntax. This adapter uses reflection to
 *   access `ScDocComment.getTags()` and `ScDocTag.scalaAdaptor().getAllText()`
 *   for proper tag extraction, avoiding compile-time dependency on the Scala plugin.
 *
 * @see PsiLanguageAdapter for the interface
 * @see JavaPsiAdapter for the delegate implementation
 */
class ScalaPsiAdapter : PsiLanguageAdapter {

    private val delegate = JavaPsiAdapter()

    override fun supportsElement(element: PsiElement): Boolean {
        val id = element.containingFile?.language?.id ?: return false
        return id.equals("scala", true) || id.equals("Scala", true)
    }

    /**
     * Resolves a PsiClass from a Scala PSI element.
     *
     * Unlike Java where files are `PsiJavaFile`, Scala files are `ScalaFileImpl`.
     * The delegate handles `PsiClass` and parent-based resolution, but cannot
     * handle Scala files directly. This method adds Scala file support
     * via reflection to avoid a compile-time dependency on the Scala plugin.
     */
    override fun resolveClass(element: PsiElement): PsiClass? {
        // Delegate handles PsiClass and parent-based resolution
        val fromDelegate = delegate.resolveClass(element)
        if (fromDelegate != null) return fromDelegate

        // Handle ScalaFile — Scala files are not PsiJavaFile but have a `typeDefinitions` property
        return resolveClassFromScalaFile(element)
    }

    override fun resolveMethods(psiClass: PsiClass): List<PsiMethod> = delegate.resolveMethods(psiClass)

    override fun resolveFields(psiClass: PsiClass): List<PsiField> = delegate.resolveFields(psiClass)

    override fun resolveAnnotations(element: PsiElement): List<PsiAnnotation> = delegate.resolveAnnotations(element)

    override fun resolveDocComment(element: PsiElement): DocComment? {
        // For Scala light methods (ScFunctionWrapper), we need to navigate to the actual definition
        val actualElement = unwrapScalaLightElement(element) ?: element
        val scDocComment = findScDocComment(actualElement) ?: return delegate.resolveDocComment(actualElement)
        return parseScaladoc(scDocComment)
    }

    /**
     * Unwraps Scala light PSI elements to get the actual underlying definition.
     *
     * Scala uses light PSI wrappers (like ScFunctionWrapper) for methods, which don't
     * directly expose doc comments. We need to navigate to the actual Scala definition.
     */
    private fun unwrapScalaLightElement(element: PsiElement): PsiElement? {
        // Check if this is a ScFunctionWrapper (light method wrapper)
        val scFunctionWrapperClass = runCatching {
            Class.forName("org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper")
        }.getOrNull() ?: return null

        if (!scFunctionWrapperClass.isInstance(element)) return null

        // Get the delegate (actual Scala function definition)
        // The method is called "delegate()" not "getDelegate()"
        val delegateMethod = runCatching {
            scFunctionWrapperClass.getMethod("delegate")
        }.getOrNull() ?: return null

        return runCatching {
            delegateMethod.invoke(element) as? PsiElement
        }.getOrNull()
    }

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> = delegate.resolveEnumConstants(psiClass)

    private fun findScDocComment(element: PsiElement): Any? {
        val owner = element as? PsiDocCommentOwner ?: return null
        val docComment = owner.docComment ?: return null
        val scDocCommentClass = runCatching {
            Class.forName("org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment")
        }.getOrNull() ?: return null
        return if (scDocCommentClass.isInstance(docComment)) docComment else null
    }

    private fun parseScaladoc(scDocComment: Any): DocComment {
        val text = (scDocComment as PsiElement).text
        val tags = mutableListOf<DocTag>()

        val scDocCommentClass = scDocComment.javaClass
        val getTagsMethod = runCatching {
            scDocCommentClass.getMethod("getTags")
        }.getOrNull()

        if (getTagsMethod != null) {
            val docTags = runCatching { getTagsMethod.invoke(scDocComment) as? Array<*> }.getOrNull()
            docTags?.forEach { tag ->
                if (tag != null) {
                    val tagName = runCatching {
                        val nameMethod = tag.javaClass.getMethod("getName")
                        (nameMethod.invoke(tag) as? String)?.removePrefix("@")
                    }.getOrNull() ?: return@forEach

                    val tagValue = runCatching {
                        val adaptorMethod = tag.javaClass.getMethod("scalaAdaptor")
                        val adaptor = adaptorMethod.invoke(tag)
                        val getAllTextMethod = adaptor.javaClass.getMethod("getAllText")
                        getAllTextMethod.invoke(adaptor) as? String
                    }.getOrNull() ?: runCatching {
                        val valueElementMethod = tag.javaClass.getMethod("getValueElement")
                        val valueElement = valueElementMethod.invoke(tag) as? PsiElement
                        valueElement?.text ?: ""
                    }.getOrNull() ?: ""

                    if (tagName.isNotEmpty()) {
                        tags.add(DocTag(tagName, tagValue))
                    }
                }
            }
        }

        return DocComment(text = text, tags = tags)
    }

    /**
     * Resolves a PsiClass from a ScalaFile element via reflection.
     *
     * ScalaFile has a `typeDefinitions()` method that returns Scala type definitions,
     * and also inherits `getClasses()` from `PsiClassOwner`. We use reflection to
     * avoid requiring the Scala plugin at compile time.
     */
    private fun resolveClassFromScalaFile(element: PsiElement): PsiClass? {
        val file = element.containingFile ?: return null
        val scalaFileClass = runCatching {
            Class.forName("org.jetbrains.plugins.scala.lang.psi.api.ScalaFile")
        }.getOrNull() ?: return null

        if (!scalaFileClass.isInstance(file)) return null

        val getClassesMethod = runCatching {
            scalaFileClass.getMethod("getClasses")
        }.getOrNull() ?: return null

        val classes = runCatching {
            @Suppress("UNCHECKED_CAST")
            getClassesMethod.invoke(file) as? Array<PsiClass>
        }.getOrNull() ?: return null

        return classes.firstOrNull()
    }
}
