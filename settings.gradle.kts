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
    plugins {
        id("dev.architectury.loom") version "1.14-SNAPSHOT"
        id("architectury-plugin") version "3.4-SNAPSHOT"
        id("com.gradleup.shadow") version "9.6.1"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
}

stonecutter.create(rootProject) {
    branch("common") { versions("1.18.2") }
    branch("fabric") { versions("1.18.2") }
    branch("forge") { versions("1.18.2") }
}

rootProject.name = "assetbridge"
