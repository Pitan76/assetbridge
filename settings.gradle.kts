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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter.create(rootProject) {
    branch("common") { versions("1.12.2") }
    branch("fabric") { versions("1.12.2") }
    branch("forge") { versions("1.12.2") }
    vcsVersion = "1.12.2"
}

rootProject.name = "assetbridge"
