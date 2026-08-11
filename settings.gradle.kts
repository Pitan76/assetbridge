pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://files.minecraftforge.net/maven/") }
        maven {
            name = "KikuGie Snapshots"
            url = uri("https://maven.kikugie.dev/snapshots")
        }
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Loom refuses to set up Minecraft 26.1.2 on anything below Java 25, so the daemon itself
    // must run on 25 (see gradle/gradle-daemon-jvm.properties). This resolver lets Gradle
    // download that JDK instead of requiring every machine and CI runner to preinstall it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Each existing subproject becomes a Stonecutter branch, keeping its own `src/`
// and gaining a `versions/` directory. The root branch gets no versions, so no
// node is created for it. Plugin versions live in `stonecutter.gradle.kts`.
// The version list is unconditional on purpose. An earlier attempt keyed it off
// `gradle.startParameter.taskNames` so that 26.1.2 replaced the others; the effect was that
// `chiseledBuild` -- whose task name never mentions a version -- silently built everything
// *except* 26.1.2, dropping it from CI and releases. See memo/MC-26.1.2.md.
stonecutter.create(rootProject) {
    branch("common") { versions("1.16.5", "1.18.2", "1.19.2", "1.20.1", "1.21.1", "26.1.2", "26.2") }
    branch("fabric") { versions("1.16.5", "1.18.2", "1.19.2", "1.20.1", "1.21.1", "26.1.2", "26.2") }
    // Forge stops at 1.20.1; 1.21.1 onwards is NeoForge territory.
    branch("forge") { versions("1.16.5", "1.18.2", "1.19.2", "1.20.1") }
    branch("neoforge") { versions("1.21.1", "26.1.2", "26.2") }
    vcsVersion = "1.18.2"
}

rootProject.name = "assetbridge"
