plugins {
    id("com.gradleup.shadow")
}

val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val fabric_api_version = project.findProperty("fabric_api_version") as String

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

val gametest = sourceSets.create("gametest") {
    val main = sourceSets.main.get()
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}

loom {
    runs {
        create("gametest") {
            server()
            name("Game Test")
            source(gametest)
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${project.layout.buildDirectory.get().asFile}/gametest-report.xml")
        }
    }
}

val common = configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    getByName("developmentFabric").extendsFrom(common)
}

val shadowBundle = configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    "modImplementation"("net.fabricmc:fabric-loader:$fabric_loader_version")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionFabric"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations.set(listOf(shadowBundle))
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}
