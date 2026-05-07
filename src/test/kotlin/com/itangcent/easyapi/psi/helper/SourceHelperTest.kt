package com.itangcent.easyapi.psi.helper

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class SourceHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var sourceHelper: SourceHelper

    override fun setUp() {
        super.setUp()
        sourceHelper = SourceHelper.getInstance(project)
    }

    // --- getInstance ---

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = SourceHelper.getInstance(project)
        val instance2 = SourceHelper.getInstance(project)
        assertSame("getInstance should return the same service instance", instance1, instance2)
    }

    // --- getSourceClassSync: local source classes ---

    fun testGetSourceClassSyncReturnsSameForSourceClass() {
        loadFile("source/LocalSourceClass.java", """
            package com.test.source;
            /**
             * A local source class.
             */
            public class LocalSourceClass {
                /** The name field. */
                private String name;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.source.LocalSourceClass")!!
        val result = sourceHelper.getSourceClassSync(psiClass)
        assertSame("Should return same class for local source class", psiClass, result)
    }

    // --- getSourceClass: suspend version ---

    fun testGetSourceClassReturnsSameForSourceClass() = runBlocking {
        loadFile("source/LocalSourceClassSuspend.java", """
            package com.test.source;
            /**
             * A local source class for suspend test.
             */
            public class LocalSourceClassSuspend {}
        """.trimIndent())
        val psiClass = findClass("com.test.source.LocalSourceClassSuspend")!!
        val result = sourceHelper.getSourceClass(psiClass)
        assertSame("Should return same class for local source class", psiClass, result)
    }

    // --- getSourceElementSync: PsiClass ---

    fun testGetSourceElementSyncReturnsSameForSourceClass() {
        loadFile("source/ElementSourceClass.java", """
            package com.test.source;
            /**
             * A class for element resolution test.
             */
            public class ElementSourceClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.source.ElementSourceClass")!!
        val result = sourceHelper.getSourceElementSync(psiClass)
        assertSame("Should return same element for source PsiClass", psiClass, result)
    }

    // --- getSourceElementSync: PsiField ---

    fun testGetSourceElementSyncResolvesFieldFromSourceClass() {
        loadFile("source/FieldSourceClass.java", """
            package com.test.source;
            /**
             * A class with documented fields.
             */
            public class FieldSourceClass {
                /** The user name. */
                private String userName;
                /** The user age. */
                private int userAge;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.source.FieldSourceClass")!!
        val field = psiClass.findFieldByName("userName", false)!!
        val result = sourceHelper.getSourceElementSync(field)
        assertNotNull("Should resolve source element for field", result)
        assertTrue("Result should be a PsiField", result is com.intellij.psi.PsiField)
        assertEquals("Field name should match", "userName", (result as com.intellij.psi.PsiField).name)
    }

    // --- getSourceElementSync: PsiMethod ---

    fun testGetSourceElementSyncResolvesMethodFromSourceClass() {
        loadFile("source/MethodSourceClass.java", """
            package com.test.source;
            /**
             * A class with documented methods.
             */
            public class MethodSourceClass {
                /**
                 * Gets the user name.
                 * @return the user name
                 */
                public String getUserName() { return null; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.source.MethodSourceClass")!!
        val method = findMethod(psiClass, "getUserName")!!
        val result = sourceHelper.getSourceElementSync(method)
        assertNotNull("Should resolve source element for method", result)
        assertTrue("Result should be a PsiMethod", result is com.intellij.psi.PsiMethod)
        assertEquals("Method name should match", "getUserName", (result as com.intellij.psi.PsiMethod).name)
    }

    // --- getSourceElement: suspend version ---

    fun testGetSourceElementReturnsSameForSourceElement() = runBlocking {
        loadFile("source/ElementSuspendClass.java", """
            package com.test.source;
            /**
             * A class for suspend element test.
             */
            public class ElementSuspendClass {
                /** The value. */
                private int value;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.source.ElementSuspendClass")!!
        val result = sourceHelper.getSourceElement(psiClass)
        assertSame("Should return same element for source PsiClass", psiClass, result)
    }

    // --- inner class handling ---

    fun testGetSourceClassSyncHandlesInnerClass() {
        loadFile("source/OuterSourceClass.java", """
            package com.test.source;
            /**
             * An outer class.
             */
            public class OuterSourceClass {
                /**
                 * An inner class.
                 */
                public static class InnerClass {
                    /** Inner field. */
                    private String innerField;
                }
            }
        """.trimIndent())
        val innerClass = findClass("com.test.source.OuterSourceClass.InnerClass")!!
        val result = sourceHelper.getSourceClassSync(innerClass)
        assertSame("Should return same inner class for source inner class", innerClass, result)
    }

    // --- null qualified name handling ---

    fun testGetSourceClassSyncHandlesAnonymousClass() {
        loadFile("source/AnonymousClassHolder.java", """
            package com.test.source;
            /**
             * A class that creates an anonymous inner class.
             */
            public class AnonymousClassHolder {
                public Runnable getRunnable() {
                    return new Runnable() {
                        @Override
                        public void run() {}
                    };
                }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.source.AnonymousClassHolder")!!
        val result = sourceHelper.getSourceClassSync(psiClass)
        assertNotNull("Should handle class without errors", result)
    }

    // --- caching behavior ---

    fun testGetSourceClassSyncCachesResult() {
        loadFile("source/CachedSourceClass.java", """
            package com.test.source;
            /**
             * A class for caching test.
             */
            public class CachedSourceClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.source.CachedSourceClass")!!
        val result1 = sourceHelper.getSourceClassSync(psiClass)
        val result2 = sourceHelper.getSourceClassSync(psiClass)
        assertSame("Should return cached result on second call", result1, result2)
    }

    // --- field resolution from inner class ---

    fun testGetSourceElementSyncResolvesFieldFromInnerClass() {
        loadFile("source/InnerFieldClass.java", """
            package com.test.source;
            /**
             * An outer class with inner fields.
             */
            public class InnerFieldClass {
                /**
                 * An inner class.
                 */
                public static class Inner {
                    /** Inner name. */
                    private String innerName;
                }
            }
        """.trimIndent())
        val innerClass = findClass("com.test.source.InnerFieldClass.Inner")!!
        val field = innerClass.findFieldByName("innerName", false)!!
        val result = sourceHelper.getSourceElementSync(field)
        assertNotNull("Should resolve source element for inner class field", result)
        assertEquals("Field name should match", "innerName", (result as com.intellij.psi.PsiField).name)
    }

    // --- method resolution from inner class ---

    fun testGetSourceElementSyncResolvesMethodFromInnerClass() {
        loadFile("source/InnerMethodClass.java", """
            package com.test.source;
            /**
             * An outer class with inner methods.
             */
            public class InnerMethodClass {
                /**
                 * An inner class.
                 */
                public static class Inner {
                    /**
                     * Gets the inner value.
                     * @return the inner value
                     */
                    public String getInnerValue() { return null; }
                }
            }
        """.trimIndent())
        val innerClass = findClass("com.test.source.InnerMethodClass.Inner")!!
        val method = findMethod(innerClass, "getInnerValue")!!
        val result = sourceHelper.getSourceElementSync(method)
        assertNotNull("Should resolve source element for inner class method", result)
        assertEquals("Method name should match", "getInnerValue", (result as com.intellij.psi.PsiMethod).name)
    }

    // --- element without containing class ---

    fun testGetSourceElementSyncHandlesElementWithoutContainingClass() {
        loadFile("source/StandaloneClass.java", """
            package com.test.source;
            /**
             * A standalone class.
             */
            public class StandaloneClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.source.StandaloneClass")!!
        val result = sourceHelper.getSourceElementSync(psiClass)
        assertSame("Should handle PsiClass directly", psiClass, result)
    }
}
