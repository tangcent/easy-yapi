package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.*
import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.utils.append
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.api.export.core.MethodFilter
import com.itangcent.idea.plugin.api.export.Orders
import com.itangcent.idea.plugin.api.export.condition.ConditionOnDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.core.LinkResolver
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.IntelligentSettingsHelper
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.jvm.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.order.Order
import kotlin.reflect.KClass

@Order(Orders.GENERIC + Orders.METHOD_DOC)
@ConditionOnSimple(false)
@ConditionOnDoc("methodDoc")
@ConditionOnSetting("genericEnable", "methodDocEnable")
open class GenericMethodDocClassExporter : ClassExporter, Worker {

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected lateinit var jvmClassHelper: JvmClassHelper

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

    override fun support(docType: KClass<*>): Boolean {
        return docType == MethodDoc::class
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
    protected lateinit var logger: Logger

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected lateinit var methodDocBuilderListener: MethodDocBuilderListener

    @Inject
    protected lateinit var intelligentSettingsHelper: IntelligentSettingsHelper

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val methodInferHelper: MethodInferHelper? = null

    @Inject
    protected lateinit var ruleComputer: RuleComputer

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    @Inject
    protected var apiHelper: ApiHelper? = null

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
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
                    logger.info("ignore class:" + cls.qualifiedName)
                    completedHandle(cls)
                    return true
                }
            }

            logger.info("search api from:${cls.qualifiedName}")

            ruleComputer.computer(ClassExportRuleKeys.API_CLASS_PARSE_BEFORE, cls)

            try {
                val classExportContext = ClassExportContext(cls)

                processClass(cls, classExportContext)

                classApiExporterHelper.foreachMethod(cls) { explicitMethod ->
                    val method = explicitMethod.psi()
                    if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                        try {
                            ruleComputer.computer(ClassExportRuleKeys.API_METHOD_PARSE_BEFORE, explicitMethod)
                            exportMethodApi(cls, explicitMethod, classExportContext, docHandle)
                        } finally {
                            ruleComputer.computer(ClassExportRuleKeys.API_METHOD_PARSE_AFTER, explicitMethod)
                        }
                    }
                }
            } finally {
                ruleComputer.computer(ClassExportRuleKeys.API_CLASS_PARSE_AFTER, cls)
            }

        } catch (e: Exception) {
            logger.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        completedHandle(cls)
        return true
    }

    @Suppress("UNUSED")
    protected open fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {
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
        if (ruleComputer.computer(ClassExportRuleKeys.IGNORE, psiElement) == true) {
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
        psiClass: PsiClass, method: ExplicitMethod,
        classExportContext: ClassExportContext,
        docHandle: DocHandle
    ) {

        actionContext!!.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = PsiMethodResource(method.psi(), psiClass)

        val methodExportContext = MethodExportContext(classExportContext, method)

        methodDocBuilderListener.startProcessMethod(methodExportContext, methodDoc)

        processMethod(methodExportContext, methodDoc)

        processMethodParameters(methodExportContext, methodDoc)

        processRet(methodExportContext, methodDoc)

        processCompleted(methodExportContext, methodDoc)

        methodDocBuilderListener.startProcessMethod(methodExportContext, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(
        methodExportContext: MethodExportContext,
        methodDoc: MethodDoc
    ) {
        apiHelper!!.nameAndAttrOfApi(methodExportContext.element(), {
            methodDocBuilderListener.setName(methodExportContext, methodDoc, it)
        }, {
            methodDocBuilderListener.appendDesc(methodExportContext, methodDoc, it)
        })
    }

    protected open fun processCompleted(method: MethodExportContext, methodDoc: MethodDoc) {
        //call after process
    }

    protected open fun processRet(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {

        val returnType = methodExportContext.element().getReturnType()
        if (returnType != null) {
            try {
                val typedResponse = parseResponseBody(methodExportContext, returnType)

                methodDocBuilderListener.setRet(methodExportContext, methodDoc, typedResponse)

                val descOfReturn = docHelper!!.findDocByTag(methodExportContext.psi(), "return")

                if (descOfReturn.notNullOrBlank()) {
                    val methodReturnMain = ruleComputer.computer(
                        ClassExportRuleKeys.METHOD_RETURN_MAIN,
                        methodExportContext.element()
                    )
                    if (methodReturnMain.isNullOrBlank()) {
                        methodDocBuilderListener.appendRetDesc(
                            methodExportContext,
                            methodDoc, descOfReturn
                        )
                    } else {
                        val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                        val comment = linkExtractor!!.extract(
                            descOfReturn,
                            methodExportContext.psi(),
                            object : AbstractLinkResolve() {

                                override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                                    psiClassHelper!!.resolveEnumOrStatic(plainText, methodExportContext.psi(), "")
                                        ?.let { options.addAll(it) }

                                    return super.linkToPsiElement(plainText, linkTo)
                                }

                                override fun linkToType(plainText: String, linkType: PsiType): String? {
                                    return jvmClassHelper.resolveClassInType(linkType)?.let {
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
                                methodDocBuilderListener.appendRetDesc(methodExportContext, methodDoc, comment)
                            }
                        }
                        if (options.notNullOrEmpty()) {
                            if (!KVUtils.addKeyOptions(typedResponse, methodReturnMain, options)) {
                                methodDocBuilderListener.appendRetDesc(
                                    methodExportContext,
                                    methodDoc,
                                    KVUtils.getOptionDesc(options)
                                )
                            }
                        }
                    }
                }

            } catch (e: ProcessCanceledException) {
                //ignore cancel
            } catch (e: Throwable) {
                logger.traceError("error to parse body", e)

            }
        }
    }

    private fun processMethodParameters(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {

        val params = methodExportContext.element().getParameters()

        if (params.isNotEmpty()) {

            val paramDocComment = classApiExporterHelper.extractParamComment(methodExportContext.psi())

            for (param in params) {

                if (ruleComputer.computer(ClassExportRuleKeys.PARAM_IGNORE, param) == true) {
                    continue
                }

                ruleComputer.computer(ClassExportRuleKeys.API_PARAM_BEFORE, param)
                try {
                    processMethodParameter(
                        methodExportContext, methodDoc, param,
                        KVUtils.getUltimateComment(paramDocComment, param.name()).append(readParamDoc(param))
                    )
                } finally {
                    ruleComputer.computer(ClassExportRuleKeys.API_PARAM_AFTER, param)
                }
            }
        }
    }

    protected fun processMethodParameter(
        methodExportContext: MethodExportContext,
        methodDoc: MethodDoc,
        param: ExplicitParameter,
        paramDesc: String?
    ) {
        val paramType = param.getType() ?: return
        val typeObject = psiClassHelper!!.getTypeObject(
            paramType, param.psi(),
            intelligentSettingsHelper.jsonOptionForInput(JsonOption.READ_COMMENT)
        )
        methodDocBuilderListener.addParam(
            methodExportContext,
            methodDoc,
            param.name(),
            typeObject,
            paramDesc,
            ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, param) == true
        )
    }

    protected fun parseResponseBody(methodExportContext: MethodExportContext, duckType: DuckType?): Any? {

        if (duckType == null) {
            return null
        }

        return when {
            needInfer() && (!duckTypeHelper!!.isQualified(duckType) ||
                    jvmClassHelper.isInterface(duckType)) -> {
                logger.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(methodExportContext.psi()) + "]")
                methodInferHelper!!.inferReturn(methodExportContext.psi())
//                actionContext!!.callWithTimeout(20000) { methodReturnInferHelper.inferReturn(method) }
            }
            else -> psiClassHelper!!.getTypeObject(
                duckType, methodExportContext.psi(),
                intelligentSettingsHelper.jsonOptionForOutput(JsonOption.READ_COMMENT)
            )
        }
    }

    protected open fun readParamDoc(explicitParameter: ExplicitParameter): String? {
        return ruleComputer.computer(ClassExportRuleKeys.PARAM_DOC, explicitParameter)
    }

    private fun needInfer(): Boolean {
        return intelligentSettingsHelper.inferEnable()
    }
}