package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import java.io.File

/**
 * Tests for [PsiNameResolver].
 *
 * Covers the three resolution paths (FQN, simple-name single-match,
 * simple-name ambiguous) and the `resolveContextElement` normalization
 * (class FQN, absolute path, project-relative path, unresolvable input).
 *
 * Uses the light fixture's VFS to provide source classes — the resolver
 * uses [com.intellij.psi.JavaPsiFacade.findClass] and
 * [com.intellij.psi.search.PsiShortNamesCache] which rely on the PSI index.
 */
class PsiNameResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/AuthResponse.java",
                """
                package com.example;
                public class AuthResponse {
                    public String token;
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Order.java",
                """
                package com.example;
                public class Order {
                    public AuthResponse auth;
                }
                """.trimIndent()
            )
            // Two classes with the same simple name "Duplicate" — ambiguous
            // when resolved without context.
            myFixture.addFileToProject(
                "com/example/dup/Duplicate.java",
                "package com.example.dup; public class Duplicate {}"
            )
            myFixture.addFileToProject(
                "com/example/other/Duplicate.java",
                "package com.example.other; public class Duplicate {}"
            )
        }
    }

    // --- resolveClass ---

    fun testFqnResolves() {
        addClasses()
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("com.example.AuthResponse", project)
        }
        Assert.assertNotNull("FQN should resolve", psiClass)
        Assert.assertEquals("com.example.AuthResponse", psiClass!!.qualifiedName)
    }

    fun testFqnReturnsNullForUnknown() {
        addClasses()
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("com.example.DoesNotExist", project)
        }
        Assert.assertNull("unknown FQN should return null", psiClass)
    }

    fun testSimpleNameSingleMatchResolves() {
        addClasses()
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("AuthResponse", project)
        }
        Assert.assertNotNull("single-match simple name should resolve", psiClass)
        Assert.assertEquals("com.example.AuthResponse", psiClass!!.qualifiedName)
    }

    fun testSimpleNameZeroMatchReturnsNull() {
        addClasses()
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("DoesNotExist", project)
        }
        Assert.assertNull("zero-match simple name should return null", psiClass)
    }

    fun testSimpleNameMultiMatchReturnsNull() {
        addClasses()
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("Duplicate", project)
        }
        Assert.assertNull("ambiguous simple name should return null", psiClass)
    }

    fun testBlankNameReturnsNull() {
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("   ", project)
        }
        Assert.assertNull("blank name should return null", psiClass)
    }

    fun testResolveClassWithContextResolvesSimpleName() {
        addClasses()
        // Use Order.java's containing file as context — AuthResponse is
        // in the same package, so TypeResolver should resolve it.
        val orderFile = runBlocking {
            PsiNameResolver.resolveContextElement("com.example.Order", project)
        }
        Assert.assertNotNull(orderFile)
        val psiClass = runBlocking {
            PsiNameResolver.resolveClass("AuthResponse", project, orderFile)
        }
        Assert.assertNotNull("context should help resolve simple name", psiClass)
        Assert.assertEquals("com.example.AuthResponse", psiClass!!.qualifiedName)
    }

    // --- resolveAllClasses ---

    fun testResolveAllClassesFqnReturnsSingleOrEmpty() {
        addClasses()
        val present = runBlocking {
            PsiNameResolver.resolveAllClasses("com.example.AuthResponse", project)
        }
        Assert.assertEquals(1, present.size)
        Assert.assertEquals("com.example.AuthResponse", present[0].qualifiedName)

        val absent = runBlocking {
            PsiNameResolver.resolveAllClasses("com.example.Missing", project)
        }
        Assert.assertTrue("missing FQN should return empty list", absent.isEmpty())
    }

    fun testResolveAllClassesSimpleNameReturnsAllMatches() {
        addClasses()
        val all = runBlocking {
            PsiNameResolver.resolveAllClasses("Duplicate", project)
        }
        Assert.assertEquals("ambiguous simple name should return all matches", 2, all.size)
    }

    fun testResolveAllClassesBlankReturnsEmpty() {
        val all = runBlocking {
            PsiNameResolver.resolveAllClasses("  ", project)
        }
        Assert.assertTrue("blank name should return empty list", all.isEmpty())
    }

    // --- resolveContextElement ---

    fun testResolveContextElementAcceptsClassFqn() {
        addClasses()
        val element = runBlocking {
            PsiNameResolver.resolveContextElement("com.example.Order", project)
        }
        Assert.assertNotNull("class FQN context should resolve to containing file", element)
        Assert.assertTrue("expected PsiFile, got $element", element is PsiFile)
    }

    fun testResolveContextElementAcceptsProjectRelativePath() {
        addClasses()
        // The light fixture uses an in-memory VFS, so files added via
        // myFixture.addFileToProject are not on the local file system.
        // Create a real file within the project's base path so the
        // project-relative path resolution (LocalFileSystem + basePath)
        // can locate it.
        val basePath = project.basePath
        Assert.assertNotNull("project base path should be set", basePath)
        val ioFile = File(basePath!!, "TestProjectRelative.java")
        ioFile.parentFile?.mkdirs()
        ioFile.writeText("package com.example; public class TestProjectRelative {}")
        try {
            // Register the file in the VFS.
            VfsUtil.findFileByIoFile(ioFile, true)
            val element = runBlocking {
                PsiNameResolver.resolveContextElement("TestProjectRelative.java", project)
            }
            Assert.assertNotNull("project-relative path should resolve", element)
            Assert.assertTrue("expected PsiFile, got $element", element is PsiFile)
        } finally {
            ioFile.delete()
        }
    }

    fun testResolveContextElementAcceptsAbsolutePath() {
        addClasses()
        // The light fixture uses an in-memory VFS, so LocalFileSystem won't
        // find fixture-added files by path. Create a real temp file on disk
        // so LocalFileSystem.findFileByPath can locate it.
        val ioFile = File.createTempFile("TestContext", ".java")
        ioFile.writeText("package com.example; public class TestContext {}")
        ioFile.deleteOnExit()
        // Ensure the file is registered in the VFS before the resolver looks
        // it up.
        VfsUtil.findFileByIoFile(ioFile, true)
        val element = runBlocking {
            PsiNameResolver.resolveContextElement(ioFile.absolutePath, project)
        }
        Assert.assertNotNull("absolute path should resolve", element)
        Assert.assertTrue("expected PsiFile, got $element", element is PsiFile)
    }

    fun testResolveContextElementReturnsNullForUnresolvable() {
        addClasses()
        val element = runBlocking {
            PsiNameResolver.resolveContextElement("does/not/exist/Anywhere.java", project)
        }
        Assert.assertNull("unresolvable context should return null", element)
    }
}
