plugins {
    id("dev.architectury.loom") version "1.14-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1" apply false
}

val minecraft_version: String by project
val maven_group: String by project
val mod_version: String by project
val archives_name: String by project

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

    base {
        archivesName.set("$archives_name-${project.name}")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "minecraft"("net.minecraft:minecraft:$minecraft_version")
        "mappings"(loom.officialMojangMappings())
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = base.archivesName.get()
                from(components["java"])
            }
        }
    }
}
