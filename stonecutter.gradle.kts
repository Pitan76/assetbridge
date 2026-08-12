plugins {
    id("dev.kikugie.stonecutter")
    id("xyz.wagyourtail.unimined") version "1.4.1" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}
stonecutter active "1.12.2"

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
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.legacyfabric.net/")
        maven("https://maven.fabricmc.net/")
        // Minecraft assets/libraries
        maven("https://libraries.minecraft.net/")
        maven("https://piston-meta.mojang.com/v1/packages/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.tterrag.com/")
        maven("https://dvs1.progwml6.com/files/maven/")
    }

    pluginManager.withPlugin("java") {
        val branchName = project.parent?.name ?: project.name
        configure<BasePluginExtension> {
            archivesName.set("$archives_name-$branchName")
        }

        group = "$maven_group.$branchName"
        version = "$mod_version+${project.name}"

        configure<JavaPluginExtension> {
            withSourcesJar()
            // 1.12.2 は Java 8
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            // Java 8 ターゲット — options.release は Java 9+ のみ
            options.release.set(null as Int?)
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
    }
}

// Builds every registered version.
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every registered version."
    dependsOn(stonecutter.tasks.named("build"))
}

stonecutter tasks {
    order("build")
}

val getCompatibleMcVersions: (Project) -> List<String> = { proj ->
    val mcVersion = proj.name
    val mcDep = proj.findProperty("mc_dep")?.toString() ?: mcVersion
    val regex = Regex("""\d+\.\d+(?:\.\d+)?""")
    val matches = regex.findAll(mcDep).map { it.value }.toList()
    if (matches.isNotEmpty()) {
        val start = matches.first()
        val end = matches.last()
        val startParts = start.split(".")
        val endParts = end.split(".")
        if (startParts.size >= 2 && endParts.size >= 2 && startParts[0] == endParts[0]) {
            val major = startParts[0]
            val list = mutableListOf<String>()
            if (startParts[1] == endParts[1]) {
                val minor = startParts[1]
                val startPatch = startParts.getOrNull(2)?.toIntOrNull() ?: 0
                val endPatch = endParts.getOrNull(2)?.toIntOrNull() ?: 0
                for (patch in startPatch..endPatch) {
                    if (patch == 0) {
                        list.add("$major.$minor")
                    } else {
                        list.add("$major.$minor.$patch")
                    }
                }
            } else {
                list.addAll(matches)
            }
            list
        } else {
            matches
        }
    } else {
        listOf(mcVersion)
    }
}
extra.set("getCompatibleMcVersions", getCompatibleMcVersions)
