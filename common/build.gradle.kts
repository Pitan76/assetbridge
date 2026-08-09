plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("maven-publish")
}

val enabled_platforms = project.findProperty("enabled_platforms") as String
val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

architectury {
    common(enabled_platforms.split(","))
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    "mappings"((project.extensions.getByName("loom") as net.fabricmc.loom.LoomGradleExtension).officialMojangMappings())

    "modImplementation"("net.fabricmc:fabric-loader:$fabric_loader_version")

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
