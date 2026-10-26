package org.octopusden.octopus.quality.internal

import org.gradle.api.Project

private val COVERAGE_TASK_NAMES =
    setOf(
        "jacocoTestReport",
        "jacocoTestCoverageVerification",
        "koverXmlReport",
        "koverVerify",
        "koverMergedXmlReport",
        "koverMergedVerify",
    )

/** True when [taskNames] contain a coverage report or verification task. */
internal fun hasCoverageTasks(taskNames: Set<String>): Boolean = taskNames.any { it in COVERAGE_TASK_NAMES }

internal fun coverageNotEnforcedWarning(projectPath: String): String =
    "octopusQuality: '$projectPath' has its test task excluded, so its coverage floor is " +
        "NOT enforced by qualityCoverage. To make that explicit, use excludeProjects(\"$projectPath\")."

/**
 * Warns, for each of [skipped] that actually has a coverage report/verify task, that its coverage
 * floor is not enforced by `qualityCoverage`. No-op when coverage is not [enabled].
 */
internal fun warnCoverageNotEnforced(
    skipped: List<Project>,
    enabled: Boolean,
) {
    if (!enabled) return
    skipped
        .filter { hasCoverageTasks(it.tasks.names) }
        .forEach { it.logger.warn(coverageNotEnforcedWarning(it.path)) }
}

/**
 * Projects whose coverage `qualityCoverage` considers, before any exclusion: every subproject, the
 * root when it carries sources, or the root alone in a single-module build. A source-less aggregator
 * root is not a candidate.
 */
internal fun <T> selectCoverageCandidates(
    root: T,
    subs: List<T>,
    rootHasSources: Boolean,
): List<T> =
    when {
        subs.isEmpty() -> listOf(root)
        rootHasSources -> subs + root
        else -> subs
    }

internal fun coverageCandidates(rootProject: Project): List<Project> =
    selectCoverageCandidates(
        rootProject,
        rootProject.allprojects.filter { it != rootProject },
        LanguageDetector.hasAnySource(rootProject),
    )

/**
 * True when the root is a coverage candidate ([rootIsCandidate]) whose coverage is dropped, by
 * `excludeProjects` or by an excluded `test`: its own Kover report/verify must then not be wired
 * into `qualityCoverage`. A source-less aggregator root is no candidate, so its merged report
 * stays wired.
 */
internal fun isRootCoverageDropped(
    rootIsCandidate: Boolean,
    excludedProjects: Set<String>,
    excludedTasks: Set<String>,
    rootName: String,
): Boolean =
    rootIsCandidate &&
        (
            isProjectExcluded(":", rootName, excludedProjects) ||
                isCoverageSkippedByExcludedTest(":", excludedTasks)
        )

/**
 * True when the root's merged (or own) Kover report/verify must stay out of `qualityCoverage`:
 * the root's own coverage is dropped ([isRootCoverageDropped]), or some coverage target is skipped
 * for an excluded `test` ([anyTargetSkipped]) and the merge would pull that suite back in. The two
 * conditions are independent, so a source-less aggregator root that is not a candidate is still
 * unwired by an excluded module test.
 */
internal fun isRootKoverUnwired(
    anyTargetSkipped: Boolean,
    rootIsCandidate: Boolean,
    excludedProjects: Set<String>,
    excludedTasks: Set<String>,
    rootName: String,
): Boolean = anyTargetSkipped || isRootCoverageDropped(rootIsCandidate, excludedProjects, excludedTasks, rootName)
