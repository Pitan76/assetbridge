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
}

// Each existing subproject becomes a Stonecutter branch, keeping its own `src/`
// and gaining a `versions/` directory. The root branch gets no versions, so no
// node is created for it. Plugin versions live in `stonecutter.gradle.kts`.
stonecutter.create(rootProject) {
    branch("common") { versions("1.18.2", "1.19.2", "1.20.1", "1.21.1") }
    branch("fabric") { versions("1.18.2", "1.19.2", "1.20.1", "1.21.1") }
    // Forge stops at 1.20.1; 1.21.1 is NeoForge territory.
    branch("forge") { versions("1.18.2", "1.19.2", "1.20.1") }
    branch("neoforge") { versions("1.21.1") }
    vcsVersion = "1.18.2"
}

rootProject.name = "assetbridge"
