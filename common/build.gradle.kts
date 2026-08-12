plugins {
    id("xyz.wagyourtail.unimined")
    `java-library`
    `maven-publish`
}

val minecraft_version = project.findProperty("minecraft_version") as String

unimined.minecraft {
    version(minecraft_version)

    mappings {
        searge()
        mcp("stable", "39-1.12")
    }

    // Common には独立したリマップ済み JAR は不要（platform プロジェクトに含まれる）
    defaultRemapJar = false
}

dependencies {
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
    inputs.property("mixin_compat", "JAVA_8")
    filesMatching("assetbridge.mixins.json") {
        expand("mixin_compat" to "JAVA_8")
    }
}
