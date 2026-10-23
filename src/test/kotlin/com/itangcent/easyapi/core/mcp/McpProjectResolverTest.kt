package com.itangcent.easyapi.core.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * Tests for [McpProjectResolver]. The light fixture opens exactly one project, so the cases with
 * several open projects go through the overload that takes the open projects as a list.
 */
class McpProjectResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    // ---- against the projects open in the test IDE ----

    fun testTheOnlyOpenProjectIsUsedWithoutAPath() {
        assertSame("no path", project, found(McpProjectResolver.resolve(null)))
        assertSame("a blank path counts as none", project, found(McpProjectResolver.resolve("  ")))
    }

    fun testPathMatchesTheProjectRoot() {
        assertSame("exact root", project, found(McpProjectResolver.resolve(project.basePath)))
    }

    fun testPathMatchIgnoresSeparatorsAndATrailingSlash() {
        val root = project.basePath!!

        assertSame("backslashes and a trailing one", project, found(McpProjectResolver.resolve(root.replace('/', '\\') + "\\")))
        assertSame("a trailing slash", project, found(McpProjectResolver.resolve("$root/")))
    }

    fun testUnknownPathIsNotFoundAndListsTheOpenProjects() {
        val outside = File(project.basePath!!).parentFile.resolve("not-an-open-project").path

        val failed = failed(McpProjectResolver.resolve(outside))

        assertEquals("code", McpToolError.PROJECT_NOT_FOUND, failed.code)
        assertTrue("lists the open roots: ${failed.openProjects}", project.basePath in failed.openProjects)
    }

    // ---- a path inside a project ----

    /** Agents often know only their working directory, which may be a sub-directory of the project. */
    fun testDirectoryInsideAProjectPicksThatProject() {
        val a = openProject("a", ROOT_A)

        val resolved = found(McpProjectResolver.resolve("C:\\work\\a\\src\\main\\java", listOf(a, openProject("b", ROOT_B))))

        assertSame("the project that contains the directory", a, resolved)
    }

    fun testFileInsideAProjectPicksThatProject() {
        val b = openProject("b", ROOT_B)

        val resolved = found(McpProjectResolver.resolve("C:/work/b/pom.xml", listOf(openProject("a", ROOT_A), b)))

        assertSame("the project that contains the file", b, resolved)
    }

    /** Nested projects: the innermost root that contains the path wins, as in the IDE's own lookup. */
    fun testInnermostContainingProjectWins() {
        val outer = openProject("outer", ROOT_A)
        val inner = openProject("inner", "$ROOT_A/modules/inner")
        val projects = listOf(outer, inner)

        assertSame("inside the inner root", inner, found(McpProjectResolver.resolve("$ROOT_A/modules/inner/src", projects)))
        assertSame("outside the inner root", outer, found(McpProjectResolver.resolve("$ROOT_A/modules/other", projects)))
    }

    /** A shared name prefix is not containment: `C:/work/ab` is not inside `C:/work/a`. */
    fun testSiblingWithACommonPrefixIsNotInside() {
        val failed = failed(McpProjectResolver.resolve("C:/work/ab/src", listOf(openProject("a", ROOT_A))))

        assertEquals("code", McpToolError.PROJECT_NOT_FOUND, failed.code)
    }

    // ---- several open projects ----

    fun testSeveralOpenProjectsWithoutAPathIsAmbiguous() {
        val failed = failed(McpProjectResolver.resolve(null, listOf(openProject("a", ROOT_A), openProject("b", ROOT_B))))

        assertEquals("code", McpToolError.PROJECT_AMBIGUOUS, failed.code)
        assertEquals("lists every open root", listOf(ROOT_A, ROOT_B), failed.openProjects)
        assertTrue("says what to pass: ${failed.message}", failed.message.contains("projectPath"))
    }

    fun testPathPicksOneOfSeveralOpenProjects() {
        val b = openProject("b", ROOT_B)

        val resolved = found(McpProjectResolver.resolve("C:\\work\\b\\", listOf(openProject("a", ROOT_A), b)))

        assertSame("backslashes and a trailing one", b, resolved)
    }

    fun testLetterCaseFollowsTheFileSystem() {
        val resolution = McpProjectResolver.resolve(ROOT_A.uppercase(), listOf(openProject("a", ROOT_A)))

        assertEquals(
            "a case-insensitive file system ignores letter case",
            !SystemInfo.isFileSystemCaseSensitive,
            resolution is ProjectResolution.Found
        )
    }

    fun testNoOpenProjectIsNotFound() {
        val failed = failed(McpProjectResolver.resolve(null, emptyList()))

        assertEquals("code", McpToolError.PROJECT_NOT_FOUND, failed.code)
        assertTrue("nothing to list", failed.openProjects.isEmpty())
    }

    // ---- helpers ----

    private fun found(resolution: ProjectResolution): Project {
        assertTrue("expected Found, got $resolution", resolution is ProjectResolution.Found)
        return (resolution as ProjectResolution.Found).project
    }

    private fun failed(resolution: ProjectResolution): ProjectResolution.Failed {
        assertTrue("expected Failed, got $resolution", resolution is ProjectResolution.Failed)
        return resolution as ProjectResolution.Failed
    }

    private fun openProject(projectName: String, root: String): Project = mock {
        on { this.name } doReturn projectName
        on { this.basePath } doReturn root
    }

    private companion object {
        const val ROOT_A = "C:/work/a"
        const val ROOT_B = "C:/work/b"
    }
}
