package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.intellij.psi.PsiManager
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
        loadFile(
            "api/contract/OtherApi.java",
            """
            package com.itangcent.api.contract;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class OtherApi {

                @GetMapping("/other")
                Result other();
            }
            """.trimIndent()
        )
    }

    private val apiScanner: ApiScanner
        get() = ApiScanner.getInstance(project)

    private fun contract() = findClass("com.itangcent.api.contract.PublicContract")!!

    private fun otherApi() = findClass("com.itangcent.api.contract.OtherApi")!!

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

    /**
     * The expansion is per class: one productive class in the selection must not suppress it for
     * the rest, so a contract selected alongside an ordinary API class is still expanded.
     */
    fun testScanSelectionOnMixedClassSelectionReachesTheContract() = runTest {
        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(contract(), otherApi())))

        assertEquals(
            "A contract selected alongside an API class must still be expanded",
            listOf("/hello", "/other", "/save"),
            paths(endpoints).sorted()
        )
    }

    /** The method-scoped path has the same per-class granularity. */
    fun testScanSelectionOnMixedMethodSelectionReachesTheContract() = runTest {
        val selected = listOf(
            findMethod(contract(), "save")!!,
            findMethod(otherApi(), "other")!!
        )

        val endpoints = apiScanner.scanSelection(SelectionScope(selected))

        assertEquals(
            "A contract method selected alongside another class's method must still be expanded",
            listOf("/other", "/save"),
            paths(endpoints).sorted()
        )
    }

    /**
     * The implementation found through the expansion is already part of the selection, so it must
     * not be scanned a second time — selecting both halves of the layout exports each endpoint
     * exactly once.
     */
    fun testScanSelectionOnContractAndImplementationDoesNotDuplicate() = runTest {
        val impl = findClass("com.itangcent.api.contract.PublicContractImpl")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(contract(), impl)))

        assertEquals(
            "Selecting the contract and its implementation must not export the endpoints twice",
            listOf("/hello", "/save"),
            paths(endpoints).sorted()
        )
    }

    /**
     * A directory selection is a bulk scan, not an explicit contract selection: it enumerates and
     * scans every class under the directory, but none of those classes counts as "pointed at
     * directly", so they must not be expanded. Without that gate a plain interface in the
     * directory would run an inheritors search and drag in an implementation living outside the
     * selection.
     *
     * One fixture asserts both halves at once: the controller inside the directory proves its
     * classes are still enumerated and scanned, the implementation outside it proves no inheritors
     * search ran.
     */
    fun testScanSelectionOnDirectoryScansItsClassesButDoesNotExpandThem() = runTest {
        val controllerFile = loadFile(
            "api/dirscan/DirScanController.java",
            """
            package com.itangcent.api.dirscan;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class DirScanController {

                @GetMapping("/dir")
                public Result dir() {
                    return null;
                }
            }
            """.trimIndent()
        )
        loadFile(
            "api/dirscan/DirScanContract.java",
            """
            package com.itangcent.api.dirscan;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;

            public interface DirScanContract {

                @GetMapping("/detached")
                Result detached();
            }
            """.trimIndent()
        )
        loadFile(
            "api/detached/DirScanContractImpl.java",
            """
            package com.itangcent.api.detached;

            import com.itangcent.api.dirscan.DirScanContract;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class DirScanContractImpl implements DirScanContract {

                @Override
                public Result detached() {
                    return null;
                }
            }
            """.trimIndent()
        )

        val directory = PsiManager.getInstance(project).findDirectory(controllerFile.virtualFile.parent)!!
        val scope = SelectionScope(listOf(directory))

        assertEquals(
            "A directory selection enumerates every class under the directory",
            setOf(
                findClass("com.itangcent.api.dirscan.DirScanContract")!!,
                findClass("com.itangcent.api.dirscan.DirScanController")!!
            ),
            scope.classes().toSet()
        )
        assertTrue(
            "A directory selection points at no class directly",
            scope.directlySelectedClasses().toList().isEmpty()
        )
        assertEquals(
            "The directory's own classes are still scanned, its interfaces are not expanded",
            listOf("/dir"),
            paths(apiScanner.scanSelection(scope)).sorted()
        )
    }
}
