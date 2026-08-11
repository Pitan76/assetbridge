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
// This branch spans both worlds (1.21.1 is obfuscated, 26.1.2 is not), so every remap-dependent
// step below has to stay conditional.
val unobfuscated = project.name.substringBefore('.').toInt() >= 26
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom")

val loomApi = the<LoomGradleExtensionAPI>()

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
    if (unobfuscated) {
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

    "neoForge"("net.neoforged:neoforge:$neoforge_version")

    if (unobfuscated) {
        compileOnly("org.spongepowered:mixin:0.8.5")
        // `namedElements` is a remapped-names artefact, so loom-no-remap does not publish it.
        "common"(project(commonProject.path)) { isTransitive = false }
    } else {
        "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    }
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

// Without remapping there is no `remapJar` for shadowJar to feed, so the bundle has to be
// folded into `jar`, which then becomes the publishable artifact. Making shadowJar primary
// instead would need the protected `mainSpec.sourcePaths`, which Kotlin DSL cannot reach.
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

// 1.21.1 must publish the *remapped* jar -- publishing an unremapped one there would ship a mod
// that cannot load. Only 26.1+, which has no remap step, publishes `jar`.
val publishedJar: TaskProvider<out AbstractArchiveTask> =
    if (unobfuscated) tasks.jar else tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar")

publishMods {
    val mcVersion = project.name
    
    @Suppress("UNCHECKED_CAST")
    val getCompatibleMcVersions = rootProject.extra.get("getCompatibleMcVersions") as (Project) -> List<String>
    val mcVersions = getCompatibleMcVersions(project)

    file.set(publishedJar.flatMap { it.archiveFile })
    displayName.set(publishedJar.flatMap { it.archiveFile.map { f -> f.asFile.name } })
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (NeoForge)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("neoforge")

    val curseforgeId = project.findProperty("curseforge_project_id")?.toString()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").orNull ?: project.findProperty("curseforge_token")?.toString()
    if (!curseforgeId.isNullOrEmpty() && !curseforgeToken.isNullOrEmpty()) {
        curseforge {
            projectId.set(curseforgeId)
            accessToken.set(curseforgeToken)
            minecraftVersions.addAll(mcVersions)
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
            minecraftVersions.addAll(mcVersions)
        }
    }
}
