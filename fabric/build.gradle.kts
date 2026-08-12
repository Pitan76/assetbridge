plugins {
    id("xyz.wagyourtail.unimined")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0"
    java
}

val minecraft_version    = project.findProperty("minecraft_version")    as String
val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val yarn_mappings        = project.findProperty("yarn_mappings")        as String
val fabric_api_version   = project.findProperty("fabric_api_version")   as String

val commonProject: Project = project(":common:${stonecutter.current.project}")
evaluationDependsOn(commonProject.path)

unimined.minecraft {
    version(minecraft_version)

    legacyFabric {
        loader(fabric_loader_version)
    }

    mappings {
        legacyIntermediary()
        legacyYarn(yarn_mappings.toInt())
    }
}

configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Common shared code (compiled with MCP names → remapped at inclusion time by Unimined).
    // Must go through "modImplementation", not plain "implementation": only Unimined's mod-aware
    // configurations get cross-mapping-namespace remapping applied, and common's classes (MCP
    // names, e.g. CreativeTabs) need remapping to this module's Legacy Yarn names (e.g. ItemGroup).
    "modImplementation"(project(commonProject.path))

    "modImplementation"("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:$fabric_api_version")
}

tasks.processResources {
    val mcDep = project.findProperty("mc_dep") as String
    val fabricApiId = project.findProperty("fabric_api_id") as String

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

publishMods {
    val mcVersion = project.name

    @Suppress("UNCHECKED_CAST")
    val getCompatibleMcVersions = rootProject.extra.get("getCompatibleMcVersions") as (Project) -> List<String>
    val mcVersions = getCompatibleMcVersions(project)

    file.set(tasks.jar.flatMap { it.archiveFile })
    displayName.set(tasks.jar.flatMap { it.archiveFile.map { f -> f.asFile.name } })
    changelog.set("Release of version ${project.version} for Minecraft $mcVersion (Fabric)")
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)
    modLoaders.add("fabric")

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
            requires("legacy-fabric-api")
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
            requires("legacy-fabric-api")
        }
    }
}
