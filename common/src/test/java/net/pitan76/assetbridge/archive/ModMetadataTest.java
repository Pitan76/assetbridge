package net.pitan76.assetbridge.archive;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetadataTest {
    @Test
    void readsFabricName() {
        Map<String, String> names = ModMetadata.displayNames(Collections.singletonMap(
                "fabric.mod.json", "\n                        {\"id\": \"rubycraft\", \"name\": \"Ruby Craft\"}"));

        assertEquals("Ruby Craft", names.get("rubycraft"));
    }

    @Test
    void readsEveryModsTomlBlock() {
        Map<String, String> names = ModMetadata.displayNames(Collections.singletonMap(
                "META-INF/mods.toml", "\n                        modLoader=\"javafml\"\n                        [[mods]]\n                        modId=\"buildcraftcore\"\n                        displayName=\"BuildCraft Core\"\n                        [[mods]]\n                        modId=\"buildcraftfactory\"\n                        displayName=\"BuildCraft Factory\"\n                        [[dependencies.buildcraftcore]]\n                        modId=\"minecraft\"\n                        "));

        assertEquals("BuildCraft Core", names.get("buildcraftcore"));
        assertEquals("BuildCraft Factory", names.get("buildcraftfactory"));
        // The dependency block names no mod of its own.
        assertNull(names.get("minecraft"));
    }

    @Test
    void ignoresBlankAndMissingNames() {
        Map<String, String> map = new HashMap<>();
        map.put("fabric.mod.json", "\n                        {\"id\": \"nameless\"}");
        map.put("META-INF/mods.toml", "\n                        [[mods]]\n                        modId=\"blank\"\n                        displayName=\"  \"\n                        ");

        Map<String, String> names = ModMetadata.displayNames(map);

        assertTrue(names.isEmpty());
    }

    @Test
    void handlesArchivesWithoutMetadata() {
        assertTrue(ModMetadata.displayNames(new HashMap<>()).isEmpty());
    }
}
