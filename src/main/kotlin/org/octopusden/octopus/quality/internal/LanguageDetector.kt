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
    private const val CACHE_KEY = "octopusQuality.detectedLanguages"

    private val LANGUAGE_DIRS = setOf("kotlin", "java", "groovy")

    /**
     * A language is present when a file with its extension exists under a language root
     * `src/<anySourceSet>/{kotlin,java,groovy}` (so Kotlin under `src/main/java` is Kotlin, while
     * resources and generated-code directories are ignored). Stops at the first file of each
     * language; `.kts` build scripts are not `.kt`.
     *
     * Computed once per evaluated [Project] (cached in its extra properties, never statically:
     * the daemon outlives builds), as callers invoke this repeatedly per project. A project that
     * is not evaluated yet (a sibling seen from [detectAll]) is computed without caching, since
     * its sources may still change.
     *
     * The configuration cache records the listings of the language roots, so adding or removing
     * source files there triggers reconfiguration.
     */
    fun detect(project: Project): DetectedLanguages {
        val extra = project.extensions.extraProperties
        if (extra.has(CACHE_KEY)) return extra.get(CACHE_KEY) as DetectedLanguages
        return scan(project).also { if (project.state.executed) extra.set(CACHE_KEY, it) }
    }

    private fun scan(project: Project): DetectedLanguages {
        var hasKotlin = false
        var hasJava = false
        var hasGroovy = false

        File(project.projectDir, "src")
            .listFiles { f -> f.isDirectory }
            .orEmpty()
            .flatMap { set -> LANGUAGE_DIRS.map { File(set, it) } }
            .filter { it.isDirectory }
            .forEach { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile }
                    .forEach {
                        when (it.extension) {
                            "kt" -> hasKotlin = true
                            "java" -> hasJava = true
                            "groovy" -> hasGroovy = true
                        }
                        if (hasKotlin && hasJava && hasGroovy) return DetectedLanguages(true, true, true)
                    }
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
