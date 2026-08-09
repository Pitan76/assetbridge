package net.pitan76.assetbridge.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetVersionTest {
    /**
     * The four MVP targets must land in four distinct generations. They used to collapse
     * into one bucket from 1.19.3 onwards, which made 1.20.1 and 1.21.1 indistinguishable
     * as conversion targets.
     */
    @ParameterizedTest
    @CsvSource({
            "1.18.2,  MODERN",
            "1.19.2,  MODERN",
            "1.20.1,  ATLASES",
            "1.21.1,  COMPONENTS"
    })
    void separatesTheTargetsThisModBuildsFor(String version, AssetVersion expected) {
        assertEquals(expected, AssetVersion.fromMinecraftVersion(version));
    }

    @ParameterizedTest
    @CsvSource({
            // Each pair straddles a generation boundary.
            "1.19.2,  MODERN",
            "1.19.3,  ATLASES",
            "1.20.4,  ATLASES",
            "1.20.5,  COMPONENTS",
            "1.21.3,  COMPONENTS",
            "1.21.4,  ITEM_DEFINITIONS"
    })
    void placesTheBoundariesWhereTheAssetSpecChanged(String version, AssetVersion expected) {
        assertEquals(expected, AssetVersion.fromMinecraftVersion(version));
    }

    @ParameterizedTest
    @CsvSource({
            "3,   LEGACY",
            "4,   FLATTENED",
            "8,   MODERN",
            "9,   MODERN",
            "12,  ATLASES",
            "22,  ATLASES",
            "32,  COMPONENTS",
            "34,  COMPONENTS",
            "46,  ITEM_DEFINITIONS"
    })
    void readsGenerationsFromPackFormats(int packFormat, AssetVersion expected) {
        assertEquals(expected, AssetVersion.fromPackFormat(packFormat));
    }

    @Test
    void treatsANegativePackFormatAsUnknown() {
        assertEquals(AssetVersion.UNKNOWN, AssetVersion.fromPackFormat(-1));
    }

    @Test
    void rejectsStringsThatAreNotReleaseVersions() {
        assertNull(AssetVersion.fromMinecraftVersion("1"));
        assertNull(AssetVersion.fromMinecraftVersion("1.5.2"));
        assertNull(AssetVersion.fromMinecraftVersion("22w13a"));
    }

    @Test
    void ordersGenerationsSoRulesKeepApplyingToLaterOnes() {
        assertTrue(AssetVersion.COMPONENTS.isAtLeast(AssetVersion.ATLASES));
        assertTrue(AssetVersion.ITEM_DEFINITIONS.isAtLeast(AssetVersion.ATLASES));
        assertTrue(AssetVersion.ATLASES.isAtLeast(AssetVersion.ATLASES));
        assertFalse(AssetVersion.MODERN.isAtLeast(AssetVersion.ATLASES));
        assertFalse(AssetVersion.LEGACY.isAtLeast(AssetVersion.MODERN));
    }

    @Test
    void comparesUnknownAsModern() {
        assertEquals(AssetVersion.MODERN, AssetVersion.UNKNOWN.resolved());
        assertTrue(AssetVersion.UNKNOWN.isAtLeast(AssetVersion.FLATTENED));
        assertFalse(AssetVersion.UNKNOWN.isAtLeast(AssetVersion.ATLASES));
    }

    /**
     * Guards the bug where the pack format was a constant: whatever version this node
     * builds for, the declared format has to belong to the generation it targets.
     */
    @Test
    void reportsAPackFormatConsistentWithTheTargetGeneration() {
        assertEquals(RuntimePack.generation(),
                AssetVersion.fromPackFormat(RuntimePack.resourcePackFormat()));
        assertTrue(RuntimePack.dataPackFormat() >= RuntimePack.resourcePackFormat(),
                "data pack formats have run ahead of resource pack formats since 1.15");
    }
}
