package com.itangcent.idea.plugin.api

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.*
import com.itangcent.common.model.Doc
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.common.utils.stream
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.LinkResolver
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.FileType
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.streams.toList

@Singleton
open class ClassApiExporterHelper {

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var logger: Logger

    @Inject
    protected val classExporter: ClassExporter? = null

    @Inject
    protected lateinit var messagesHelper: MessagesHelper
    fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
        val subTagMap = docHelper!!.getSubTagMapOfDocComment(psiMethod, "param")
        if (subTagMap.isEmpty()) {
            return null
        }

        val methodParamComment: KV<String, Any> = KV.create()
        val parameters = psiMethod.parameterList.parameters
        subTagMap.entries.forEach { entry ->
            val name: String = entry.key
            val value: String? = entry.value
            if (value.notNullOrBlank()) {

                val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                val comment = linkExtractor!!.extract(value, psiMethod, object : AbstractLinkResolve() {

                    override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                        psiClassHelper!!.resolveEnumOrStatic(
                            plainText,
                            parameters.firstOrNull { it.name == name } ?: psiMethod,
                            name
                        )
                            ?.let { options.addAll(it) }

                        return super.linkToPsiElement(plainText, linkTo)
                    }

                    override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                        return linkResolver!!.linkToClass(linkClass)
                    }

                    override fun linkToType(plainText: String, linkType: PsiType): String? {
                        return jvmClassHelper!!.resolveClassInType(linkType)?.let {
                            linkResolver!!.linkToClass(it)
                        }
                    }

                    override fun linkToField(plainText: String, linkField: PsiField): String? {
                        return linkResolver!!.linkToProperty(linkField)
                    }

                    override fun linkToMethod(plainText: String, linkMethod: PsiMethod): String? {
                        return linkResolver!!.linkToMethod(linkMethod)
                    }

                    override fun linkToUnresolved(plainText: String): String? {
                        return plainText
                    }
                })

                methodParamComment[name] = comment ?: ""
                if (options.notNullOrEmpty()) {
                    methodParamComment["$name@options"] = options
                }
            }

        }

        return methodParamComment
    }

    /**
     * @param handle the handle will be called in ReadUI
     */
    fun foreachMethod(
        cls: PsiClass, handle: (ExplicitMethod) -> Unit,
    ) {
        actionContext.runInReadUI {
            val methods = duckTypeHelper!!.explicit(cls)
                .methods()
                .stream()
                .filter { !jvmClassHelper!!.isBasicMethod(it.psi().name) }
                .filter { !it.psi().hasModifierProperty("static") }
                .filter { !it.psi().isConstructor }
                .filter { !shouldIgnore(it) }
                .toList()
            actionContext.runAsync {
                val boundary = actionContext.createBoundary()
                try {
                    for (method in methods) {
                        actionContext.callInReadUI {
                            handle(method)
                        }
                        boundary.waitComplete(false)
                        Thread.sleep(100)
                    }
                } finally {
                    boundary.remove()
                }
            }
        }
    }

    protected open fun shouldIgnore(explicitElement: ExplicitElement<*>): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, explicitElement) ?: false
    }

    protected open fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    fun foreachPsiMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        actionContext.runInReadUI {
            jvmClassHelper!!.getAllMethods(cls)
                .stream()
                .filter { !jvmClassHelper.isBasicMethod(it.name) }
                .filter { !it.hasModifierProperty("static") }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
        }
    }

    fun export(): List<Doc> {
        val docs: MutableList<Doc> = Collections.synchronizedList(java.util.ArrayList())
        export { docs.add(it) }
        return docs
    }

    fun export(handle: (Doc) -> Unit) {
        logger.info("Start find apis...")
        val psiClassQueue: BlockingQueue<PsiClass> = LinkedBlockingQueue()

        val boundary = actionContext.createBoundary()

        actionContext.runAsync {
            SelectedHelper.Builder()
//                .dirFilter { dir, callBack ->
//                    try {
//                        val yes = messagesHelper.showYesNoDialog(
//                            "Export the api in directory [${ActionUtils.findCurrentPath(dir)}]?",
//                            "Confirm",
//                            Messages.getQuestionIcon()
//                        )
//                        if (yes == Messages.YES) {
//                            callBack(true)
//                        } else {
//                            logger.debug("Cancel the operation export api from [${
//                                ActionUtils.findCurrentPath(dir)
//                            }]!")
//                            callBack(false)
//                        }
//                    } catch (e: Exception) {
//                        callBack(false)
//                    }
//                }
                .fileFilter { file -> FileType.acceptable(file.name) }
                .classHandle {
                    psiClassQueue.add(it)
                }
                .traversal()
        }

        while (true) {
            val psiClass = psiClassQueue.poll()
            if (psiClass == null) {
                if (boundary.waitComplete(100, false)
                    && psiClassQueue.isEmpty()
                ) {
                    boundary.remove()
                    boundary.close()
                    break
                }
            } else {
                val classQualifiedName = actionContext.callInReadUI { psiClass.qualifiedName }
                LOG.info("wait api parsing... $classQualifiedName")
                actionContext.withBoundary {
                    classExporter!!.export(psiClass) { handle(it) }
                }
                LOG.info("api parse $classQualifiedName completed.")
            }
        }
    }
}

private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ClassApiExporterHelper::class.java)