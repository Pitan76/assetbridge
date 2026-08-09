// Stonecutter controller. This is the root project's build script; `build.gradle.kts`
// in each branch is the central script shared by that branch's nodes.
//
// Plugin versions MUST be declared here rather than in a branch script: the nodes
// inherit this buildscript classpath, and declaring them in two places loads Loom
// under two classloaders (ClassCastException on LoomGradleExtension).
plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.14-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}
stonecutter active "1.18.2" /* [SC] DO NOT EDIT */

val maven_group = project.findProperty("maven_group") as String
val mod_version = project.findProperty("mod_version") as String
val archives_name = project.findProperty("archives_name") as String

allprojects {
    group = maven_group
    version = mod_version
}

subprojects {
    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin("java") {
        // A node's `project.name` is the version directory, so it is identical across
        // branches (`:common:1.18.2`, `:fabric:1.18.2`, ...). Naming the archive after
        // it would give every node the same group:name:version, and Gradle would then
        // substitute one project dependency for another. Qualify by branch instead.
        val branchName = project.parent?.name ?: project.name
        configure<BasePluginExtension> {
            archivesName.set("$archives_name-$branchName")
        }

        // Every node is named after its version directory, so `group:name:version` is
        // identical across branches and Gradle treats the nodes as the same component.
        // Qualify the group by branch to keep them distinct.
        group = "$maven_group.$branchName"

        // Keep the Minecraft version in the artifact version so jars from different
        // nodes never overwrite each other.
        version = "$mod_version+${project.name}"

        configure<JavaPluginExtension> {
            withSourcesJar()
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(17)
        }
    }
}

// Builds every registered version. The 0.6-era `registerChiseled`/`chiseled` API is
// gone in 0.9.x; task aggregation replaces it.
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every registered version."
    dependsOn(stonecutter.tasks.named("build"))
}

// Serialise `build` across nodes. Stonecutter documents task ordering as a way to
// keep concurrency-sensitive tasks from running in parallel.
stonecutter tasks {
    order("build")
}
