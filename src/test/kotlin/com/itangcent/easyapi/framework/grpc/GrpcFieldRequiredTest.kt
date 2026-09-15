package com.itangcent.easyapi.framework.grpc

import com.itangcent.easyapi.core.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for `field.required` on protobuf messages (issue #1458).
 *
 * `GrpcTypeParser` hard-coded `required = false` ("protobuf3 fields are all
 * optional"). That is the correct *framework default*, but `field.required` must
 * still be able to override it.
 */
class GrpcFieldRequiredTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var parser: GrpcTypeParser

    override fun setUp() {
        super.setUp()
        loadFile("grpc/GeneratedMessageV3.java")
        loadFile("grpc/ProtoUser.java")
        parser = GrpcTypeParser(DocMetadataResolver.getInstance(project))
    }

    override fun createConfigReader() = TestConfigReader.fromRules(
        project,
        // `it` is the getter the field was derived from.
        "field.required" to """groovy:it.name().endsWith("Name")"""
    )

    fun testFieldRequiredRuleIsHonoured() = runTest {
        val psiClass = findClass("com.itangcent.grpc.ProtoUser")
        assertNotNull("ProtoUser should be resolvable", psiClass)

        val model = parser.parseMessageType(psiClass!!)
        assertNotNull("ProtoUser should be parsed as a protobuf message", model)
        val fields = (model as? ObjectModel.Object)?.fields
        assertNotNull("ProtoUser should expose its getters as fields", fields)

        assertTrue(
            "field.required must be honoured for protobuf fields; " +
                "actual=${fields!!.map { "${it.key}=${it.value.required}" }}",
            fields["name"]!!.required
        )
        assertFalse(
            "a field without a matching field.required rule keeps the protobuf3 default",
            fields["id"]!!.required
        )
    }
}
