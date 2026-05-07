package com.itangcent.easyapi.psi

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class LinkResolverSourceResolutionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var linkResolver: LinkResolver

    override fun setUp() {
        super.setUp()
        linkResolver = LinkResolver.getInstance(project)
    }

    // --- resolveClass: source-level class ---

    fun testResolveClassByFqn() {
        loadFile("linkres/ResolvedClass.java", """
            package com.test.linkres;
            /**
             * A resolvable class.
             */
            public class ResolvedClass {}
        """.trimIndent())
        val contextClass = loadFile("linkres/ContextClass.java", """
            package com.test.linkres;
            public class ContextClass {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.ContextClass")!!
        val result = linkResolver.resolveClass("com.test.linkres.ResolvedClass", contextElement)
        assertNotNull("Should resolve class by fully qualified name", result)
        assertEquals("com.test.linkres.ResolvedClass", result!!.qualifiedName)
    }

    // --- resolveClass: same package ---

    fun testResolveClassFromSamePackage() {
        loadFile("linkres/SamePackageTarget.java", """
            package com.test.linkres;
            /**
             * A target in the same package.
             */
            public class SamePackageTarget {}
        """.trimIndent())
        loadFile("linkres/SamePackageContext.java", """
            package com.test.linkres;
            public class SamePackageContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.SamePackageContext")!!
        val result = linkResolver.resolveClass("SamePackageTarget", contextElement)
        assertNotNull("Should resolve class from same package", result)
        assertEquals("com.test.linkres.SamePackageTarget", result!!.qualifiedName)
    }

    // --- resolveClass: from import ---

    fun testResolveClassFromImport() {
        loadFile("linkres/ImportedTarget.java", """
            package com.test.linkres.imported;
            /**
             * An imported target class.
             */
            public class ImportedTarget {}
        """.trimIndent())
        loadFile("linkres/ImportContext.java", """
            package com.test.linkres;
            import com.test.linkres.imported.ImportedTarget;
            public class ImportContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.ImportContext")!!
        val result = linkResolver.resolveClass("ImportedTarget", contextElement)
        assertNotNull("Should resolve class from import statement", result)
        assertEquals("com.test.linkres.imported.ImportedTarget", result!!.qualifiedName)
    }

    // --- resolveClass: not found ---

    fun testResolveClassReturnsNullForUnknownClass() {
        loadFile("linkres/NotFoundContext.java", """
            package com.test.linkres;
            public class NotFoundContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.NotFoundContext")!!
        val result = linkResolver.resolveClass("com.test.linkres.NonExistentClass", contextElement)
        assertNull("Should return null for unknown class", result)
    }

    // --- resolveLink: class only ---

    fun testResolveLinkClassOnly() {
        loadFile("linkres/LinkTargetClass.java", """
            package com.test.linkres;
            /**
             * A link target.
             */
            public class LinkTargetClass {}
        """.trimIndent())
        loadFile("linkres/LinkContext.java", """
            package com.test.linkres;
            public class LinkContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.LinkContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.LinkTargetClass", contextElement)
        assertNotNull("Should resolve link to class", result)
        assertTrue("Result should be a PsiClass", result is com.intellij.psi.PsiClass)
        assertEquals("com.test.linkres.LinkTargetClass", (result as com.intellij.psi.PsiClass).qualifiedName)
    }

    // --- resolveLink: class with field ---

    fun testResolveLinkClassWithField() {
        loadFile("linkres/LinkFieldTarget.java", """
            package com.test.linkres;
            /**
             * A target with fields.
             */
            public class LinkFieldTarget {
                /** The status code. */
                private int statusCode;
            }
        """.trimIndent())
        loadFile("linkres/LinkFieldContext.java", """
            package com.test.linkres;
            public class LinkFieldContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.LinkFieldContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.LinkFieldTarget#statusCode", contextElement)
        assertNotNull("Should resolve link to field", result)
        assertTrue("Result should be a PsiField", result is com.intellij.psi.PsiField)
        assertEquals("statusCode", (result as com.intellij.psi.PsiField).name)
    }

    // --- resolveLink: class with method ---

    fun testResolveLinkClassWithMethod() {
        loadFile("linkres/LinkMethodTarget.java", """
            package com.test.linkres;
            /**
             * A target with methods.
             */
            public class LinkMethodTarget {
                /**
                 * Gets the status.
                 * @return the status
                 */
                public int getStatus() { return 0; }
            }
        """.trimIndent())
        loadFile("linkres/LinkMethodContext.java", """
            package com.test.linkres;
            public class LinkMethodContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.LinkMethodContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.LinkMethodTarget#getStatus()", contextElement)
        assertNotNull("Should resolve link to method", result)
        assertTrue("Result should be a PsiMethod", result is com.intellij.psi.PsiMethod)
        assertEquals("getStatus", (result as com.intellij.psi.PsiMethod).name)
    }

    // --- resolveLink: dot notation for field ---

    fun testResolveLinkDotNotationField() {
        loadFile("linkres/DotFieldTarget.java", """
            package com.test.linkres;
            /**
             * A target for dot notation.
             */
            public class DotFieldTarget {
                /** The priority. */
                private int priority;
            }
        """.trimIndent())
        loadFile("linkres/DotFieldContext.java", """
            package com.test.linkres;
            public class DotFieldContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.DotFieldContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.DotFieldTarget.priority", contextElement)
        assertNotNull("Should resolve dot notation link to field", result)
        assertTrue("Result should be a PsiField", result is com.intellij.psi.PsiField)
        assertEquals("priority", (result as com.intellij.psi.PsiField).name)
    }

    // --- resolveLink: getter to property conversion ---

    fun testResolveLinkGetterToProperty() {
        loadFile("linkres/GetterTarget.java", """
            package com.test.linkres;
            /**
             * A target with getter.
             */
            public class GetterTarget {
                /** The active flag. */
                private boolean active;
                /**
                 * Is active.
                 * @return true if active
                 */
                public boolean isActive() { return active; }
            }
        """.trimIndent())
        loadFile("linkres/GetterContext.java", """
            package com.test.linkres;
            public class GetterContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.GetterContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.GetterTarget#isActive()", contextElement)
        assertNotNull("Should resolve getter method", result)
        assertTrue("Result should be a PsiMethod for isActive", result is com.intellij.psi.PsiMethod)
        assertEquals("isActive", (result as com.intellij.psi.PsiMethod).name)
    }

    fun testResolveLinkDotNotationGetterToProperty() {
        loadFile("linkres/GetterDotTarget.java", """
            package com.test.linkres;
            /**
             * A target for dot getter.
             */
            public class GetterDotTarget {
                /** The status. */
                private String status;
            }
        """.trimIndent())
        loadFile("linkres/GetterDotContext.java", """
            package com.test.linkres;
            public class GetterDotContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.GetterDotContext")!!
        val result = linkResolver.resolveLink("com.test.linkres.GetterDotTarget.getStatus", contextElement)
        assertNotNull("Should resolve dot notation getter to field", result)
        assertTrue("Result should be a PsiField", result is com.intellij.psi.PsiField)
        assertEquals("status", (result as com.intellij.psi.PsiField).name)
    }

    // --- resolveLink: invalid link ---

    fun testResolveLinkReturnsNullForInvalidLink() {
        loadFile("linkres/InvalidLinkContext.java", """
            package com.test.linkres;
            public class InvalidLinkContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.InvalidLinkContext")!!
        val result = linkResolver.resolveLink("", contextElement)
        assertNull("Should return null for empty link", result)
    }

    // --- extractLinks ---

    fun testExtractLinksFromJavaDocFormat() {
        val text = "See {@link UserDTO} and {@link AccountDTO#name} for details."
        val links = linkResolver.extractLinks(text)
        assertEquals("Should extract 2 JavaDoc links", 2, links.size)
        assertTrue("First link should be UserDTO", links.contains("UserDTO"))
        assertTrue("Second link should be AccountDTO#name", links.contains("AccountDTO#name"))
    }

    fun testExtractLinksFromKDocFormat() {
        val text = "See [UserDTO] and [AccountDTO.name] for details."
        val links = linkResolver.extractLinks(text)
        assertEquals("Should extract 1 KDoc link (only dot/hash links)", 1, links.size)
        assertTrue("Should extract AccountDTO.name", links.contains("AccountDTO.name"))
    }

    // --- resolveAllLinks ---

    fun testResolveAllLinks() {
        loadFile("linkres/AllLinksTarget.java", """
            package com.test.linkres;
            /**
             * A target for resolveAllLinks.
             */
            public class AllLinksTarget {}
        """.trimIndent())
        loadFile("linkres/AllLinksContext.java", """
            package com.test.linkres;
            public class AllLinksContext {}
        """.trimIndent())
        val contextElement = findClass("com.test.linkres.AllLinksContext")!!
        val results = linkResolver.resolveAllLinks(
            "See {@link com.test.linkres.AllLinksTarget}",
            contextElement
        )
        assertEquals("Should resolve 1 link", 1, results.size)
        assertTrue("Resolved element should be a PsiClass", results[0] is com.intellij.psi.PsiClass)
    }

    // --- getInstance ---

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = LinkResolver.getInstance(project)
        val instance2 = LinkResolver.getInstance(project)
        assertSame("getInstance should return the same service instance", instance1, instance2)
    }
}
