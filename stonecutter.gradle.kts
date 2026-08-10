// Stonecutter controller. This is the root project's build script; `build.gradle.kts`
// in each branch is the central script shared by that branch's nodes.
//
// Plugin versions MUST be declared here rather than in a branch script: the nodes
// inherit this buildscript classpath, and declaring them in two places loads Loom
// under two classloaders (ClassCastException on LoomGradleExtension).
plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17.491" apply false
    // MC 26.1+ is unobfuscated, so no mappings exist and the regular Loom -- which requires
    // them -- cannot prepare a node. `loom-no-remap` is the variant with remapping removed.
    //
    // Both IDs are marker artifacts for the *same* `dev.architectury:architectury-loom` jar at
    // the same version, so declaring both here puts exactly one copy of Loom on the shared
    // buildscript classpath. That is what makes the per-node choice below safe: it selects
    // which plugin to apply, never which classes to load. Keep the two versions identical.
    id("dev.architectury.loom-no-remap") version "1.17.491" apply false
    id("architectury-plugin") version "3.5.169" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}
stonecutter active "1.18.2" /* [SC] DO NOT EDIT */

// 26.1 renamed vanilla types that this mod touches in ~85 places. These are pure identifier
// swaps with no behavioural difference, so a string replacement keeps a single source shape;
// wrapping every use in `//? if` blocks would bury the code in preprocessor comments.
//
// The source is written with the pre-26 names and rewritten when a node at 26 or above is
// active. Both renames stay inside their original package, so the import lines migrate too.
// Verified before adding these: the old names never appear as part of a longer identifier, so
// a plain textual replacement cannot hit anything unintended.
//
// Renames that change more than the name (MetadataSectionType is a record where the old
// serializer was an interface) still need a `//? if` branch at the call site.
stonecutter parameters {
    replacements {
        string {
            direction = eval(current.version, ">=26")
            replace("ResourceLocation", "Identifier")
        }
        string {
            direction = eval(current.version, ">=26")
            replace("MetadataSectionSerializer", "MetadataSectionType")
        }
        // Fabric API moved its creative tab helpers out of `itemgroup.v1` and renamed them. The
        // shapes are unchanged -- `builder()` still returns CreativeModeTab.Builder, and the new
        // output type implements CreativeModeTab.Output, so the existing `accept` method reference
        // still resolves -- which keeps these to plain renames.
        string {
            direction = eval(current.version, ">=26")
            replace(
                "net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup",
                "net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab",
            )
        }
        string {
            direction = eval(current.version, ">=26")
            replace(
                "net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent",
                "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent",
            )
        }
    }
}

// A node is named after its Minecraft version, which decides the Java level:
// Minecraft moved to 21 in 1.20.5, and to 25 in the 26.x scheme.
fun javaFor(nodeName: String): JavaVersion {
    val parts = nodeName.split('.').mapNotNull { it.toIntOrNull() }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    // 26.x dropped the leading `1.`, so a major of 26 or more is the new scheme.
    if (major >= 26) return JavaVersion.VERSION_25
    val atLeast1205 = minor > 20 || (minor == 20 && patch >= 5)
    return if (atLeast1205) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
}

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
            sourceCompatibility = javaFor(project.name)
            targetCompatibility = javaFor(project.name)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaFor(project.name).majorVersion.toInt())
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
