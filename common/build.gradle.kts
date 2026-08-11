import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("architectury-plugin")
    id("maven-publish")
}

// MC 26.1+ ships unobfuscated, so no mappings exist and the regular Loom -- which refuses to
// prepare without them -- cannot be used. `loom-no-remap` is the same jar with remapping
// removed; the controller declares both markers so only the choice is made here.
//
// Loom therefore cannot go in the `plugins {}` block above (that block takes no conditionals),
// which costs us the `loom { }` type-safe accessor. `the<LoomGradleExtensionAPI>()` replaces it.
val unobfuscated = project.name.substringBefore('.').toInt() >= 26
val isLegacy = project.name == "1.12.2"
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom")

val loomApi = the<LoomGradleExtensionAPI>()

val enabled_platforms = project.findProperty("enabled_platforms") as String
val fabric_loader_version = project.findProperty("fabric_loader_version") as String
val minecraft_version = project.findProperty("minecraft_version") as String

architectury {
    common(enabled_platforms.split(","))
}

if (unobfuscated) {
    repositories {
        // Mixin is no longer pulled in through a remapped loader dependency.
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

if (isLegacy) {
    // Legacy Fabric uses its own intermediary (not Fabric's). Without this URL
    // Loom downloads the wrong intermediary and mapping resolution fails.
    the<LoomGradleExtensionAPI>().intermediaryUrl.set(
        "https://maven.legacyfabric.net/net/legacyfabric/intermediary/%1\$s/intermediary-%1\$s-v2.jar"
    )
}

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraft_version")
    // No mappings exist for 26.1+, and loom-no-remap does not ask for any.
    // 1.12.2 predates Mojang mappings (which start at 1.14.4); use Legacy Fabric Yarn instead.
    if (!unobfuscated) {
        if (isLegacy) {
            val yarn_mappings = project.findProperty("yarn_mappings") as String
            "mappings"("net.legacyfabric:yarn:$yarn_mappings:v2")
        } else {
            "mappings"(loomApi.officialMojangMappings())
        }
    }

    // loom-no-remap drops the `mod` prefix on the dependency configurations: there is no
    // remapping step for them to feed.
    val modImplementation = if (unobfuscated) "implementation" else "modImplementation"
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")

    if (unobfuscated) {
        compileOnly("org.spongepowered:mixin:0.8.5")
    }

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
    val mixinCompat = if (project.name == "1.16.5") "JAVA_8" else "JAVA_17"
    inputs.property("mixin_compat", mixinCompat)
    filesMatching("assetbridge.mixins.json") {
        expand("mixin_compat" to mixinCompat)
    }
}
