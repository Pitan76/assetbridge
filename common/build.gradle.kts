val enabled_platforms = project.findProperty("enabled_platforms") as String
val fabric_loader_version = project.findProperty("fabric_loader_version") as String

architectury {
    common(enabled_platforms.split(","))
}

dependencies {
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
