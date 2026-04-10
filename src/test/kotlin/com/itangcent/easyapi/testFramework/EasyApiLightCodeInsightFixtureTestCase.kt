package com.itangcent.easyapi.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.context.ActionContextBuilder
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import java.io.InputStreamReader

abstract class EasyApiLightCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {

    protected lateinit var actionContext: ActionContext
    protected lateinit var testSettingBinder: ConstantSettingBinder

    protected open fun createConfigReader(): ConfigReader = TestConfigReader()

    protected open fun createSettings(): Settings = Settings()

    protected open fun customizeContext(builder: ActionContextBuilder) {
    }

    @Before
    override fun setUp() {
        super.setUp()
        testSettingBinder = ConstantSettingBinder(createSettings())
        // Register testSettingBinder as a project service so SettingBinder.getInstance(project) returns it
        project.registerServiceInstance(SettingBinder::class.java, testSettingBinder)
        val builder = ActionContext.builder()
            .bind(Project::class, project)
            .bind(ConfigReader::class, createConfigReader())
            .bind(SettingBinder::class, testSettingBinder)
            .withSpiBindings()

        customizeContext(builder)
        actionContext = builder.build()
    }

    @After
    override fun tearDown() {
        actionContext.stop()
        super.tearDown()
    }

    protected fun runTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    protected fun setSettings(settings: Settings) {
        testSettingBinder.save(settings)
    }

    protected fun updateSettings(updater: Settings.() -> Unit) {
        testSettingBinder.updateSettings(updater)
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

    protected fun loadFile(path: String): PsiFile {
        // Skip silently if already loaded (tests share the same project across methods)
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
        // Skip silently if already loaded (tests share the same project across methods)
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

    protected inline fun <reified T : Any> instance(): T = actionContext.instance()

    protected fun <T : Any> instance(kClass: kotlin.reflect.KClass<T>): T = actionContext.instance(kClass)
}
