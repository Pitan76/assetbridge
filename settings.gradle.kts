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
// and gaining a `versions/` directory. The root branch is left without versions
// so no node is created for it.
stonecutter.create(rootProject) {
    branch("common") { versions("1.18.2") }
    branch("fabric") { versions("1.18.2") }
    branch("forge") { versions("1.18.2") }
}

rootProject.name = "assetbridge"
