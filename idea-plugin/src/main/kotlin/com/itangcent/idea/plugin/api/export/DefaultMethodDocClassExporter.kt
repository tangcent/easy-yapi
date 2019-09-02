package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.util.containers.isNullOrEmpty
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodReturnInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.DuckTypeHelper
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import com.itangcent.intellij.util.traceError
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.regex.Pattern
import kotlin.reflect.KClass

open class DefaultMethodDocClassExporter : ClassExporter, Worker {

    override fun support(docType: KClass<*>): Boolean {
        return docType == MethodDoc::class && methodDocEnable()
    }

    private var statusRecorder: StatusRecorder = StatusRecorder()

    override fun status(): WorkerStatus {
        return statusRecorder.status()
    }

    override fun waitCompleted() {
        return statusRecorder.waitCompleted()
    }

    override fun cancel() {
        return statusRecorder.cancel()
    }

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val docParseHelper: DocParseHelper? = null

    @Inject
    protected val methodDocHelper: MethodDocHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val methodReturnInferHelper: MethodReturnInferHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (!methodDocEnable()) {
            return false
        }
        if (cls !is PsiClass) return false
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> return false
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    foreachMethod(cls) { method ->
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(method, kv, docHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        return true
    }

    @Suppress("UNUSED")
    protected fun processClass(cls: PsiClass, kv: KV<String, Any?>) {
    }

    @Suppress("UNUSED")
    protected fun hasApi(psiClass: PsiClass): Boolean {
        return true
    }

    @Suppress("UNUSED")
    protected fun isApi(psiMethod: PsiMethod): Boolean {
        return true
    }

    @Suppress("UNUSED")
    open protected fun shouldIgnore(psiElement: PsiElement): Boolean {
        if (ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) == true) {
            return true
        }

        if (psiElement is PsiClass) {
            if (ruleComputer.computer(ClassExportRuleKeys.CLASS_FILTER, psiElement) == false) {
                return true
            }
        } else {
            if (ruleComputer.computer(ClassExportRuleKeys.METHOD_FILTER, psiElement) == false) {
                return true
            }
        }

        return false
    }

    private fun exportMethodApi(method: PsiMethod, kv: KV<String, Any?>,
                                docHandle: DocHandle) {

        actionContext!!.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = method

        processMethod(method, kv, methodDoc)

        processMethodParameters(method, methodDoc)

        processRet(method, methodDoc)

        processCompleted(method, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, methodDoc: MethodDoc) {

        val attr: String?
        var attrOfMethod = findAttrOfMethod(method)
        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, method)

        if (attrOfMethod.isNullOrBlank()) {
            methodDocHelper!!.setName(methodDoc, method.name)
        } else {
            val lines = attrOfMethod.lines()
            attr = if (lines.size > 1) {//multi line
                lines.firstOrNull { it.isNotBlank() }
            } else {
                attrOfMethod
            }

            methodDocHelper!!.appendDesc(methodDoc, attrOfMethod)
            methodDocHelper.setName(methodDoc, attr ?: method.name)
        }

        readMethodDoc(method)?.let {
            methodDocHelper.appendDesc(methodDoc, docParseHelper.resolveLinkInAttr(it, method))
        }

    }

    protected open fun readMethodDoc(method: PsiMethod): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)
    }

    protected open fun processCompleted(method: PsiMethod, methodDoc: MethodDoc) {
        //call after process
    }

    protected open fun processRet(method: PsiMethod, methodDoc: MethodDoc) {

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val typedResponse = parseResponseBody(returnType, method)

                methodDocHelper!!.setRet(methodDoc, typedResponse)

            } catch (e: ProcessCanceledException) {
                //ignore cancel
            } catch (e: Throwable) {
                logger!!.error("error to parse body")
                logger.traceError(e)
            }
        }
    }

    open protected fun findAttrOfMethod(method: PsiMethod): String? {
        return DocCommentUtils.getAttrOfDocComment(method.docComment)
    }

    private fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
        val docComment = psiMethod.docComment
        var methodParamComment: KV<String, Any>? = null
        if (docComment != null) {
            for (paramDocTag in docComment.findTagsByName("param")) {
                var name: String? = null
                var value: String? = null
                paramDocTag.dataElements
                        .asSequence()
                        .map { it?.text }
                        .filterNot { StringUtils.isBlank(it) }
                        .forEach {
                            when {
                                name == null -> name = it
                                value == null -> value = it
                                else -> value += it
                            }
                        }
                if (StringUtils.isNoneBlank(name, value)) {
                    if (methodParamComment == null) methodParamComment = KV.create()

                    if (value!!.contains("@link")) {
                        val pattern = Pattern.compile("\\{@link (.*?)\\}")
                        val matcher = pattern.matcher(value)

                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()

                        val sb = StringBuffer()
                        while (matcher.find()) {
                            matcher.appendReplacement(sb, "")
                            val linkClassOrProperty = matcher.group(1)
                            psiClassHelper!!.resolveEnumOrStatic(linkClassOrProperty, psiMethod, name!!)
                                    ?.let { options.addAll(it) }
                        }
                        matcher.appendTail(sb)
                        methodParamComment[name!!] = sb.toString()
                        if (!options.isNullOrEmpty()) {
                            methodParamComment["$name@options"] = options
                        }
                        continue
                    }
                    methodParamComment[name!!] = value!!
                }
            }
        }
        return methodParamComment
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    private fun processMethodParameters(method: PsiMethod, methodDoc: MethodDoc) {

        val params = method.parameterList.parameters

        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {
                processMethodParameter(method, methodDoc, param, paramDocComment?.get(param.name!!)?.toString())
            }
        }

    }

    protected fun processMethodParameter(method: PsiMethod, methodDoc: MethodDoc, param: PsiParameter, paramDesc: String?) {
        val typeObject = psiClassHelper!!.getTypeObject(param.type, method, JsonOption.READ_COMMENT)
        methodDocHelper!!.addParam(methodDoc, param.name!!, typeObject, paramDesc)
    }

    protected fun parseResponseBody(psiType: PsiType?, method: PsiMethod): Any? {

        if (psiType == null) {
            return null
        }

        return when {
            needInfer() && (!duckTypeHelper!!.isQualified(psiType, method) ||
                    PsiClassUtils.isInterface(psiType)) -> {
                methodReturnInferHelper!!.setMaxDeep(inferMaxDeep())
                logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(method) + "]")
                methodReturnInferHelper.inferReturn(method)
//                actionContext!!.callWithTimeout(20000) { methodReturnInferHelper.inferReturn(method) }
            }
            readGetter() -> psiClassHelper!!.getTypeObject(psiType, method, JsonOption.ALL)
            else -> psiClassHelper!!.getTypeObject(psiType, method, JsonOption.READ_COMMENT)
        }
    }

    private fun methodDocEnable(): Boolean {
        return settingBinder!!.read().methodDocEnable
    }

    private fun readGetter(): Boolean {
        return settingBinder!!.read().readGetter
    }

    private fun needInfer(): Boolean {
        return settingBinder!!.read().inferEnable
    }

    private fun inferMaxDeep(): Int {
        return settingBinder!!.read().inferMaxDeep
    }

}