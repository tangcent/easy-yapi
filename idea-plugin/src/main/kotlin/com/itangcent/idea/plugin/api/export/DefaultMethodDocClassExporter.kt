package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.*
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.append
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.group.JsonSetting
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import java.util.*
import kotlin.reflect.KClass

open class DefaultMethodDocClassExporter : ClassExporter, Worker {

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected lateinit var jvmClassHelper: JvmClassHelper

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
    protected val methodDocHelper: MethodDocHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val jsonSetting: JsonSetting? = null

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

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
        if (!methodDocEnable()) {
            completedHandle(cls)
            return false
        }
        if (cls !is PsiClass) {
            completedHandle(cls)
            return false
        }
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> {
                    completedHandle(cls)
                    return false
                }
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    completedHandle(cls)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    classApiExporterHelper.foreachMethod(cls) { explicitMethod ->
                        val method = explicitMethod.psi()
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(cls, explicitMethod, kv, docHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        completedHandle(cls)
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
            psiClass: PsiClass, method: ExplicitMethod, kv: KV<String, Any?>,
            docHandle: DocHandle
    ) {

        actionContext!!.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = PsiMethodResource(method.psi(), psiClass)

        processMethod(method, kv, methodDoc)

        processMethodParameters(method, methodDoc)

        processRet(method, methodDoc)

        processCompleted(method, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(method: ExplicitMethod, kv: KV<String, Any?>, methodDoc: MethodDoc) {
        apiHelper!!.nameAndAttrOfApi(method, {
            methodDocHelper!!.setName(methodDoc, it)
        }, {
            methodDocHelper!!.appendDesc(methodDoc, it)
        })
    }

    protected open fun processCompleted(method: ExplicitMethod, methodDoc: MethodDoc) {
        //call after process
    }

    protected open fun processRet(method: ExplicitMethod, methodDoc: MethodDoc) {

        val returnType = method.getReturnType()
        if (returnType != null) {
            try {
                val typedResponse = parseResponseBody(returnType, method)

                methodDocHelper!!.setRet(methodDoc, typedResponse)

                val descOfReturn = docHelper!!.findDocByTag(method.psi(), "return")

                if (descOfReturn.notNullOrBlank()) {
                    val methodReturnMain = ruleComputer!!.computer(ClassExportRuleKeys.METHOD_RETURN_MAIN, method)
                    if (methodReturnMain.isNullOrBlank()) {
                        methodDocHelper.appendRetDesc(methodDoc, descOfReturn)
                    } else {
                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                        val comment = linkExtractor!!.extract(descOfReturn, method.psi(), object : AbstractLinkResolve() {

                            override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                                psiClassHelper!!.resolveEnumOrStatic(plainText, method.psi(), "")
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

    private fun processMethodParameters(method: ExplicitMethod, methodDoc: MethodDoc) {

        val params = method.getParameters()

        if (params.isNotEmpty()) {

            val paramDocComment = classApiExporterHelper.extractParamComment(method.psi())

            for (param in params) {

                if (ruleComputer!!.computer(ClassExportRuleKeys.PARAM_IGNORE, param) == true) {
                    continue
                }

                ruleComputer.computer(ClassExportRuleKeys.PARAM_BEFORE, param)
                try {
                    processMethodParameter(methodDoc, param,
                            KVUtils.getUltimateComment(paramDocComment, param.name()).append(readParamDoc(param))
                    )
                } finally {
                    ruleComputer.computer(ClassExportRuleKeys.PARAM_AFTER, param)
                }
            }
        }
    }

    protected fun processMethodParameter(
            methodDoc: MethodDoc,
            param: ExplicitParameter,
            paramDesc: String?
    ) {
        val paramType = param.getType() ?: return
        val typeObject = psiClassHelper!!.getTypeObject(paramType, param.psi(),
                jsonSetting!!.jsonOptionForInput(JsonOption.READ_COMMENT))
        methodDocHelper!!.addParam(methodDoc, param.name(), typeObject, paramDesc, ruleComputer!!.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true)
    }

    protected fun parseResponseBody(duckType: DuckType?, method: ExplicitMethod): Any? {

        if (duckType == null) {
            return null
        }

        return when {
            needInfer() && (!duckTypeHelper!!.isQualified(duckType) ||
                    jvmClassHelper.isInterface(duckType)) -> {
                logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(method.psi()) + "]")
                methodInferHelper!!.inferReturn(method.psi())
//                actionContext!!.callWithTimeout(20000) { methodReturnInferHelper.inferReturn(method) }
            }
            else -> psiClassHelper!!.getTypeObject(duckType, method.psi(),
                    jsonSetting!!.jsonOptionForOutput(JsonOption.READ_COMMENT))
        }
    }

    protected open fun readParamDoc(explicitParameter: ExplicitParameter): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.PARAM_DOC, explicitParameter)
    }

    private fun methodDocEnable(): Boolean {
        return settingBinder!!.read().methodDocEnable
    }

    private fun needInfer(): Boolean {
        return settingBinder!!.read().inferEnable
    }

    private fun inferMaxDeep(): Int {
        return settingBinder!!.read().inferMaxDeep
    }

}