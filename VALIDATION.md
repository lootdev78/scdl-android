# Validation

The project was normalized without building an APK.

Checked conditions:

- no `buildSrc` directory or custom convention plugin
- no test runner, unit-test dependency, instrumentation-test dependency, signing or publishing configuration
- all source packages and Android namespaces use `io.github.lootdev.scdl.*`
- every Android module is restricted to `arm64-v8a`
- app ID is `io.github.lootdev.scdl`
- the launcher activity is the English SCDL terminal GUI
- only standard Gradle repositories are configured
- the requested OpenJDK 21 path and disabled toolchain discovery/download are present
- AGP 8.13.2, Kotlin 2.3.0 and Gradle 8.14.3 are configured
- XML files and embedded ZIP resources pass local integrity checks

A configuration-only `gradlew help` attempt was made, but this environment could not resolve `services.gradle.org`, so the Gradle distribution could not be downloaded. No APK task was run. Native runtime binaries were intentionally not added.
