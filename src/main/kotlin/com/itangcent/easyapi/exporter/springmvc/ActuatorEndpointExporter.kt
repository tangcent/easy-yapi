package com.itangcent.easyapi.exporter.springmvc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

class ActuatorEndpointExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = "SpringActuator"

    private val scanner = ActuatorEndpointScanner()
    private val recognizer = ActuatorEndpointRecognizer()
    private val engine = RuleEngine.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isApiClass(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse actuator endpoint:$className")

        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)

        val endpoints: List<ApiEndpoint>
        try {
            endpoints = read { scanner.scan(psiClass) }

            for (endpoint in endpoints) {
                val method = endpoint.sourceMethod ?: continue
                engine.evaluate(RuleKeys.EXPORT_AFTER, method) { ctx ->
                    ctx.setExt("api", endpoint)
                }
            }
        } finally {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
        }

        LOG.info("after parse actuator endpoint:$className, found ${endpoints.size} endpoints")
        return endpoints
    }

    companion object : IdeaLog
}
