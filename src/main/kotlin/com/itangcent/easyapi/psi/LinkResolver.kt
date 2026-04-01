package com.itangcent.easyapi.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope

/**
 * Utility for resolving link references in documentation and scripts.
 *
 * Supports multiple link formats:
 * - `{@link ClassName}` - JavaDoc link format
 * - `{@link ClassName#member}` - JavaDoc link with member
 * - `[ClassName]` - Kotlin KDoc format
 * - `[ClassName.member]` - Kotlin KDoc with member
 * - `ClassName#member` - Direct reference format
 * - `ClassName.member` - Dot notation format
 *
 * ## Resolution Strategy
 * 1. Parse the link reference into class name and optional member name
 * 2. Resolve the class by trying:
 *    - Fully qualified name
 *    - Same package as context element
 *    - Import statements in the context file
 * 3. Resolve the member (field/method) if specified
 *
 * ## Usage
 * ```kotlin
 * val resolver = LinkResolver.getInstance(project)
 * 
 * // Resolve a class
 * val psiClass = resolver.resolveClass("com.example.User", contextElement)
 * 
 * // Resolve a member
 * val member = resolver.resolveMember("com.example.User#name", contextElement)
 * 
 * // Extract all links from text
 * val links = resolver.extractLinks("{@link User#name} and {@link Order}")
 * ```
 */
@Service(Service.Level.PROJECT)
class LinkResolver(private val project: Project) {

    /**
     * Parsed result of a link reference.
     */
    data class LinkReference(
        val className: String,
        val memberName: String?
    )

    /**
     * Parse a link reference text into class name and optional member.
     *
     * Supported formats:
     * - `ClassName` → (ClassName, null)
     * - `com.example.ClassName` → (com.example.ClassName, null)
     * - `ClassName#field` → (ClassName, field)
     * - `com.example.ClassName#method()` → (com.example.ClassName, method)
     * - `ClassName.field` → (ClassName, field)
     * - `com.example.ClassName.method()` → (com.example.ClassName, method)
     */
    fun parseLinkReference(linkRef: String): LinkReference? = Companion.parseLinkReference(linkRef)

