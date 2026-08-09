plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val forge_version = project.findProperty("forge_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

loom {
    forge {
        mixinConfig("assetbridge.mixins.json")
    }
}

architectury {
    platformSetupLoomIde()
    forge()
}

configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    getByName("developmentForge").extendsFrom(configurations["common"])
}

configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// Under Stonecutter the artifact-producing project is the common *node*
// (`:common:1.18.2`), not the branch container (`:common`).
val commonProject: Project = project(":common:${stonecutter.current.project}")

// Architectury creates the `transformProduction*` configurations while the common
// node is evaluated, so it must be configured before this script resolves them.
evaluationDependsOn(commonProject.path)

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    "mappings"(loom.officialMojangMappings())

    "forge"("net.minecraftforge:forge:$forge_version")

    "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    "shadowBundle"(project(commonProject.path, "transformProductionForge"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}
