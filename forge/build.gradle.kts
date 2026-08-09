plugins {
    id("com.gradleup.shadow")
}

val forge_version: String by project

loom {
    forge {
        mixinConfig("assetbridge.mixins.json")
    }
}

architectury {
    platformSetupLoomIde()
    forge()
}

val common by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    val developmentForge by getting
    developmentForge.extendsFrom(common)
}

val shadowBundle by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    "forge"("net.minecraftforge:forge:$forge_version")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionForge"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations.set(listOf(shadowBundle))
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    input.set(tasks.shadowJar.flatMap { it.archiveFile })
}
