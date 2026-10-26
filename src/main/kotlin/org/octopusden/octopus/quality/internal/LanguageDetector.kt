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
     * resources and generated-code directories are ignored). Each root is walked lazily and
     * abandoned as soon as it can add nothing new (see [walkRoot]): a `kotlin` root at its first
     * `.kt`, a `groovy` root at its first `.groovy`, a `java` root once it has seen both a `.java`
     * and a `.kt`; a root whose languages are all known is not listed. `.kts` build scripts are
     * not `.kt`.
     *
     * Computed once per evaluated [Project] (cached in its extra properties, never statically:
     * the daemon outlives builds), as callers invoke this repeatedly per project. A project that
     * is not evaluated yet (a sibling seen from [detectAll]) is computed without caching, since
     * its sources may still change.
     *
     * The configuration cache records the listings of the directories actually walked, so a
     * change in one of those (not in a subtree the walk never reached) triggers reconfiguration.
     */
    fun detect(project: Project): DetectedLanguages {
        val extra = project.extensions.extraProperties
        if (extra.has(CACHE_KEY)) return extra.get(CACHE_KEY) as DetectedLanguages
        return scan(project).also { if (project.state.executed) extra.set(CACHE_KEY, it) }
    }

    private fun scan(project: Project): DetectedLanguages {
        var found = DetectedLanguages(hasKotlin = false, hasJava = false, hasGroovy = false)
        File(project.projectDir, "src")
            .listFiles { f -> f.isDirectory }
            .orEmpty()
            .flatMap { set -> LANGUAGE_DIRS.map { File(set, it) } }
            .filter { it.isDirectory }
            .forEach { found = walkRoot(it, found).languages }
        return found
    }

    internal data class RootScan(
        val languages: DetectedLanguages,
        val dirsListed: Int,
    )

    /**
     * Walks one language root breadth-first and stops as soon as it can add nothing new.
     * The extensions a root can report: `kotlin` -> `kt`; `groovy` -> `groovy`; `java` -> `java`
     * and `kt` (Kotlin under `src/main/java` is supported). Extensions already in [known] are not
     * looked for, so a root whose extensions are all known is not listed at all.
     */
    internal fun walkRoot(
        root: File,
        known: DetectedLanguages,
    ): RootScan {
        val have = setOfNotNull("kt".takeIf { known.hasKotlin }, "java".takeIf { known.hasJava }, "groovy".takeIf { known.hasGroovy })
        val rootExtensions =
            when (root.name) {
                "kotlin" -> setOf("kt")
                "groovy" -> setOf("groovy")
                else -> setOf("java", "kt")
            }
        val wanted = (rootExtensions - have).toMutableSet()
        val seen = have.toMutableSet()
        var listed = 0
        val pending = ArrayDeque(listOf(root))
        while (wanted.isNotEmpty() && pending.isNotEmpty()) {
            listed++
            for (f in pending.removeFirst().listFiles().orEmpty()) {
                if (f.isDirectory) {
                    pending.addLast(f)
                } else if (wanted.remove(f.extension)) {
                    seen += f.extension
                }
            }
        }
        return RootScan(DetectedLanguages("kt" in seen, "java" in seen, "groovy" in seen), listed)
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
