package org.octopusden.octopus.quality.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskSelectionTest {
    private val cores = mapOf(":" to "root", ":a" to "a", ":a:core" to "core", ":b:core" to "core")

    // A bare name excludes the task in every module.
    @Test
    fun `bare task name excludes the task in a subproject`() {
        assertTrue(isTaskExcluded(":client", "test", setOf("test")))
    }

    // The qualified form excludes it in exactly one module.
    @Test
    fun `qualified path excludes the task only in the named project`() {
        val excluded = setOf(":client:test")
        assertTrue(isTaskExcluded(":client", "test", excluded))
        assertFalse(isTaskExcluded(":server", "test", excluded))
    }

    @Test
    fun `an unlisted task is not excluded`() {
        assertFalse(isTaskExcluded(":client", "test", setOf("integrationTest")))
    }

    @Test
    fun `an empty exclusion set excludes nothing`() {
        assertFalse(isTaskExcluded(":client", "test", emptySet()))
    }

    // The root project's path is already ":", so naive interpolation would build "::test" and
    // silently fail to match. Single-module builds run every gate on the root, so this is the
    // case that would break them.
    @Test
    fun `root project qualifies to a single colon, not a double one`() {
        assertEquals(":test", qualifiedTaskPath(":", "test"))
        assertTrue(isTaskExcluded(":", "test", setOf(":test")))
        assertFalse(isTaskExcluded(":", "test", setOf("::test")))
    }

    @Test
    fun `subproject path is qualified by prefixing its path`() {
        assertEquals(":client:test", qualifiedTaskPath(":client", "test"))
        assertEquals(":a:b:test", qualifiedTaskPath(":a:b", "test"))
    }

    // --- coverage-suite selection ---

    @Test
    fun `the standard test task is always a coverage suite`() {
        assertTrue(isCoverageSuite(":client", "test", emptySet(), emptySet()))
    }

    // The default must not move: an extra Test task nobody declared is not a coverage suite.
    @Test
    fun `an undeclared extra Test task is not a coverage suite`() {
        assertFalse(isCoverageSuite(":client", "unitTest", emptySet(), emptySet()))
    }

    @Test
    fun `a declared extra Test task is a coverage suite`() {
        assertTrue(isCoverageSuite(":client", "unitTest", emptySet(), setOf("unitTest")))
    }

    // Declaration is unqualified, so one entry covers the task in every module that defines it.
    @Test
    fun `declaration by bare name applies in every module`() {
        val declared = setOf("unitTest")
        assertTrue(isCoverageSuite(":client", "unitTest", emptySet(), declared))
        assertTrue(isCoverageSuite(":server", "unitTest", emptySet(), declared))
    }

    @Test
    fun `exclusion wins over declaration`() {
        assertFalse(isCoverageSuite(":client", "unitTest", setOf(":client:unitTest"), setOf("unitTest")))
        assertFalse(isCoverageSuite(":client", "unitTest", setOf("unitTest"), setOf("unitTest")))
    }

    // The case that motivated the property: a docker-bound `test` is excluded while the
    // docker-free `unitTest` still counts, in the same module.
    @Test
    fun `excluding the default suite leaves a declared extra suite selected`() {
        val excluded = setOf(":client:test")
        val declared = setOf("unitTest")
        assertFalse(isCoverageSuite(":client", "test", excluded, declared))
        assertTrue(isCoverageSuite(":client", "unitTest", excluded, declared))
    }

    @Test
    fun `a root-project suite exclusion is honoured`() {
        assertFalse(isCoverageSuite(":", "test", setOf(":test"), emptySet()))
        assertTrue(isCoverageSuite(":", "test", setOf(":other"), emptySet()))
    }

    @Test
    fun `coverage is skipped for a module whose test is excluded by path or bare name`() {
        assertTrue(isCoverageSkippedByExcludedTest(":lib", setOf(":lib:test")))
        assertTrue(isCoverageSkippedByExcludedTest(":lib", setOf("test")))
        assertTrue(isCoverageSkippedByExcludedTest(":", setOf(":test")))
    }

    @Test
    fun `coverage is not skipped for other modules or other excluded tasks`() {
        assertFalse(isCoverageSkippedByExcludedTest(":app", setOf(":lib:test")))
        assertFalse(isCoverageSkippedByExcludedTest(":lib", setOf(":lib:integrationTest")))
        assertFalse(isCoverageSkippedByExcludedTest(":lib", emptySet()))
    }

    @Test
    fun `per-module coverage leaves the root out only under kover when its report is unwired`() {
        val covered = listOf(":", ":app")
        val kover = org.octopusden.octopus.quality.CoverageExtension.Tool.KOVER
        val jacoco = org.octopusden.octopus.quality.CoverageExtension.Tool.JACOCO
        assertEquals(listOf(":app"), perModuleCoverage(covered, ":", rootKoverUnwired = true, tool = kover))
        assertEquals(covered, perModuleCoverage(covered, ":", rootKoverUnwired = false, tool = kover))
        assertEquals(covered, perModuleCoverage(covered, ":", rootKoverUnwired = true, tool = jacoco))
    }

    // --- project exclusion ---

    // An entry starting with ":" is a path and matches exactly that project.
    @Test
    fun `a path entry excludes only the project at that path`() {
        val excluded = setOf(":a:core")
        assertTrue(isProjectExcluded(":a:core", "core", excluded))
        assertFalse(isProjectExcluded(":b:core", "core", excluded))
        assertFalse(isProjectExcluded(":a", "a", excluded))
    }

    // A path is never a prefix: excluding :a does not exclude :a:core.
    @Test
    fun `a path entry does not match nested projects`() {
        assertFalse(isProjectExcluded(":a:core", "core", setOf(":a")))
    }

    // Backward compatible: a bare name matches project.name, in every module that has it.
    @Test
    fun `a bare entry matches by project name`() {
        val excluded = setOf("core")
        assertTrue(isProjectExcluded(":a:core", "core", excluded))
        assertTrue(isProjectExcluded(":b:core", "core", excluded))
        assertFalse(isProjectExcluded(":a", "a", excluded))
    }

    // A bare entry never matches by path text, and a path entry never by name.
    @Test
    fun `a path is not compared with the name and a name is not compared with the path`() {
        assertFalse(isProjectExcluded(":core", "other", setOf("core")))
        assertFalse(isProjectExcluded(":x:core", ":a:core", setOf(":a:core")))
    }

    @Test
    fun `a path entry that matches a project produces no warning`() {
        assertEquals(emptyList<String>(), excludeProjectsWarnings(setOf(":a:core"), cores))
    }

    @Test
    fun `a bare entry warns as deprecated and names every project it matched`() {
        val warning = excludeProjectsWarnings(setOf("core"), cores).single()
        assertTrue(warning.contains("deprecated"))
        assertTrue(warning.contains(":a:core") && warning.contains(":b:core"))
    }

    @Test
    fun `an entry matching no project warns`() {
        assertTrue(excludeProjectsWarnings(setOf(":nope"), cores).single().contains("matched no project"))
        assertTrue(excludeProjectsWarnings(setOf("nope"), cores).single().contains("matched no project"))
    }

    @Test
    fun `an empty exclusion set excludes no project`() {
        assertFalse(isProjectExcluded(":a:core", "core", emptySet()))
    }
}
