# octopus-quality-gates

Quality gates for Octopus components: the Gradle convention plugin
`org.octopusden.octopus-quality`, published to Maven Central as
`org.octopusden.octopus:octopus-quality-plugin`.

The plugin wires linters, coverage and publication checks into JVM builds
(Java, Kotlin, Groovy) with versions pinned per plugin release.

```kotlin
plugins {
    id("org.octopusden.octopus-quality") version "<version>"
}
```

## History

Until 3.2.0 the plugin lived in [octopus-base](https://github.com/octopusden/octopus-base)
under `gradle-quality-plugin/` and was released together with it. Its history was
carried over here; `octopusden/octopus-base#NNN` in commit messages refers to pull
requests in that repository.

Consumer wiring, tool versions and report paths are still documented in octopus-base:

- [Octopus JVM Style Guidelines](https://github.com/octopusden/octopus-base/blob/main/docs/Octopus%20JVM%20Style%20Guidelines.md)
- [Octopus Quality Plugin CI Reference](https://github.com/octopusden/octopus-base/blob/main/docs/Octopus%20Quality%20Plugin%20CI%20Reference.md)

## Build

```shell
./gradlew build                                              # compile + tests
./gradlew detekt ktlintCheck koverXmlReport koverVerify      # the plugin's own quality gates
```

JDK 17 to run Gradle; the plugin targets Java 11 bytecode.