    /**
     * Resolve a class by name, trying multiple strategies.
     *
     * Resolution order:
     * 1. Fully qualified name
     * 2. Same package as context element
     * 3. Import statements in context file
     */
    fun resolveClass(className: String, contextElement: PsiElement): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        return facade.findClass(className, scope)
            ?: resolveClassFromPackage(className, contextElement, facade, scope)
            ?: resolveClassFromImports(className, contextElement, facade, scope)
    }

    /**
     * Resolve a link reference to a PSI element (class, field, or method).
     *
     * @param linkRef The link reference text (e.g., "com.example.User#name")
     * @param contextElement The context element for import resolution
     * @return The resolved PSI element, or null if not found
     */
    fun resolveLink(linkRef: String, contextElement: PsiElement): PsiElement? {
        val parsed = parseLinkReference(linkRef) ?: return null
        val psiClass = resolveClass(parsed.className, contextElement) ?: return null

        // If no member specified, return the class
        if (parsed.memberName == null) {
            return psiClass
        }

        // Try to resolve the member
        val memberName = parsed.memberName.removeSuffix("()")

        // Try field first
        val field = psiClass.findFieldByName(memberName, false)
        if (field != null) return field

        // Try method
        val methods = psiClass.findMethodsByName(memberName, false)
        if (methods.isNotEmpty()) return methods.first()

        // Try getter-to-property conversion
        val getterField = getterToPropertyName(memberName)
        if (getterField != null) {
            val getterFieldResult = psiClass.findFieldByName(getterField, false)
            if (getterFieldResult != null) return getterFieldResult
        }

        return null
    }

    /**
     * Extract all link references from text.
     *
     * Supports both JavaDoc `{@link ...}` and Kotlin KDoc `[...]` formats.
     *
     * @param text The text containing link references
     * @return List of extracted link reference texts
     */
    fun extractLinks(text: String): List<String> {
        val results = mutableListOf<String>()
        val linkPattern = Regex("""\{@link\s+([^}]+)}""")
        val kdocPattern = Regex("""\[([^\]]+)]""")

        linkPattern.findAll(text).forEach { match ->
            val linkRef = match.groupValues[1].trim()
            results.add(linkRef)
        }

        kdocPattern.findAll(text).forEach { match ->
            val linkRef = match.groupValues[1].trim()
            // Only include KDoc links that look like class references
            if (linkRef.contains(".") || linkRef.contains("#")) {
                results.add(linkRef)
            }
        }

        return results
    }

    /**
     * Resolve all links in text to PSI elements.
     *
     * @param text The text containing link references
     * @param contextElement The context element for resolution
     * @return List of resolved PSI elements
     */
    fun resolveAllLinks(text: String, contextElement: PsiElement): List<PsiElement> {
        return extractLinks(text)
            .mapNotNull { resolveLink(it, contextElement) }
    }

    private fun resolveClassFromPackage(
        className: String,
        contextElement: PsiElement,
        facade: JavaPsiFacade,
        scope: GlobalSearchScope
    ): PsiClass? {
        val containingFile = contextElement.containingFile as? PsiJavaFile ?: return null
        val packageName = containingFile.packageName
        if (packageName.isBlank()) return null
        return facade.findClass("$packageName.$className", scope)
    }

    private fun resolveClassFromImports(
        className: String,
        contextElement: PsiElement,
        facade: JavaPsiFacade,
        scope: GlobalSearchScope
    ): PsiClass? {
        val containingFile = contextElement.containingFile as? PsiJavaFile ?: return null
        val imports = containingFile.importList?.importStatements ?: return null

        for (importStmt in imports) {
            val importRef = importStmt.qualifiedName ?: continue
            val candidate = when {
                importRef.endsWith(".$className") -> importRef
                importRef.endsWith(".*") -> importRef.removeSuffix("*") + className
                else -> continue
            }
            facade.findClass(candidate, scope)?.let { return it }
        }

        return null
    }

    companion object {
        /**
         * Parse a link reference text into class name and optional member.
         * Static version that doesn't require a project instance.
         *
         * Supported formats:
         * - `ClassName` → (ClassName, null)
         * - `com.example.ClassName` → (com.example.ClassName, null)
         * - `ClassName#field` → (ClassName, field)
         * - `com.example.ClassName#method()` → (com.example.ClassName, method)
         * - `ClassName.field` → (ClassName, field)
         * - `com.example.ClassName.method()` → (com.example.ClassName, method)
         * - `{@link ClassName}` → (ClassName, null)
         * - `[ClassName]` → (ClassName, null)
         */
        fun parseLinkReference(linkRef: String): LinkReference? {
            var ref = linkRef.trim()
            if (ref.isBlank()) return null

            // Strip {@link ...} wrapper (Java)
            if (ref.startsWith("{@link")) {
                ref = ref.removePrefix("{@link").trimStart()
                if (ref.endsWith("}")) {
                    ref = ref.removeSuffix("}").trimEnd()
                }
            }
            // Strip [...] wrapper (Kotlin KDoc)
            else if (ref.startsWith("[") && ref.endsWith("]")) {
                ref = ref.removePrefix("[").removeSuffix("]").trim()
            }

            if (ref.isBlank()) return null

            // Split on '#' first (standard javadoc separator)
            if (ref.contains('#')) {
                val className = ref.substringBefore('#').trim()
                val member = ref.substringAfter('#').trim()
                    .removeSuffix("()")
                    .takeIf { it.isNotBlank() }
                if (className.isNotBlank()) {
                    return LinkReference(className, member)
                }
                return null
            }

            // Handle dot-separated property: `Xxx.field`
            // Heuristic: if the last segment starts with a lowercase letter, treat it as a property.
            val lastDot = ref.lastIndexOf('.')
            if (lastDot > 0) {
                val afterDot = ref.substring(lastDot + 1)
                if (afterDot.isNotBlank() && afterDot[0].isLowerCase()) {
                    val className = ref.substring(0, lastDot).trim()
                    val member = afterDot.trim().removeSuffix("()")
                    if (className.isNotBlank()) {
                        return LinkReference(className, member)
                    }
                }
            }

            return LinkReference(ref, null)
        }

        /**
         * Get the LinkResolver instance for a project.
         */
        fun getInstance(project: Project): LinkResolver = project.getService(LinkResolver::class.java)

        /**
         * Convert a getter method name to a property name.
         * - `getType` → `type`
         * - `isActive` → `active`
         * - `name` → null (not a getter)
         */
        fun getterToPropertyName(name: String): String? {
            if (name.startsWith("get") && name.length > 3 && name[3].isUpperCase()) {
                return name[3].lowercaseChar() + name.substring(4)
            }
            if (name.startsWith("is") && name.length > 2 && name[2].isUpperCase()) {
                return name[2].lowercaseChar() + name.substring(3)
            }
            return null
        }
    }
}
