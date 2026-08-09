plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
}

val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val fabric_api_version = project.findProperty("fabric_api_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

fun createTransformerDebugLog() {
    val logFile = file("run/.architectury-transformer/debug.log")
    if (!logFile.exists()) {
        logFile.parentFile.mkdirs()
        logFile.createNewFile()
    }
}

createTransformerDebugLog()

tasks.configureEach {
    if (name == "runClient" || name == "runServer" || name.contains("run")) {
        doFirst {
            createTransformerDebugLog()
        }
    }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

sourceSets.create("gametest") {
    val main = sourceSets.main.get()
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}

loom {
    runs {
        create("gametest") {
            server()
            name("Game Test")
            source(sourceSets.getByName("gametest"))
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${project.layout.buildDirectory.get().asFile}/gametest-report.xml")
        }
    }
}

configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    getByName("developmentFabric").extendsFrom(configurations["common"])
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

    "modImplementation"("net.fabricmc:fabric-loader:$fabric_loader_version")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    "shadowBundle"(project(commonProject.path, "transformProductionFabric"))
}

tasks.processResources {
    // Dependency ranges come from the node's gradle.properties, so adding a version
    // means adding a property rather than editing the manifest.
    val mcDep = project.findProperty("mc_dep") as String

    inputs.property("version", project.version)
    inputs.property("mc_dep", mcDep)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "mc_dep" to mcDep)
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
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (Fabric)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("fabric")

    val curseforgeId = project.findProperty("curseforge_project_id")?.toString()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").orNull ?: project.findProperty("curseforge_token")?.toString()
    if (!curseforgeId.isNullOrEmpty() && !curseforgeToken.isNullOrEmpty()) {
        curseforge {
            projectId.set(curseforgeId)
            accessToken.set(curseforgeToken)
            minecraftVersions.add(mcVersion)
            client.set(true)
            server.set(true)
            requires("fabric-api")
        }
    }

    val modrinthId = project.findProperty("modrinth_project_id")?.toString()
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").orNull ?: project.findProperty("modrinth_token")?.toString()
    if (!modrinthId.isNullOrEmpty() && !modrinthToken.isNullOrEmpty()) {
        modrinth {
            projectId.set(modrinthId)
            accessToken.set(modrinthToken)
            minecraftVersions.add(mcVersion)
            requires("fabric-api")
        }
    }
}
