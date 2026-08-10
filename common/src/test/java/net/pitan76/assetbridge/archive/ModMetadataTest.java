package net.pitan76.assetbridge.archive;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetadataTest {
    @Test
    void readsFabricName() {
        Map<String, String> names = ModMetadata.displayNames(Map.of(
                "fabric.mod.json", """
                        {"id": "rubycraft", "name": "Ruby Craft"}"""));

        assertEquals("Ruby Craft", names.get("rubycraft"));
    }

    @Test
    void readsEveryModsTomlBlock() {
        Map<String, String> names = ModMetadata.displayNames(Map.of(
                "META-INF/mods.toml", """
                        modLoader="javafml"
                        [[mods]]
                        modId="buildcraftcore"
                        displayName="BuildCraft Core"
                        [[mods]]
                        modId="buildcraftfactory"
                        displayName="BuildCraft Factory"
                        [[dependencies.buildcraftcore]]
                        modId="minecraft"
                        """));

        assertEquals("BuildCraft Core", names.get("buildcraftcore"));
        assertEquals("BuildCraft Factory", names.get("buildcraftfactory"));
        // The dependency block names no mod of its own.
        assertNull(names.get("minecraft"));
    }

    @Test
    void ignoresBlankAndMissingNames() {
        Map<String, String> names = ModMetadata.displayNames(Map.of(
                "fabric.mod.json", """
                        {"id": "nameless"}""",
                "META-INF/mods.toml", """
                        [[mods]]
                        modId="blank"
                        displayName="  "
                        """));

        assertTrue(names.isEmpty());
    }

    @Test
    void handlesArchivesWithoutMetadata() {
        assertTrue(ModMetadata.displayNames(Map.of()).isEmpty());
    }
}
