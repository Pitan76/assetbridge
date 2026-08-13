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

    // FabricLikeMinecraftTransformer defaults every dependency mod's mixin remap to
    // MixinRemapOptions.off() -- no hard-remap of @Inject "method" targets and no refmap
    // generation at all. legacy-fabric-api's own mixins (compiled against Yarn-style
    // method_XXXX intermediary names, e.g. MinecraftClient#reloadResources() = method_5576) are
    // therefore shipped unremapped; Mixin then tries to resolve that literal intermediary string
    // as a final legacyYarn name against the remapped game jar, finds nothing ("No refMap
    // loaded"), and crashes MinecraftClient's class transform before any mod -- including
    // AssetBridge -- initialises. Explicitly re-enabling the base Mixin remapper for the
    // modImplementation configuration restores the hard-remap + refmap generation this ecosystem
    // needs.
    mods {
        modImplementation {
            mixinRemap {
                enableBaseMixin()
            }
        }
    }
}

configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Common shared code (compiled with MCP names → remapped at inclusion time by Unimined).
    // Also bundled via "shadowBundle" (mirroring forge/build.gradle.kts) so its classes actually
    // end up in the built jar -- shadowJar below is scoped to only that configuration, so without
    // this, common's classes are silently absent from the final jar.
    implementation(project(commonProject.path))
    "shadowBundle"(project(commonProject.path))

    "modImplementation"("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:$fabric_api_version") {
        // legacy-fabric-lifecycle-events-v1's "-common-1.8.9" jar carries a mixin
        // (MinecraftClientMixin, target intermediary method_2954) that only resolves under
        // 1.8.9's intermediary numbering. Applied against this project's 1.12.2 intermediary it
        // has no matching target and crashes MinecraftClient's class transform before any mod
        // code -- including AssetBridge's own init -- ever runs. Only that 1.8.9-specific jar is
        // excluded: legacy-fabric-command-api-v1 hard-depends on the plain
        // legacy-fabric-lifecycle-events-v1-common module (the 1.12.2-targeted one, which is not
        // affected), so that one has to stay.
        exclude(group = "net.legacyfabric.legacy-fabric-api", module = "legacy-fabric-lifecycle-events-v1-common-1.8.9")
        // Same story for legacy-fabric-item-groups-v1: its "-common-1.8.9" jar (merged into the
        // runtime mod list as "legacy-fabric-item-groups-v1-common-versioned") carries a
        // ButtonWidgetMixin whose @WrapMethod target only resolves under 1.8.9's mapping, and it
        // is not covered by the modImplementation-wide mixinRemap override below -- Unimined
        // splits these version-merged jars out before that applies. It crashes TitleScreen the
        // same way the lifecycle-events one crashed MinecraftClient's own transform.
        exclude(group = "net.legacyfabric.legacy-fabric-api", module = "legacy-fabric-item-groups-v1-common-1.8.9")
    }
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
