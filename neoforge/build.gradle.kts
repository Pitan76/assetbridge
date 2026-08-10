plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
}

val neoforge_version = project.findProperty("neoforge_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

architectury {
    platformSetupLoomIde()
    neoForge()
}

configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    getByName("developmentNeoForge").extendsFrom(configurations["common"])
}

configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// Under Stonecutter the artifact-producing project is the common *node*
// (`:common:1.21.1`), not the branch container (`:common`).
val commonProject: Project = project(":common:${stonecutter.current.project}")

// Architectury creates the `transformProduction*` configurations while the common
// node is evaluated, so it must be configured before this script resolves them.
evaluationDependsOn(commonProject.path)

repositories {
    maven("https://maven.neoforged.net/releases")
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    "mappings"(loom.officialMojangMappings())

    "neoForge"("net.neoforged:neoforge:$neoforge_version")

    "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    "shadowBundle"(project(commonProject.path, "transformProductionNeoForge"))
}

tasks.processResources {
    val mcDep = project.findProperty("mc_dep") as String
    val neoforgeLoaderDep = project.findProperty("neoforge_loader_dep") as String
    val javafmlDep = project.findProperty("javafml_dep") as String

    inputs.property("version", project.version)
    inputs.property("mc_dep", mcDep)
    inputs.property("neoforge_loader_dep", neoforgeLoaderDep)
    inputs.property("javafml_dep", javafmlDep)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "version" to project.version,
            "mc_dep" to mcDep,
            "neoforge_loader_dep" to neoforgeLoaderDep,
            "javafml_dep" to javafmlDep,
        )
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}

publishMods {
    val mcVersion = project.name
    file.set(tasks.remapJar.flatMap { it.archiveFile })
    displayName.set(tasks.remapJar.flatMap { it.archiveFile.map { it.asFile.name } })
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (NeoForge)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("neoforge")

    val curseforgeId = project.findProperty("curseforge_project_id")?.toString()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").orNull ?: project.findProperty("curseforge_token")?.toString()
    if (!curseforgeId.isNullOrEmpty() && !curseforgeToken.isNullOrEmpty()) {
        curseforge {
            projectId.set(curseforgeId)
            accessToken.set(curseforgeToken)
            minecraftVersions.add(mcVersion)
            client.set(true)
            server.set(true)
        }
    }

    val modrinthId = project.findProperty("modrinth_project_id")?.toString()
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").orNull ?: project.findProperty("modrinth_token")?.toString()
    if (!modrinthId.isNullOrEmpty() && !modrinthToken.isNullOrEmpty()) {
        modrinth {
            projectId.set(modrinthId)
            accessToken.set(modrinthToken)
            minecraftVersions.add(mcVersion)
        }
    }
}
