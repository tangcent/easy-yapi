package com.itangcent.easyapi.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.DefaultConfigReader
import com.itangcent.easyapi.core.event.ActionCompletedTopic
import com.itangcent.easyapi.core.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.settings.SettingBinder
import kotlinx.coroutines.runBlocking
import java.io.InputStreamReader

/**
 * Base test case class for EasyAPI plugin tests using LightJavaCodeInsightFixtureTestCase.
 *
 * ## Customizing Configuration
 *
 * For test cases that need customized config, implement [createConfigReader] with [TestConfigReader]:
 * ```kotlin
 * override fun createConfigReader(): ConfigReader? {
 *     return TestConfigReader().apply {
 *         load("custom_rule.easyapi")
 *     }
 * }
 * ```
 *
 * ## Testing with Different Configurations
 *
 * If a test case needs to test with different configs, add multiple inner test classes
 * to load different configs via [createConfigReader]:
 * ```kotlin
 * class MyFeatureTest {
 *     class WithDefaultConfig : EasyApiLightCodeInsightFixtureTestCase() {
 *         // tests with default config
 *     }
 *
 *     class WithCustomConfig : EasyApiLightCodeInsightFixtureTestCase() {
 *         override fun createConfigReader(): ConfigReader? {
 *             return TestConfigReader().apply { load("custom.easyapi") }
 *         }
 *         // tests with custom config
 *     }
 * }
 * ```
 *
 * ## Customizing Settings
 *
 * For test cases that need customized settings, call [settingBinder.update].
 * There are two common scenarios:
 *
 * **1. Update settings in `setUp()` (applies to all tests in the class):**
 * ```kotlin
 * override fun setUp() {
 *     super.setUp()
 *     settingBinder.update {
 *         enableUrlTemplating = false
 *     }
 * }
 * ```
 *
 * **2. Update settings in test method (applies only to that specific test):**
 * ```kotlin
 * fun testSomething() {
 *     settingBinder.update {
 *         enableUrlTemplating = false
 *     }
 *     // test code
 * }
 * ```
 *
 * ## Replacing Project Services
 *
 * If you need to replace other project services, override [setUp] and call `registerServiceInstance`:
 * ```kotlin
 * override fun setUp() {
 *     super.setUp()
 *     project.registerServiceInstance(
 *         serviceInterface = MyService::class.java,
 *         instance = MockMyService()
 *     )
 * }
 * ```
 *
 * ## Same-Package Import Requirement for Resource Files
 *
 * **Important:** In the IntelliJ PSI test fixture environment, all classes referenced
 * by a Java file must be explicitly imported, even if they are in the same package.
 * In normal Java compilation, same-package classes don't require imports, but the
 * lightweight test fixture's PSI resolver cannot resolve unimported same-package types
 * unless they are explicitly imported. Without proper imports, the PSI will treat
 * unresolved type references as `UnresolvedType`, which causes `buildObjectModelFromType`
 * to return `ObjectModel.Single` instead of `ObjectModel.Object`.
 *
 * For example, if `UserController.java` uses `OrderedDTO` from the same package,
 * it must include `import com.itangcent.jackson.OrderedDTO;` even though they share
 * the same package declaration.
 */
abstract class EasyApiLightCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {

    /**
     * Creates a custom ConfigReader for the test.
     * Override this method to provide custom configuration via [TestConfigReader].
     *
     * @return a ConfigReader instance, or null to use the default configuration
     * @see TestConfigReader
     */
    protected open fun createConfigReader(): ConfigReader? = null

    protected val settingBinder
        get() = SettingBinder.getInstance(project)

    override fun setUp() {
        super.setUp()
        val configReader = createConfigReader() ?: TestConfigReader.empty(project)
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = configReader
        )
        // Trigger reload to notify listeners (e.g. RuleProvider) to clear stale caches.
        // This is necessary because RuleProvider is a project-level singleton that survives
        // across light fixture test classes.
        runBlocking { configReader.reload() }
        // Publish branch change to clear caches that depend on project state.
        // This simulates a branch switch to ensure services like ProjectClassAvailabilityService
        // clear their caches between test runs.
        project.messageBus.syncPublisher(BranchChangeListener.VCS_BRANCH_CHANGED)
            .branchHasChanged("test-setup")
    }

    override fun tearDown() {
        project.syncPublish(ActionCompletedTopic.TOPIC)
        super.tearDown()
    }

    protected fun runTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    protected fun findClass(qualifiedName: String): PsiClass? {
        return readSync {
            JavaPsiFacade.getInstance(project)
                .findClass(qualifiedName, GlobalSearchScope.allScope(project))
        }
    }

    protected fun findMethod(psiClass: PsiClass, name: String): PsiMethod? {
        return psiClass.findMethodsByName(name, false).firstOrNull() as? PsiMethod
    }

    /**
     * Loads a resource file into the test fixture's virtual file system.
     *
     * **Important:** In the IntelliJ PSI test fixture environment, all classes referenced
     * by a Java file must be explicitly imported, even if they are in the same package.
     * In normal Java compilation, same-package classes don't require imports, but the
     * lightweight test fixture's PSI resolver cannot resolve unimported same-package types
     * unless they are explicitly imported. Without proper imports, the PSI will treat
     * unresolved type references as `UnresolvedType`, which causes `buildObjectModelFromType`
     * to return `ObjectModel.Single` instead of `ObjectModel.Object`.
     *
     * For example, if `UserController.java` uses `OrderedDTO` from the same package,
     * it must include `import com.itangcent.jackson.OrderedDTO;` even though they share
     * the same package declaration.
     *
     * @param path the resource path relative to the test resources directory
     * @return the loaded PsiFile
     */
    protected fun loadFile(path: String): PsiFile {
        myFixture.findFileInTempDir(path)?.let { existing ->
            return myFixture.psiManager.findFile(existing)!!
        }
        val resourceStream = javaClass.getResourceAsStream("/$path")
            ?: throw AssertionError("Resource not found: $path")
        val reader = InputStreamReader(resourceStream, Charsets.UTF_8)
        val content = reader.readText()
        reader.close()
        return ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
    }

    protected fun loadFile(path: String, content: String): PsiFile {
        myFixture.findFileInTempDir(path)?.let { existing ->
            return myFixture.psiManager.findFile(existing)!!
        }
        return ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
    }

    protected fun loadSource(clazz: Class<*>): PsiClass {
        val className = clazz.simpleName
        val packageName = clazz.`package`.name
        val path = packageName.replace('.', '/') + "/" + className + ".java"
        val resourceStream = javaClass.getResourceAsStream("/jdk/$className.java")
        val content = if (resourceStream != null) {
            val reader = InputStreamReader(resourceStream, Charsets.UTF_8)
            val text = reader.readText()
            reader.close()
            text
        } else {
            generateStubClass(clazz)
        }
        ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
        return findClass(clazz.name)!!
    }

    private fun generateStubClass(clazz: Class<*>): String {
        val packageName = clazz.`package`.name
        val className = clazz.simpleName
        return when {
            clazz.isEnum -> "package $packageName; public enum $className {}"
            clazz.isInterface -> "package $packageName; public interface $className {}"
            clazz.isArray -> "package $packageName; public class $className {}"
            else -> "package $packageName; public class $className {}"
        }
    }
}
