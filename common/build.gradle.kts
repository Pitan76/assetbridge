import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("architectury-plugin")
    id("maven-publish")
}

apply(plugin = "dev.architectury.loom")

val loomApi = the<LoomGradleExtensionAPI>()

val enabled_platforms = project.findProperty("enabled_platforms") as String
val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

architectury {
    common(enabled_platforms.split(","))
}

the<LoomGradleExtensionAPI>().intermediaryUrl.set(
    "https://maven.legacyfabric.net/net/legacyfabric/intermediary/%1\$s/intermediary-%1\$s-v2.jar"
)

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")

    val yarn_mappings = project.findProperty("yarn_mappings") as String
    "mappings"("net.legacyfabric:yarn:$yarn_mappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val mixinCompat = "JAVA_8"
    inputs.property("mixin_compat", mixinCompat)
    filesMatching("assetbridge.mixins.json") {
        expand("mixin_compat" to mixinCompat)
    }
}
