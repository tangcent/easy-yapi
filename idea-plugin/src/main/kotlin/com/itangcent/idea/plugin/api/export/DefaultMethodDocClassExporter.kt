package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.*
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import org.apache.commons.lang3.StringUtils
import java.util.*
import kotlin.reflect.KClass

open class DefaultMethodDocClassExporter : ClassExporter, Worker {

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

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
    protected val methodInferHelper: MethodInferHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    @Inject
    protected var apiHelper: ApiHelper? = null

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
                            exportMethodApi(cls, method, kv, docHandle)
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
    protected open fun processClass(cls: PsiClass, kv: KV<String, Any?>) {
    }

    @Suppress("UNUSED")
    protected open fun hasApi(psiClass: PsiClass): Boolean {
        return true
    }

    @Suppress("UNUSED")
    protected open fun isApi(psiMethod: PsiMethod): Boolean {
        return true
    }

    @Suppress("UNUSED")
    protected open fun shouldIgnore(psiElement: PsiElement): Boolean {
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

    private fun exportMethodApi(
            psiClass: PsiClass, method: PsiMethod, kv: KV<String, Any?>,
            docHandle: DocHandle
    ) {

        actionContext!!.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = PsiMethodResource(method, psiClass)

        processMethod(method, kv, methodDoc)

        processMethodParameters(method, methodDoc)

        processRet(method, methodDoc)

        processCompleted(method, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, methodDoc: MethodDoc) {
        apiHelper!!.nameAndAttrOfApi(method, {
            methodDocHelper!!.setName(methodDoc, it)
        }, {
            methodDocHelper!!.appendDesc(methodDoc, it)
        })
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

                val descOfReturn = docHelper!!.findDocByTag(method, "return")

                if (descOfReturn.notNullOrBlank()) {
                    val methodReturnMain = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_RETURN_MAIN, method)
                    if (methodReturnMain.isNullOrBlank()) {
                        methodDocHelper.appendRetDesc(methodDoc, descOfReturn)
                    } else {
                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                        val comment = linkExtractor!!.extract(descOfReturn, method, object : AbstractLinkResolve() {

                            override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                                psiClassHelper!!.resolveEnumOrStatic(plainText, method, "")
                                        ?.let { options.addAll(it) }

                                return super.linkToPsiElement(plainText, linkTo)
                            }

                            override fun linkToType(plainText: String, linkType: PsiType): String? {
                                return jvmClassHelper!!.resolveClassInType(linkType)?.let {
                                    linkResolver!!.linkToClass(it)
                                }
                            }

                            override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                                return linkResolver!!.linkToClass(linkClass)
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

                        if (comment.notNullOrBlank()) {
                            if (!KVUtils.addKeyComment(typedResponse, methodReturnMain, comment!!)) {
                                methodDocHelper.appendRetDesc(methodDoc, comment)
                            }
                        }
                        if (options.notNullOrEmpty()) {
                            if (!KVUtils.addKeyOptions(typedResponse, methodReturnMain, options)) {
                                methodDocHelper.appendRetDesc(methodDoc, KVUtils.getOptionDesc(options))
                            }
                        }
                    }
                }

            } catch (e: ProcessCanceledException) {
                //ignore cancel
            } catch (e: Throwable) {
                logger!!.traceError("error to parse body", e)

            }
        }
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
                        .filterNot { it.isNullOrBlank() }
                        .forEach {
                            when {
                                name == null -> name = it
                                value == null -> value = it
                                else -> value += it
                            }
                        }
                if (StringUtils.isNoneBlank(name, value)) {
                    if (methodParamComment == null) methodParamComment = KV.create()

                    val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                    val comment = linkExtractor!!.extract(value, psiMethod, object : AbstractLinkResolve() {

                        override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                            psiClassHelper!!.resolveEnumOrStatic(plainText, psiMethod, name!!)
                                    ?.let { options.addAll(it) }

                            return super.linkToPsiElement(plainText, linkTo)
                        }

                        override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                            return linkResolver!!.linkToClass(linkClass)
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

                    methodParamComment[name!!] = comment ?: ""
                    if (options.notNullOrEmpty()) {
                        methodParamComment["$name@options"] = options
                    }

                }
            }
        }
        return methodParamComment
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        jvmClassHelper!!.getAllMethods(cls)
                .filter { !jvmClassHelper.isBasicMethod(it.name) }
                .filter { !it.hasModifierProperty("static") }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    private fun processMethodParameters(method: PsiMethod, methodDoc: MethodDoc) {

        val params = method.parameterList.parameters

        if (params.isNotEmpty()) {

            val paramDocComment = extractParamComment(method)

            for (param in params) {

                if (ruleComputer!!.computer(ClassExportRuleKeys.PARAM_IGNORE, param) == true) {
                    continue
                }

                processMethodParameter(method, methodDoc, param, paramDocComment?.get(param.name!!)?.toString())
            }
        }

    }

    protected fun processMethodParameter(
            method: PsiMethod,
            methodDoc: MethodDoc,
            param: PsiParameter,
            paramDesc: String?
    ) {
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
                methodInferHelper!!.setMaxDeep(inferMaxDeep())
                logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(method) + "]")
                methodInferHelper.inferReturn(method)
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