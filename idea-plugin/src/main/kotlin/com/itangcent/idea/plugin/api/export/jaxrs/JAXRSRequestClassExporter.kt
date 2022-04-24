package com.itangcent.idea.plugin.api.export.jaxrs

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.element.ExplicitField

/**
 * Support export apis from jax-rs.
 * dosc:
 * - https://www.oracle.com/technical-resources/articles/java/jax-rs.html
 * - https://cxf.apache.org/docs/jax-rs.html
 * - https://code.quarkus.io/
 *
 * @author tangcent
 */
@Singleton
@ConditionOnSimple(false)
@ConditionOnClass(JAXRSClassName.PATH_ANNOTATION)
@ConditionOnSetting("jaxrsEnable")
open class JAXRSRequestClassExporter : RequestClassExporter() {

    @Inject
    protected lateinit var annotationHelper: AnnotationHelper

    @Inject
    protected val commentResolver: CommentResolver? = null

    @Inject
    protected lateinit var jaxrsBaseAnnotationParser: JAXRSBaseAnnotationParser

    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {
        var basePath = URL.of(findPath(cls))
        val prefixPath = ruleComputer.computer(ClassExportRuleKeys.CLASS_PREFIX_PATH, cls)
        if (prefixPath.notNullOrBlank()) {
            basePath = URL.of(prefixPath).concat(basePath)
        }
        classExportContext.setExt("basePath", basePath)
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return jaxrsBaseAnnotationParser.hasApi(psiClass)
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return jaxrsBaseAnnotationParser.isApi(psiMethod)
    }

    override fun processMethod(methodExportContext: MethodExportContext, request: Request) {
        super.processMethod(methodExportContext, request)

        val classExportContext = methodExportContext.classContext()
        val basePath: URL = classExportContext?.getExt("basePath") ?: URL.nil()
        val psiMethod = methodExportContext.psi()
        request.method = jaxrsBaseAnnotationParser.findHttpMethod(psiMethod) ?: HttpMethod.NO_METHOD
        val httpPath = basePath.concat(URL.of(findPath(psiMethod)))
        requestBuilderListener.setPath(methodExportContext, request, httpPath)
    }

    override fun processMethodParameter(
        request: Request,
        parameterExportContext: ParameterExportContext,
        paramDesc: String?
    ) {
        processParameterOrField(request, parameterExportContext, paramDesc)
    }

    private fun processParameterOrField(
        request: Request,
        exportContext: VariableExportContext,
        paramDesc: String?
    ) {
        //@BeanParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.BEAN_PARAM_ANNOTATION)) {
            val beanType = exportContext.type()?.unbox() as? SingleDuckType ?: return
            duckTypeHelper.explicit(beanType).collectFields {
                processParameterOrField(request, FieldExportContext(exportContext, it), null)
            }
            return
        }

        var ultimateComment = (paramDesc ?: "")
        exportContext.type()?.let { duckType ->
            commentResolver!!.resolveCommentForType(duckType, exportContext.psi())?.let {
                ultimateComment = "$ultimateComment $it"
            }
        }

        val readParamDefaultValue = readParamDefaultValue(exportContext.element())

        if (readParamDefaultValue.notNullOrBlank()) {
            exportContext.setDefaultVal(readParamDefaultValue!!)
        }

        if (exportContext.required() == null) {
            ruleComputer.computer(ClassExportRuleKeys.PARAM_REQUIRED, exportContext.element())?.let {
                exportContext.setRequired(it)
            }
        }

        //@FormParam -> form
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.FORM_PARAM_ANNOTATION)) {
            findParamName(exportContext, JAXRSClassName.FORM_PARAM_ANNOTATION)
            if (request.method == HttpMethod.NO_METHOD) {
                requestBuilderListener.setMethod(
                    exportContext, request,
                    ruleComputer.computer(
                        ClassExportRuleKeys.METHOD_DEFAULT_HTTP_METHOD,
                        exportContext.methodContext()!!.element()
                    )
                        ?: HttpMethod.POST
                )
            }
            addParamAsForm(exportContext, request, exportContext.unbox(), ultimateComment)
            return
        }

        //@QueryParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.QUERY_PARAM_ANNOTATION)) {
            findParamName(exportContext, JAXRSClassName.QUERY_PARAM_ANNOTATION)
            addParamAsQuery(exportContext, request, exportContext.unbox(), ultimateComment)
            return
        }

        //@PathParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.PATH_PARAM_ANNOTATION)) {
            val paramName = findParamName(exportContext, JAXRSClassName.PATH_PARAM_ANNOTATION)
            requestBuilderListener.addPathParam(exportContext, request, paramName, ultimateComment)
            return
        }

        //@HeaderParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.HEADER_PARAM_ANNOTATION)) {
            val paramName = findParamName(exportContext, JAXRSClassName.HEADER_PARAM_ANNOTATION)

            val header = Header()
            header.name = paramName
            header.value = exportContext.defaultVal()
            header.desc = ultimateComment
            header.required = exportContext.required()
            requestBuilderListener.addHeader(exportContext, request, header)
            return
        }

        //@CookieParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.COOKIE_PARAM_ANNOTATION)) {
            val paramName = findParamName(exportContext, JAXRSClassName.COOKIE_PARAM_ANNOTATION)

            requestBuilderListener.appendDesc(
                exportContext,
                request, if (exportContext.required() == true) {
                    "Need cookie:$paramName ($ultimateComment)"
                } else {
                    val defaultValue = exportContext.defaultVal()
                    if (defaultValue.isNullOrBlank()) {
                        "Cookie:$paramName ($ultimateComment)"
                    } else {
                        "Cookie:$paramName=$defaultValue ($ultimateComment)"
                    }
                }
            )
            return
        }

        //@MatrixParam
        if (annotationHelper.hasAnn(exportContext.psi(), JAXRSClassName.MATRIX_PARAM_ANNOTATION)) {
            //todo:
            logger.info(
                "@MatrixParam not be supported.\n" +
                        "Create a feature quest issue? 👉🏻https://github.com/tangcent/easy-yapi/issues/new/"
            )
            return
        }

        //as body by default
        if (exportContext is FieldExportContext) {
            addParamAsQuery(exportContext, request, exportContext.unbox(), ultimateComment)
        } else {
            setRequestBody(
                exportContext,
                request, exportContext.raw(), paramDesc
            )
        }
        return
    }

    private fun findParamName(exportContext: VariableExportContext, annName: String): String {
        annotationHelper.findAttrAsString(exportContext.psi(), annName)
            ?.also { exportContext.setParamName(it) }
            ?.let { return it }
        return exportContext.name()
    }

    //region process spring annotation-------------------------------------------------------------------

    private fun findPath(ele: PsiElement): String? {
        return annotationHelper.findAttrAsString(ele, JAXRSClassName.PATH_ANNOTATION)
    }

    //endregion process spring annotation-------------------------------------------------------------------

    class FieldExportContext(
        val parent: ExportContext,
        val field: ExplicitField
    ) : AbstractExportContext(parent), VariableExportContext {

        /**
         * Returns the name of the element.
         *
         * @return the element name.
         */
        override fun name(): String {
            return field.name()
        }

        /**
         * Returns the type of the variable.
         *
         * @return the variable type.
         */
        override fun type(): DuckType {
            return field.getType()
        }

        override fun element(): ExplicitField {
            return field
        }

        override fun psi(): PsiField {
            return field.psi()
        }
    }
}
