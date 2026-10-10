package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

/**
 * Exporting from a method or class selected in the editor.
 *
 * A common server layout declares the mapping annotations on an interface and the framework
 * annotation (`@RestController`) on the implementation, so the interface itself is not an API
 * class. Selecting a contract member used to yield "No API endpoints found" because the class
 * scan dropped the interface at the API-class gate. [ApiScanner.scanSelection] now resolves the
 * implementations of an explicitly selected contract.
 *
 * ```
 * PublicContract (interface: @GetMapping/@PostMapping)   PublicContractImpl (@RestController)
 * ```
 */
class ContractSelectionExportTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")

        // Every type a fixture references must be imported explicitly — the light fixture
        // cannot resolve an unimported same-package type, and an unresolved supertype or
        // parameter type breaks findSuperMethods() (and with it method dedup).
        loadFile(
            "api/contract/PublicContract.java",
            """
            package com.itangcent.api.contract;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;

            public interface PublicContract {

                @GetMapping("/hello")
                Result hello();

                @PostMapping("/save")
                Result save(String name);
            }
            """.trimIndent()
        )
        loadFile(
            "api/contract/PublicContractImpl.java",
            """
            package com.itangcent.api.contract;

            import com.itangcent.api.contract.PublicContract;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class PublicContractImpl implements PublicContract {

                @Override
                public Result hello() {
                    return null;
                }

                @Override
                public Result save(String name) {
                    return null;
                }
            }
            """.trimIndent()
        )
        loadFile(
            "api/contract/LonelyContract.java",
            """
            package com.itangcent.api.contract;

            import org.springframework.web.bind.annotation.GetMapping;

            public interface LonelyContract {

                @GetMapping("/lonely")
                String lonely();
            }
            """.trimIndent()
        )
        loadFile(
            "api/contract/PlainClass.java",
            """
            package com.itangcent.api.contract;

            public class PlainClass {

                public String value() {
                    return null;
                }
            }
            """.trimIndent()
        )
    }

    private val apiScanner: ApiScanner
        get() = ApiScanner.getInstance(project)

    private fun contract() = findClass("com.itangcent.api.contract.PublicContract")!!

    private fun paths(endpoints: List<com.itangcent.easyapi.core.export.ApiEndpoint>): List<String> =
        endpoints.map { (it.metadata as HttpMetadata).path }

    /** The reported symptom: exporting from a method of the contract interface. */
    fun testScanSelectionOnContractMethodFindsItsEndpoint() = runTest {
        val selected = findMethod(contract(), "save")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(selected)))

        assertEquals(
            "Selecting a contract method must export the endpoint it declares",
            listOf("/save"),
            paths(endpoints)
        )
    }

    /** The other contract member, to prove the selection is still narrowed to the chosen method. */
    fun testScanSelectionOnContractMethodExcludesSiblingMethods() = runTest {
        val selected = findMethod(contract(), "hello")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(selected)))

        assertEquals(
            "Selecting one contract method must not export its siblings",
            listOf("/hello"),
            paths(endpoints)
        )
    }

    /** Selecting the contract type itself exports every endpoint it declares. */
    fun testScanSelectionOnContractClassReturnsAllEndpoints() = runTest {
        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(contract())))

        assertEquals(
            "Selecting the contract class must export all of its endpoints",
            listOf("/hello", "/save"),
            paths(endpoints)
        )
    }

    /** Selecting the implementation keeps working — a concrete API class needs no expansion. */
    fun testScanSelectionOnImplementationMethodStillWorks() = runTest {
        val impl = findClass("com.itangcent.api.contract.PublicContractImpl")!!
        val selected = findMethod(impl, "save")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(selected)))

        assertEquals(
            "Selecting an implementation method must still export its endpoint",
            listOf("/save"),
            paths(endpoints)
        )
    }

    /** A contract with no implementation yields nothing — and must not fail. */
    fun testScanSelectionOnUnimplementedContractReturnsNothing() = runTest {
        val lonely = findClass("com.itangcent.api.contract.LonelyContract")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(lonely)))

        assertTrue(
            "A contract with no API implementation has no endpoints to export",
            endpoints.isEmpty()
        )
    }

    /** Only contracts are expanded; a plain concrete class must never be treated as one. */
    fun testScanSelectionOnPlainClassReturnsNothing() = runTest {
        val plain = findClass("com.itangcent.api.contract.PlainClass")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(plain)))

        assertTrue(
            "A non-API concrete class has no endpoints and no implementations to scan",
            endpoints.isEmpty()
        )
    }
}
