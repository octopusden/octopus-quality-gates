package org.octopusden.octopus.quality.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverageWiringTest {
    @Test
    fun `a single-module root is its own candidate`() {
        assertEquals(listOf("root"), selectCoverageCandidates("root", emptyList(), rootHasSources = false))
        assertEquals(listOf("root"), selectCoverageCandidates("root", emptyList(), rootHasSources = true))
    }

    @Test
    fun `a source-less aggregator root is not a candidate`() {
        assertEquals(listOf("lib"), selectCoverageCandidates("root", listOf("lib"), rootHasSources = false))
    }

    @Test
    fun `a root with sources is a candidate next to its subprojects`() {
        assertEquals(listOf("lib", "root"), selectCoverageCandidates("root", listOf("lib"), rootHasSources = true))
    }

    @Test
    fun `a candidate root is dropped by excludeProjects by path or name, or by an excluded test`() {
        assertTrue(isRootCoverageDropped(true, setOf(":"), emptySet(), "app"))
        assertTrue(isRootCoverageDropped(true, setOf("app"), emptySet(), "app"))
        assertTrue(isRootCoverageDropped(true, emptySet(), setOf(":test"), "app"))
        assertTrue(isRootCoverageDropped(true, emptySet(), setOf("test"), "app"))
        assertFalse(isRootCoverageDropped(true, emptySet(), emptySet(), "app"))
    }

    @Test
    fun `an aggregator root keeps its merged report even when excluded by path or test`() {
        assertFalse(isRootCoverageDropped(false, setOf(":"), setOf(":test"), "app"))
    }

    @Test
    fun `only report and verify tasks count as coverage tasks`() {
        assertTrue(hasCoverageTasks(setOf("test", "koverVerify")))
        assertTrue(hasCoverageTasks(setOf("jacocoTestReport")))
        assertFalse(hasCoverageTasks(setOf("test", "detekt")))
    }

    @Test
    fun `the warning says the floor is not enforced and suggests excludeProjects`() {
        val warning = coverageNotEnforcedWarning(":lib")
        assertTrue(warning.contains("NOT enforced by qualityCoverage"))
        assertTrue(warning.contains("""excludeProjects(":lib")"""))
        assertFalse(warning.contains("additionalTestTasks"))
    }
}
