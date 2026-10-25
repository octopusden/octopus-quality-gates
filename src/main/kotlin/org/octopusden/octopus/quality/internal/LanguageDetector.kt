package org.octopusden.octopus.quality.internal

import org.gradle.api.Project
import java.io.File

internal data class DetectedLanguages(
    val hasKotlin: Boolean,
    val hasJava: Boolean,
    val hasGroovy: Boolean,
) {
    val isKotlinOnly get() = hasKotlin && !hasJava && !hasGroovy
}

internal object LanguageDetector {
    /**
     * A language is present when a file with its extension exists under `src/` (any source set,
     * any directory name, so Kotlin under `src/main/java` is Kotlin). Stops at the first file of
     * each language; `.kts` build scripts are not `.kt`.
     */
    fun detect(project: Project): DetectedLanguages {
        var hasKotlin = false
        var hasJava = false
        var hasGroovy = false
        val buildDir =
            project.layout.buildDirectory.asFile
                .get()

        File(project.projectDir, "src")
            .walkTopDown()
            .onEnter { it != buildDir }
            .filter { it.isFile }
            .forEach {
                when (it.extension) {
                    "kt" -> hasKotlin = true
                    "java" -> hasJava = true
                    "groovy" -> hasGroovy = true
                }
                if (hasKotlin && hasJava && hasGroovy) return DetectedLanguages(true, true, true)
            }

        return DetectedLanguages(hasKotlin, hasJava, hasGroovy)
    }

    /** True if the project carries any JVM source (kotlin/java/groovy) of its own. */
    fun hasAnySource(project: Project): Boolean = detect(project).let { it.hasKotlin || it.hasJava || it.hasGroovy }

    /**
     * Detect languages across all subprojects (or the root project if single-module).
     */
    fun detectAll(
        rootProject: Project,
        excludedProjects: Set<String>,
    ): DetectedLanguages {
        val projects =
            if (rootProject.subprojects.isEmpty()) {
                listOf(rootProject)
            } else {
                // allprojects (minus root) supports nested multi-level builds (e.g. :platform:api)
                rootProject.allprojects.filter { it != rootProject && it.name !in excludedProjects }
            }

        var hasKotlin = false
        var hasJava = false
        var hasGroovy = false

        for (project in projects) {
            val lang = detect(project)
            if (lang.hasKotlin) hasKotlin = true
            if (lang.hasJava) hasJava = true
            if (lang.hasGroovy) hasGroovy = true
        }

        return DetectedLanguages(hasKotlin, hasJava, hasGroovy)
    }
}
