package com.itangcent.easyapi.core.psi

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.IrType
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Contract for [ObjectModel.ref] at the producer: `DefaultPsiClassHelper`.
 *
 * `Single.type` is a projection, and a projection loses the source. This file pins what the
 * projection must **not** lose, so the "a branch forgot to record where this came from" failure
 * shows up as a failing assertion instead of as an OpenAPI document that inlines the same class
 * five times and can never point a `$ref` at it.
 *
 * The rules asserted here:
 * - every node records the type it was *declared* with, including nested classes (the class name
 *   is otherwise unrecoverable once the object is expanded into fields);
 * - a boxed primitive and its keyword stay distinguishable (`Integer` vs `int`) — the one fact
 *   [com.itangcent.easyapi.core.psi.type.ResolvedType.qualifiedName] collapses on purpose;
 * - an unresolved declaration keeps the spelling it was written with, even though the JSON word
 *   for it is guessed;
 * - a file field records the class it was declared as, next to the `file` marker the resolver
 *   substituted for it (the marker names no class, so it cannot stand alone);
 * - a node nobody declared (rule-configured field) carries no ref at all, rather than a made-up one.
 */
class ObjectModelRefContractTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultPsiClassHelper

    override fun setUp() {
        super.setUp()
        helper = DefaultPsiClassHelper.getInstance(project)
    }

    fun testEveryNodeRecordsTheTypeItWasDeclaredWith() = runBlocking {
        // The light fixture has no JDK on its classpath: without these stubs `String`/`Integer`
        // arrive as unresolved *simple* names, which is a fixture artefact rather than the
        // production spelling this test is about (see the unresolved-declaration test below,
        // which pins that fallback on purpose).
        loadJDKClass("java.lang.String")
        loadJDKClass("java.lang.Integer")
        loadFile(
            "model/Address.java",
            """
            package model;
            public class Address {
                public String city;
            }
            """.trimIndent()
        )
        loadFile(
            "model/RefHolder.java",
            """
            package model;
            public class RefHolder {
                public int primitive;
                public Integer boxed;
                public String name;
                public Address address;
                public Address[] addresses;
            }
            """.trimIndent()
        )

        val model = helper.buildObjectModel(findClass("model.RefHolder")!!) as ObjectModel.Object

        assertEquals(
            mapOf(
                // The root object records the class it was expanded from — this is the fact that
                // lets a document name the shape instead of calling it `object`.
                "<root>" to "model.RefHolder",
                "primitive" to "int",
                // Boxed and unboxed stay apart; `qualifiedName()` would answer `int` for both.
                "boxed" to "java.lang.Integer",
                "name" to "java.lang.String",
                "address" to "model.Address",
                "addresses" to "model.Address[]"
            ),
            mapOf(
                "<root>" to model.ref,
                "primitive" to model.refOf("primitive"),
                "boxed" to model.refOf("boxed"),
                "name" to model.refOf("name"),
                "address" to model.refOf("address"),
                "addresses" to model.refOf("addresses")
            )
        )
    }

    fun testANestedObjectRecordsItsOwnClassName() = runBlocking {
        loadFile(
            "model/Address.java",
            """
            package model;
            public class Address {
                public String city;
            }
            """.trimIndent()
        )
        loadFile(
            "model/Person.java",
            """
            package model;
            public class Person {
                public Address address;
            }
            """.trimIndent()
        )

        val model = helper.buildObjectModel(findClass("model.Person")!!) as ObjectModel.Object
        val address = model.fields["address"]?.model as ObjectModel.Object

        // Not only the root: the nested node knows its own class, which is what makes a
        // `$ref`/`title` possible for a class that appears below the top level.
        assertEquals("model.Address", address.ref)
    }

    fun testAFileFieldRecordsTheClassItWasDeclaredAs() = runBlocking {
        loadJDKClass("org.springframework.web.multipart.MultipartFile")
        loadFile(
            "model/UploadForm.java",
            """
            package model;
            import org.springframework.web.multipart.MultipartFile;
            public class UploadForm {
                public String title;
                public MultipartFile attachment;
            }
            """.trimIndent()
        )

        val model = helper.buildObjectModel(findClass("model.UploadForm")!!) as ObjectModel.Object
        val attachment = model.fields["attachment"]?.model?.asSingle()

        // `file` is neither a JSON type nor a class name, so it cannot be the only thing the
        // model records: without the declaration a document can never say *which* type a file
        // field was. The resolver substitutes the `__file__` marker before a ClassType can form,
        // which is why the declaration has to be carried through that substitution.
        assertEquals(IrType.FILE, attachment?.type)
        assertEquals("org.springframework.web.multipart.MultipartFile", attachment?.ref)
    }

    fun testAnUnresolvedDeclarationKeepsTheSpellingItWasWrittenWith() = runBlocking {
        loadFile(
            "model/UnresolvedHolder.java",
            """
            package model;
            public class UnresolvedHolder {
                public NotOnTheClasspath missing;
            }
            """.trimIndent()
        )

        val model = helper.buildObjectModel(findClass("model.UnresolvedHolder")!!) as ObjectModel.Object

        // The JSON word for this is still a guess, but the declaration itself survives the guess:
        // before `ref`, `Interpreter` had already become `int` by this point and the original
        // spelling was gone for good.
        assertEquals("NotOnTheClasspath", model.refOf("missing"))
    }

    fun testARuleConfiguredFieldCarriesNoRef() = runBlocking {
        loadFile(
            "model/Configured.java",
            """
            package model;
            public class Configured {
                public String id;
            }
            """.trimIndent()
        )
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "json.additional.field" to """{"name":"extra","type":"string","desc":"declared by configuration"}"""
            )
        )
        project.registerServiceInstance(
            serviceInterface = RuleEngine::class.java,
            instance = RuleEngine.getInstance(project)
        )

        val model = helper.buildObjectModel(findClass("model.Configured")!!) as ObjectModel.Object

        assertTrue("the rule-configured field must be present", model.fields.containsKey("extra"))
        // Nothing declared this field's type, so it must not pretend that something did: a
        // fabricated ref would make a document claim a class name that does not exist.
        assertNull(model.fields["extra"]?.model?.ref)
        assertEquals(IrType.STRING, model.fields["extra"]?.model?.asSingle()?.type)
    }

    private fun ObjectModel.Object.refOf(field: String): String? = fields[field]?.model?.ref
}
