plugins {
    id("xyz.wagyourtail.unimined")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
    java
}

val minecraft_version = project.findProperty("minecraft_version") as String
val forge_version     = project.findProperty("forge_version")     as String

val commonProject: Project = project(":common:${stonecutter.current.project}")
evaluationDependsOn(commonProject.path)

unimined.minecraft {
    version(minecraft_version)

    minecraftForge {
        loader(forge_version.substringAfter("-"))   // "14.23.5.2859"
        mixinConfig("assetbridge.mixins.json")
    }

    mappings {
        searge()
        mcp("stable", "39-1.12")
    }
}

// Common shared code linked in via a separate configuration so Unimined
// can remap it alongside the Forge classpath.
configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
}

configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    "common"(project(commonProject.path)) { isTransitive = false }
    "shadowBundle"(project(commonProject.path))
}

tasks.processResources {
    val mcDep          = project.findProperty("mc_dep")           as String
    val forgeLoaderDep = project.findProperty("forge_loader_dep") as String

    inputs.property("version", project.version)
    inputs.property("mc_dep", mcDep)
    inputs.property("forge_loader_dep", forgeLoaderDep)

    filesMatching("META-INF/mods.toml") {
        expand(
            "version"          to project.version,
            "mc_dep"           to mcDep,
            "forge_loader_dep" to forgeLoaderDep,
        )
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

// The plain `jar`/`remapJar` output never contains common's classes (they're only pulled
// in via "shadowBundle", scoped to shadowJar). Remap shadowJar itself so the published
// artifact is both obfuscated (searge) and has common bundled in.
val remapShadowJar = unimined.minecrafts[sourceSets.main.get()]!!
    .remap(tasks.shadowJar.get(), "remapShadowJar") {
        asJar { archiveClassifier.set("") }
    }
tasks.build {
    dependsOn(remapShadowJar)
}

publishMods {
    val mcVersion = project.name

    @Suppress("UNCHECKED_CAST")
    val getCompatibleMcVersions = rootProject.extra.get("getCompatibleMcVersions") as (Project) -> List<String>
    val mcVersions = getCompatibleMcVersions(project)

    file.set(remapShadowJar.flatMap { it.asJar.archiveFile })
    displayName.set(remapShadowJar.flatMap { it.asJar.archiveFile.map { f -> f.asFile.name } })
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (Forge)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("forge")

    val curseforgeId = project.findProperty("curseforge_project_id")?.toString()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").orNull
        ?: project.findProperty("curseforge_token")?.toString()
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
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").orNull
        ?: project.findProperty("modrinth_token")?.toString()
    if (!modrinthId.isNullOrEmpty() && !modrinthToken.isNullOrEmpty()) {
        modrinth {
            projectId.set(modrinthId)
            accessToken.set(modrinthToken)
            minecraftVersions.addAll(mcVersions)
        }
    }
}
