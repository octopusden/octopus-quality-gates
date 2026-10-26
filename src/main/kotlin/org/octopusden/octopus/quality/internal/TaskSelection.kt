package org.octopusden.octopus.quality.internal

import org.gradle.api.Project
import org.octopusden.octopus.quality.CoverageExtension

/**
 * Fully qualified path of [taskName] in the project at [projectPath].
 *
 * The root project's path is already `":"`, so naive interpolation would yield `"::test"` and
 * never match an exclusion a consumer wrote as `":test"`.
 */
internal fun qualifiedTaskPath(
    projectPath: String,
    taskName: String,
): String = if (projectPath == ":") ":$taskName" else "$projectPath:$taskName"

/**
 * True when [taskName] in the project at [projectPath] is excluded, by bare name or by fully
 * qualified path.
 *
 * A bare name excludes that task in every module; the qualified form excludes it in one.
 */
internal fun isTaskExcluded(
    projectPath: String,
    taskName: String,
    excludedTasks: Set<String>,
): Boolean = taskName in excludedTasks || qualifiedTaskPath(projectPath, taskName) in excludedTasks

/**
 * True when [taskName] in the project at [projectPath] is a suite whose coverage the aggregated
 * JaCoCo report counts: the standard `test`, plus anything declared in [additionalTestTasks],
 * minus anything in [excludedTasks]. Exclusion wins over declaration.
 *
 * Extra suites are opt-in rather than automatic, following `SubprojectConfigurer.configureJaCoCo`,
 * which scopes its wiring to the standard `test` / `jacocoTestReport` /
 * `jacocoTestCoverageVerification` triplet instead of coupling every `Test` task to every report
 * task. With nothing declared this selects exactly what the aggregate selected before the property
 * existed, so adding it moves no existing consumer.
 */
internal fun isCoverageSuite(
    projectPath: String,
    taskName: String,
    excludedTasks: Set<String>,
    additionalTestTasks: Set<String>,
): Boolean =
    (taskName == "test" || taskName in additionalTestTasks) &&
        !isTaskExcluded(projectPath, taskName, excludedTasks)

/**
 * True when the module at [projectPath] has its standard `test` task excluded.
 *
 * The module's coverage report and verification depend on `test`, so wiring them into an aggregate
 * would pull the excluded suite back into the graph; such a module's coverage is not wired at all.
 */
internal fun isCoverageSkippedByExcludedTest(
    projectPath: String,
    excludedTasks: Set<String>,
): Boolean = isTaskExcluded(projectPath, "test", excludedTasks)

private val COVERAGE_TASK_NAMES =
    setOf(
        "jacocoTestReport",
        "jacocoTestCoverageVerification",
        "koverXmlReport",
        "koverVerify",
        "koverMergedXmlReport",
        "koverMergedVerify",
    )

/**
 * Warns, for each of [skipped] that actually has a coverage report/verify task, that its coverage
 * floor is not enforced by `qualityCoverage`. No-op when coverage is not [enabled].
 */
internal fun warnCoverageNotEnforced(
    skipped: List<Project>,
    enabled: Boolean,
) {
    if (!enabled) return
    for (project in skipped.filter { p -> p.tasks.names.any { it in COVERAGE_TASK_NAMES } }) {
        project.logger.warn(
            "octopusQuality: '${project.path}' has its test task excluded, so its coverage floor is " +
                "NOT enforced by qualityCoverage. To make that explicit, use excludeProjects(\"${project.path}\").",
        )
    }
}

/**
 * Warns once that the root's merged Kover report/floor is not checked by `qualityCoverage` while
 * the `test` tasks of [skipped] are excluded. No-op unless coverage runs with Kover ([tool], null
 * when disabled) in a multi-module build, or when nothing is skipped.
 */
internal fun warnMergedCoverageNotChecked(
    root: Project,
    skipped: List<Project>,
    tool: CoverageExtension.Tool?,
) {
    if (tool != CoverageExtension.Tool.KOVER || skipped.isEmpty() || root.subprojects.isEmpty()) return
    root.logger.warn(
        "octopusQuality: the merged coverage report and floor are NOT checked by qualityCoverage while " +
            "test tasks are excluded (${skipped.joinToString { qualifiedTaskPath(it.path, "test") }}).",
    )
}
