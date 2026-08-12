plugins {
    id("dev.kikugie.stonecutter")
    id("architectury-plugin") version "3.5.169" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}
stonecutter active "1.12.2"

// stonecutter parameters {
//     replacements {
//         string {
//             direction = eval(current.version, ">=26")
//             replace("ResourceLocation", "Identifier")
//         }
//     }
// }

fun javaFor(nodeName: String): JavaVersion {
    return JavaVersion.VERSION_1_8
}

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
        maven("https://maven.legacyfabric.net/")
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
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaFor(project.name).majorVersion))
            }
        }
 
        tasks.withType<JavaCompile>().configureEach {
            val major = javaFor(project.name).majorVersion.toInt()
            if (major >= 9) {
                options.release.set(major)
            } else {
                options.release.set(null as Int?)
            }
        }
    }
}

// Builds every registered version. The 0.6-era `registerChiseled`/`chiseled` API is
// gone in 0.9.x; task aggregation replaces it.
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every registered version."
    dependsOn(stonecutter.tasks.named("build"))
}

// Serialise `build` across nodes. Stonecutter documents task ordering as a way to
// keep concurrency-sensitive tasks from running in parallel.
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
