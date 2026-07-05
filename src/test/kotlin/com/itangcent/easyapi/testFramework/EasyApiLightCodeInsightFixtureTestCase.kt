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
import com.itangcent.easyapi.settings.DefaultSettingBinder
import kotlinx.coroutines.delay
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
 * For test cases that need customized settings, call
 * `SettingBinder.getInstance(project).update(ModuleType::class)`.
 * There are two common scenarios:
 *
 * **1. Update settings in `setUp()` (applies to all tests in the class):**
 * ```kotlin
 * override fun setUp() {
 *     super.setUp()
 *     SettingBinder.getInstance(project).update(ParsingOutputSettings::class) {
 *         enableUrlTemplating = false
 *     }
 * }
 * ```
 *
 * **2. Update settings in test method (applies only to that specific test):**
 * ```kotlin
 * fun testSomething() {
 *     SettingBinder.getInstance(project).update(ParsingOutputSettings::class) {
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

    /**
     * The [LoggedErrorProcessor] captured in [setUp] so it can be restored in
     * [tearDown]. See [KotlinGistErrorSuppressor] for the rationale.
     */
    private var previousErrorProcessor: com.intellij.testFramework.LoggedErrorProcessor? = null

    override fun setUp() {
        super.setUp()
        // Suppress the Kotlin plugin's `kotlin-library-kind` gist diagnostic,
        // which otherwise escalates to a TestLoggerAssertionError and aborts
        // every test that traverses a class's methods in the light fixture.
        previousErrorProcessor = KotlinGistErrorSuppressor.install()
        loadCommonJDKClasses()
        // Register DefaultSettingBinder as the SettingBinder project service.
        project.registerServiceInstance(
            serviceInterface = SettingBinder::class.java,
            instance = DefaultSettingBinder(project)
        )
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
        try {
            project.syncPublish(ActionCompletedTopic.TOPIC)
        } finally {
            previousErrorProcessor?.let { KotlinGistErrorSuppressor.uninstall(it) }
            super.tearDown()
        }
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
     * Waits until a class with the given qualified name is findable via [JavaPsiFacade].
     *
     * In the light test fixture, [addFileToProject] creates the VirtualFile and PsiFile
     * immediately, but [JavaPsiFacade.findClass] depends on the PSI index which updates
     * asynchronously. A fixed [delay] is fragile because the indexing latency is
     * non-deterministic. This method polls with short intervals until the class is
     * resolvable, or throws after [timeoutMs].
     *
     * @param qualifiedName the fully qualified class name to wait for
     * @param timeoutMs maximum time to wait in milliseconds (default 5000)
     * @param pollIntervalMs interval between polls in milliseconds (default 50)
     * @return the found PsiClass
     * @throws AssertionError if the class is not found within the timeout
     */
    protected suspend fun waitForClass(
        qualifiedName: String,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 50
    ): PsiClass {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val psiClass = readSync {
                JavaPsiFacade.getInstance(project)
                    .findClass(qualifiedName, GlobalSearchScope.allScope(project))
            }
            if (psiClass != null) return psiClass
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Class '$qualifiedName' not found within ${timeoutMs}ms")
            }
            delay(pollIntervalMs)
        }
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

    /**
     * Loads commonly needed JDK classes into the test fixture.
     *
     * Called automatically by [setUp] so all test classes have access to
     * basic JDK types. This is necessary because the light test fixture
     * environment does not include a real JDK classpath.
     *
     * **Important**: Only classes that are known to be safe to load as source
     * stubs are included here. The mock JDK already provides some core types
     * (e.g., `java.lang.Object`, `java.lang.String`, `java.util.List`,
     * `java.util.Map`), and loading them as source stubs would shadow the
     * compiled versions, breaking generic type resolution and collection
     * detection. Similarly, wrapper types like `java.lang.Integer` and
     * `java.lang.Long` should not be loaded as source stubs because their
     * minimal stubs interfere with generic type binding.
     *
     * Tests that need specific JDK classes should call [loadJDKClass]
     * explicitly in their test methods.
     */
    protected fun loadCommonJDKClasses() {
        // Annotations commonly used in test source code
        loadJDKClass("java.lang.Override")
        loadJDKClass("java.lang.Deprecated")
        loadJDKClass("java.lang.SuppressWarnings")
        // Common interface
        loadJDKClass("java.io.Serializable")
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

    /**
     * Loads a JDK class into the test fixture's virtual file system with proper inheritance.
     *
     * In the light test fixture environment, JDK classes are not available by default.
     * This method loads a JDK class by its fully qualified name, first checking for a
     * pre-written stub in `/jdk/<simpleName>.java` resources, then falling back to a
     * built-in stub from [JDK_STUBS], and finally generating a minimal empty stub.
     *
     * Dependencies (superclasses/interfaces) are automatically loaded first to ensure
     * the PSI resolver can traverse the full inheritance chain.
     *
     * Example usage:
     * ```kotlin
     * loadJDKClass("java.util.ArrayList")  // loads ArrayList + all dependencies
     * loadJDKClass("java.util.List")       // loads List + Collection + Iterable
     * ```
     *
     * @param fqn the fully qualified name of the JDK class (e.g., "java.util.List")
     * @return the loaded PsiClass
     */
    protected fun loadJDKClass(fqn: String): PsiClass {
        val existing = findClass(fqn)
        if (existing != null) return existing

        val stub = JDK_STUBS[fqn]
        val content = if (stub != null) {
            // Load dependencies first so the PSI resolver can resolve supertypes
            for (dep in stub.dependencies) {
                loadJDKClass(dep)
            }
            stub.source
        } else {
            val lastDot = fqn.lastIndexOf('.')
            val packageName = fqn.substring(0, lastDot)
            val className = fqn.substring(lastDot + 1)
            // Try resource file before generating empty stub
            val resourceStream = javaClass.getResourceAsStream("/jdk/$className.java")
            if (resourceStream != null) {
                val reader = InputStreamReader(resourceStream, Charsets.UTF_8)
                val text = reader.readText()
                reader.close()
                text
            } else {
                "package $packageName; public class $className {}"
            }
        }

        val path = fqn.replace('.', '/') + ".java"
        ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
        return findClass(fqn) ?: throw AssertionError("Failed to load JDK class: $fqn")
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

    /**
     * Pre-built JDK class stubs with correct inheritance hierarchies.
     *
     * Each entry maps a fully qualified name to a [JdkStub] containing the source
     * and a list of dependency FQNs that must be loaded first. This ensures the
     * PSI resolver can traverse the complete type hierarchy (e.g., ArrayList →
     * AbstractList → AbstractCollection → Collection → Iterable).
     */
    private data class JdkStub(val source: String, val dependencies: List<String> = emptyList())

    private val JDK_STUBS: Map<String, JdkStub> by lazy {
        mapOf(
            // java.lang
            "java.lang.Iterable" to JdkStub(
                "package java.lang; public interface Iterable<T> {}"
            ),
            "java.lang.Comparable" to JdkStub(
                "package java.lang; public interface Comparable<T> {}"
            ),
            // java.util - Collection hierarchy
            "java.util.Collection" to JdkStub(
                "package java.util; public interface Collection<E> extends Iterable<E> {}",
                listOf("java.lang.Iterable")
            ),
            "java.util.List" to JdkStub(
                "package java.util; public interface List<E> extends Collection<E> {}",
                listOf("java.util.Collection")
            ),
            "java.util.Set" to JdkStub(
                "package java.util; public interface Set<E> extends Collection<E> {}",
                listOf("java.util.Collection")
            ),
            "java.util.SortedSet" to JdkStub(
                "package java.util; public interface SortedSet<E> extends Set<E> {}",
                listOf("java.util.Set")
            ),
            "java.util.NavigableSet" to JdkStub(
                "package java.util; public interface NavigableSet<E> extends SortedSet<E> {}",
                listOf("java.util.SortedSet")
            ),
            "java.util.Queue" to JdkStub(
                "package java.util; public interface Queue<E> extends Collection<E> {}",
                listOf("java.util.Collection")
            ),
            "java.util.Deque" to JdkStub(
                "package java.util; public interface Deque<E> extends Queue<E> {}",
                listOf("java.util.Queue")
            ),
            "java.util.AbstractCollection" to JdkStub(
                "package java.util; public abstract class AbstractCollection<E> implements Collection<E> {}",
                listOf("java.util.Collection")
            ),
            "java.util.AbstractList" to JdkStub(
                "package java.util; public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {}",
                listOf("java.util.AbstractCollection", "java.util.List")
            ),
            "java.util.AbstractSet" to JdkStub(
                "package java.util; public abstract class AbstractSet<E> extends AbstractCollection<E> implements Set<E> {}",
                listOf("java.util.AbstractCollection", "java.util.Set")
            ),
            "java.util.AbstractSequentialList" to JdkStub(
                "package java.util; public abstract class AbstractSequentialList<E> extends AbstractList<E> {}",
                listOf("java.util.AbstractList")
            ),
            "java.util.ArrayList" to JdkStub(
                "package java.util; public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess {}",
                listOf("java.util.AbstractList", "java.util.List")
            ),
            "java.util.LinkedList" to JdkStub(
                "package java.util; public class LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque {}",
                listOf("java.util.AbstractSequentialList", "java.util.List", "java.util.Deque")
            ),
            "java.util.HashSet" to JdkStub(
                "package java.util; public class HashSet<E> extends AbstractSet<E> implements Set<E> {}",
                listOf("java.util.AbstractSet", "java.util.Set")
            ),
            "java.util.LinkedHashSet" to JdkStub(
                "package java.util; public class LinkedHashSet<E> extends HashSet<E> implements Set<E> {}",
                listOf("java.util.HashSet", "java.util.Set")
            ),
            "java.util.TreeSet" to JdkStub(
                "package java.util; public class TreeSet<E> extends AbstractSet<E> implements NavigableSet<E> {}",
                listOf("java.util.AbstractSet", "java.util.NavigableSet")
            ),
            "java.util.Vector" to JdkStub(
                "package java.util; public class Vector<E> extends AbstractList<E> implements List<E> {}",
                listOf("java.util.AbstractList", "java.util.List")
            ),
            "java.util.Stack" to JdkStub(
                "package java.util; public class Stack<E> extends Vector<E> {}",
                listOf("java.util.Vector")
            ),
            "java.util.ArrayDeque" to JdkStub(
                "package java.util; public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E> {}",
                listOf("java.util.AbstractCollection", "java.util.Deque")
            ),
            "java.util.Collections" to JdkStub(
                "package java.util; public class Collections {}"
            ),
            "java.util.RandomAccess" to JdkStub(
                "package java.util; public interface RandomAccess {}"
            ),
            // java.util - Map hierarchy
            "java.util.Map" to JdkStub(
                "package java.util; public interface Map<K, V> {}"
            ),
            "java.util.SortedMap" to JdkStub(
                "package java.util; public interface SortedMap<K, V> extends Map<K, V> {}",
                listOf("java.util.Map")
            ),
            "java.util.NavigableMap" to JdkStub(
                "package java.util; public interface NavigableMap<K, V> extends SortedMap<K, V> {}",
                listOf("java.util.SortedMap")
            ),
            "java.util.AbstractMap" to JdkStub(
                "package java.util; public abstract class AbstractMap<K, V> implements Map<K, V> {}",
                listOf("java.util.Map")
            ),
            "java.util.HashMap" to JdkStub(
                "package java.util; public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {}",
                listOf("java.util.AbstractMap", "java.util.Map")
            ),
            "java.util.LinkedHashMap" to JdkStub(
                "package java.util; public class LinkedHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {}",
                listOf("java.util.HashMap", "java.util.Map")
            ),
            "java.util.TreeMap" to JdkStub(
                "package java.util; public class TreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V> {}",
                listOf("java.util.AbstractMap", "java.util.NavigableMap")
            ),
            // java.util.concurrent - Map hierarchy
            "java.util.concurrent.ConcurrentMap" to JdkStub(
                "package java.util.concurrent; public interface ConcurrentMap<K, V> extends Map<K, V> {}",
                listOf("java.util.Map")
            ),
            "java.util.concurrent.ConcurrentHashMap" to JdkStub(
                "package java.util.concurrent; public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {}",
                listOf("java.util.AbstractMap", "java.util.concurrent.ConcurrentMap")
            ),
            // java.lang - core types
            "java.lang.Object" to JdkStub(
                "package java.lang; public class Object {}"
            ),
            "java.lang.Enum" to JdkStub(
                "package java.lang; public abstract class Enum<E extends Enum<E>> implements Comparable<E> {}",
                listOf("java.lang.Comparable")
            ),
            "java.lang.String" to JdkStub(
                "package java.lang; public final class String implements Comparable<String>, CharSequence {}",
                listOf("java.lang.Comparable")
            ),
            "java.lang.CharSequence" to JdkStub(
                "package java.lang; public interface CharSequence {}"
            ),
            "java.lang.Number" to JdkStub(
                "package java.lang; public abstract class Number implements java.io.Serializable {}"
            ),
            "java.lang.Integer" to JdkStub(
                "package java.lang; public final class Integer extends Number implements Comparable<Integer> {}",
                listOf("java.lang.Number", "java.lang.Comparable")
            ),
            "java.lang.Long" to JdkStub(
                "package java.lang; public final class Long extends Number implements Comparable<Long> {}",
                listOf("java.lang.Number", "java.lang.Comparable")
            ),
            "java.lang.Double" to JdkStub(
                "package java.lang; public final class Double extends Number implements Comparable<Double> {}",
                listOf("java.lang.Number", "java.lang.Comparable")
            ),
            "java.lang.Float" to JdkStub(
                "package java.lang; public final class Float extends Number implements Comparable<Float> {}",
                listOf("java.lang.Number", "java.lang.Comparable")
            ),
            "java.lang.Boolean" to JdkStub(
                "package java.lang; public final class Boolean implements Comparable<Boolean> {}",
                listOf("java.lang.Comparable")
            ),
            "java.lang.Throwable" to JdkStub(
                "package java.lang; public class Throwable {}"
            ),
            "java.lang.Exception" to JdkStub(
                "package java.lang; public class Exception extends Throwable {}",
                listOf("java.lang.Throwable")
            ),
            "java.lang.RuntimeException" to JdkStub(
                "package java.lang; public class RuntimeException extends Exception {}",
                listOf("java.lang.Exception")
            ),
            "java.lang.Deprecated" to JdkStub(
                "package java.lang; public @interface Deprecated {}"
            ),
            "java.lang.Override" to JdkStub(
                "package java.lang; public @interface Override {}"
            ),
            "java.lang.SuppressWarnings" to JdkStub(
                "package java.lang; public @interface SuppressWarnings {}"
            ),
            // java.io
            "java.io.Serializable" to JdkStub(
                "package java.io; public interface Serializable {}"
            ),
            "java.io.IOException" to JdkStub(
                "package java.io; public class IOException extends Exception {}",
                listOf("java.lang.Exception")
            ),
        )
    }
}
