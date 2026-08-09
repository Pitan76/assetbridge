plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
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

// Under Stonecutter the artifact-producing project is the node (`:common:1.18.2`),
// not the branch container (`:common`). Resolve it through the sibling branch.
val commonProject: Project = requireNotNull(stonecutter.node.sibling("common")?.project) {
    "No common node matching $project"
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    "mappings"((project.extensions.getByName("loom") as net.fabricmc.loom.LoomGradleExtension).officialMojangMappings())

    "modImplementation"("net.fabricmc:fabric-loader:$fabric_loader_version")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    "common"(project(commonProject.path, "namedElements")) { isTransitive = false }
    "shadowBundle"(project(commonProject.path, "transformProductionFabric"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations.set(listOf(project.configurations["shadowBundle"]))
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}
