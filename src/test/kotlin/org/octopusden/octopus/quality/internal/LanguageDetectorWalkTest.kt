package org.octopusden.octopus.quality.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LanguageDetectorWalkTest {
    @TempDir
    lateinit var tmp: File

    private val none = DetectedLanguages(hasKotlin = false, hasJava = false, hasGroovy = false)

    private fun root(
        name: String,
        vararg files: String,
    ): File {
        val root = File(tmp, name).also { it.mkdirs() }
        files.forEach { File(root, it).also { f -> f.parentFile.mkdirs() }.writeText("") }
        return root
    }

    @Test
    fun `kotlin root stops at the top-level kt without listing the deep subtree`() {
        val r = root("kotlin", "A.kt", "a/b/c/d/Deep.kt", "a/b/c/d/Deep.java")
        val scan = LanguageDetector.walkRoot(r, none)
        assertEquals(DetectedLanguages(hasKotlin = true, hasJava = false, hasGroovy = false), scan.languages)
        assertEquals(1, scan.dirsListed)
    }

    @Test
    fun `groovy root stops at the first groovy file`() {
        val r = root("groovy", "A.groovy", "a/b/Deep.groovy")
        val scan = LanguageDetector.walkRoot(r, none)
        assertEquals(DetectedLanguages(hasKotlin = false, hasJava = false, hasGroovy = true), scan.languages)
        assertEquals(1, scan.dirsListed)
    }

    @Test
    fun `java root keeps walking past java until it also finds kotlin`() {
        val r = root("java", "A.java", "a/B.java", "a/b/C.kt", "a/b/c/d/Deep.java")
        val scan = LanguageDetector.walkRoot(r, none)
        assertEquals(DetectedLanguages(hasKotlin = true, hasJava = true, hasGroovy = false), scan.languages)
        assertEquals(3, scan.dirsListed)
    }

    @Test
    fun `java root stops at the first java file when kotlin is already known`() {
        val r = root("java", "A.java", "a/b/c/Deep.java")
        val known = none.copy(hasKotlin = true)
        val scan = LanguageDetector.walkRoot(r, known)
        assertEquals(DetectedLanguages(hasKotlin = true, hasJava = true, hasGroovy = false), scan.languages)
        assertEquals(1, scan.dirsListed)
    }

    @Test
    fun `a root is skipped entirely when everything it could report is known`() {
        val r = root("kotlin", "A.kt")
        val scan = LanguageDetector.walkRoot(r, none.copy(hasKotlin = true))
        assertEquals(0, scan.dirsListed)
    }

    @Test
    fun `a root with nothing to find is walked completely`() {
        val r = root("kotlin", "a/b/Notes.txt", "c/d.kts")
        val scan = LanguageDetector.walkRoot(r, none)
        assertEquals(none, scan.languages)
        assertEquals(4, scan.dirsListed)
    }
}
