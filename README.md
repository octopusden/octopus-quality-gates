# octopus-quality-gates

Quality gates for Octopus components: the Gradle convention plugin
`org.octopusden.octopus-quality`, published to Maven Central as
`org.octopusden.octopus:octopus-quality-plugin`.

The plugin wires linters, coverage and publication checks into JVM builds
(Java, Kotlin, Groovy). Checkstyle, PMD, SpotBugs and ErrorProne (default engine
version) are pinned per plugin release; detekt, ktlint, Kover and the Kotlin Gradle
plugin come from the consumer build, at the versions it declares.

The plugin is published to Maven Central only, so add it to the plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

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

## Release

Run the **Gradle Release** workflow (Actions → Gradle Release → Run workflow) on `main`. It runs the
quality gates (detekt, ktlint, Kover) first and releases only if they pass. Inputs:

- `increment-version-level` — `patch` / `minor` / `major`, bumped from the latest `vX.Y.Z` tag;
- `dry-run` — rehearse without publishing, tagging or registering;
- `resume-deployment-id` — only to recover a run that failed after the upload, with the id the
  failed run printed as `RELEASE_PUBLISH_RESUME_DEPLOYMENT_ID`. Do not plainly re-run such a run:
  it would upload the same version twice.

Recovering a run that failed after the upload:

- If another release run is waiting in the `release` queue, cancel it first. It starts as soon as
  the failed run ends, computes the same version (no tag was created) and uploads it again.
- Dispatch the resume with the same `increment-version-level`: the version is recomputed from the
  latest tag, and the Portal refuses a deployment whose version does not match.
- Resume before anything else is merged into `main`. The resume run tags the commit it checks out,
  so a newer `main` would get a tag on a commit that was never built. If `main` has moved, dispatch
  from a branch created at the failed run's "Built commit".

The release publishes to Maven Central through the Central Portal, tags the commit, creates the
GitHub release and registers the version in octopus-release-log, which triggers TeamCity
`[p] octopus-quality-gates` → Release Post Processing.
