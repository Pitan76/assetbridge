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

// Under Stonecutter the artifact-producing project is the common *node*
// (`:common:1.18.2`), not the branch container (`:common`).
val commonProject: Project = project(":common:${stonecutter.current.project}")

// Architectury creates the `transformProduction*` configurations while the common
// node is evaluated, so it must be configured before this script resolves them.
evaluationDependsOn(commonProject.path)

if (unobfuscated) {
    repositories {
        // Mixin is no longer pulled in through a remapped loader dependency.
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    // No mappings exist for 26.1+, and loom-no-remap does not ask for any.
    if (!unobfuscated) {
        "mappings"(loomApi.officialMojangMappings())
    }

    // loom-no-remap drops the `mod` prefix on the dependency configurations: there is no
    // remapping step for them to feed.
    val modImplementation = if (unobfuscated) "implementation" else "modImplementation"
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    if (unobfuscated) {
        compileOnly("org.spongepowered:mixin:0.8.5")
        // `namedElements` is a remapped-names artefact, so loom-no-remap does not publish it.
        "common"(project(commonProject.path)) { isTransitive = false }
    } else {
        "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    }
    "shadowBundle"(project(commonProject.path, "transformProductionFabric"))
}

tasks.processResources {
    // Dependency ranges come from the node's gradle.properties, so adding a version
    // means adding a property rather than editing the manifest.
    val mcDep = project.findProperty("mc_dep") as String
    val fabricApiId = if (project.name == "1.16.5") "fabric" else "fabric-api"

    inputs.property("version", project.version)
    inputs.property("mc_dep", mcDep)
    inputs.property("fabric_api_id", fabricApiId)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mc_dep" to mcDep,
            "fabric_api_id" to fabricApiId,
        )
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

// Without remapping there is no `remapJar` for shadowJar to feed, so the bundle has to be
// folded into `jar`, which then becomes the publishable artifact.
//
// The alternative -- making shadowJar the primary artifact -- needs `mainSpec.sourcePaths` to
// suppress its source-set inclusion, and that member is protected: reachable from Groovy's
// dynamic dispatch but not from Kotlin DSL. Folding into `jar` needs no such access, and it
// also keeps Loom's own post-processing of `jar` (Jar-in-Jar metadata in fabric.mod.json) last,
// which is what must not be overwritten.
if (unobfuscated) {
    tasks.jar {
        dependsOn(tasks.shadowJar)
        // This project's own output is added first and therefore wins; the bundle only fills in
        // the common classes.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(zipTree(tasks.shadowJar.flatMap { it.archiveFile }))
    }
} else {
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    }
}

// The obfuscated nodes publish the remapped jar; 26.1+ has no remap step and publishes `jar`.
// Publishing an unremapped jar for the obfuscated nodes would ship a mod that cannot load, so
// this must stay conditional.
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
            requires("fabric-api")
        }
    }

    val modrinthId = project.findProperty("modrinth_project_id")?.toString()
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").orNull ?: project.findProperty("modrinth_token")?.toString()
    if (!modrinthId.isNullOrEmpty() && !modrinthToken.isNullOrEmpty()) {
        modrinth {
            projectId.set(modrinthId)
            accessToken.set(modrinthToken)
            minecraftVersions.addAll(mcVersions)
            requires("fabric-api")
        }
    }
}

