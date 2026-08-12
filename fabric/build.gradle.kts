import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("architectury-plugin")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
}

// MC 26.1+ ships unobfuscated: no mappings exist, so the regular Loom -- which refuses to
// prepare without them -- cannot be used. `loom-no-remap` is the same jar with remapping
// removed; the controller declares both markers so only the choice is made here.
//
// Loom therefore cannot go in the `plugins {}` block above (that block takes no conditionals),
// which costs us the `loom { }` and `tasks.remapJar` type-safe accessors. They are replaced by
// `the<LoomGradleExtensionAPI>()` and `tasks.named(...)` below.
val unobfuscated = project.name.substringBefore('.').toInt() >= 26
val isLegacy = project.name == "1.12.2"
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom")

val loomApi = the<LoomGradleExtensionAPI>()

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

loomApi.runs {
    create("gametest") {
        server()
        name("Game Test")
        source(sourceSets.getByName("gametest"))
        vmArg("-Dfabric-api.gametest")
        vmArg("-Dfabric-api.gametest.report-file=${project.layout.buildDirectory.get().asFile}/gametest-report.xml")
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

val commonProject: Project = project(":common:${stonecutter.current.project}")

evaluationDependsOn(commonProject.path)

if (unobfuscated) {
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

if (isLegacy) {
    loomApi.intermediaryUrl.set(
        "https://maven.legacyfabric.net/net/legacyfabric/intermediary/%1\$s/intermediary-%1\$s-v2.jar"
    )
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")

    val yarn_mappings = project.findProperty("yarn_mappings") as String
    "mappings"("net.legacyfabric:yarn:$yarn_mappings:v2")

    val modImplementation = if (unobfuscated) "implementation" else "modImplementation"
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")


    modImplementation("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:$fabric_api_version")

    if (unobfuscated) {
        compileOnly("org.spongepowered:mixin:0.8.5")
        "common"(project(commonProject.path)) { isTransitive = false }
    } else {
        "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    }
    "shadowBundle"(project(commonProject.path, "transformProductionFabric"))
}

tasks.processResources {
    val mcDep = project.findProperty("mc_dep") as String

    inputs.property("version", project.version)
    inputs.property("mc_dep", mcDep)
    inputs.property("fabric_api_id", "fabric")

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mc_dep" to mcDep,
            "fabric_api_id" to "fabric",
        )
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}

val publishedJar: TaskProvider<out AbstractArchiveTask> =
    if (unobfuscated) tasks.jar else tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar")

publishMods {
    val mcVersion = project.name

    @Suppress("UNCHECKED_CAST")
    val getCompatibleMcVersions = rootProject.extra.get("getCompatibleMcVersions") as (Project) -> List<String>
    val mcVersions = getCompatibleMcVersions(project)
    
    file.set(publishedJar.flatMap { it.archiveFile })
    displayName.set(publishedJar.flatMap { it.archiveFile.map { f -> f.asFile.name } })
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (Fabric)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("fabric")

    val curseforgeId = project.findProperty("curseforge_project_id")?.toString()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").orNull ?: project.findProperty("curseforge_token")?.toString()
    if (!curseforgeId.isNullOrEmpty() && !curseforgeToken.isNullOrEmpty()) {
        curseforge {
            projectId.set(curseforgeId)
            accessToken.set(curseforgeToken)
            minecraftVersions.addAll(mcVersions)
            client.set(true)
            server.set(true)

            requires("legacy-fabric-api")
        }
    }

    val modrinthId = project.findProperty("modrinth_project_id")?.toString()
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").orNull ?: project.findProperty("modrinth_token")?.toString()
    if (!modrinthId.isNullOrEmpty() && !modrinthToken.isNullOrEmpty()) {
        modrinth {
            projectId.set(modrinthId)
            accessToken.set(modrinthToken)
            minecraftVersions.addAll(mcVersions)

            requires("legacy-fabric-api")
        }
    }
}

