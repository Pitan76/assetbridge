import org.gradle.kotlin.dsl.*

plugins {
    id("dev.architectury.loom") version "1.14-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1" apply false
}

val minecraft_version = project.findProperty("minecraft_version") as String
val maven_group = project.findProperty("maven_group") as String
val mod_version = project.findProperty("mod_version") as String
val archives_name = project.findProperty("archives_name") as String

architectury {
    minecraft = minecraft_version
}

allprojects {
    group = maven_group
    version = mod_version
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

    configure<org.gradle.api.plugins.BasePluginExtension> {
        archivesName.set("$archives_name-${project.name}")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "minecraft"("net.minecraft:minecraft:$minecraft_version")
        "mappings"((project.extensions.getByName("loom") as net.fabricmc.loom.LoomGradleExtension).officialMojangMappings())
    }

    configure<org.gradle.api.plugins.JavaPluginExtension> {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    configure<org.gradle.api.publish.PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                val base = extensions.getByType<org.gradle.api.plugins.BasePluginExtension>()
                artifactId = base.archivesName.get()
                from(components["java"])
            }
        }
    }
}
