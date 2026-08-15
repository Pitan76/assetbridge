package net.pitan76.assetbridge.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyLangKeyTest {
    @Test
    void readsABlockName() {
        LegacyLangKey key = LegacyLangKey.parse("tile.example.name");
        assertNotNull(key);
        assertTrue(key.isBlock());
        assertEquals("example", key.base());
        assertEquals(-1, key.meta());
        assertEquals("block.examplemod.example", key.modernKey("examplemod"));
    }

    @Test
    void readsAnItemName() {
        LegacyLangKey key = LegacyLangKey.parse("item.example.name");
        assertNotNull(key);
        assertFalse(key.isBlock());
        assertEquals("item.examplemod.example", key.modernKey("examplemod"));
    }

    /** Metadata 0 is the entry itself, so it must land on the plain name and not a suffix. */
    @Test
    void treatsMetadataZeroAsTheEntryItself() {
        LegacyLangKey key = LegacyLangKey.parse("tile.example.0.name");
        assertNotNull(key);
        assertEquals("example", key.base());
        assertEquals(0, key.meta());
        assertEquals("example", key.registryName());
        assertEquals("block.examplemod.example", key.modernKey("examplemod"));
    }

    @Test
    void suffixesTheOtherMetadataValues() {
        LegacyLangKey key = LegacyLangKey.parse("tile.example.11.name");
        assertNotNull(key);
        assertEquals("example", key.base());
        assertEquals(11, key.meta());
        assertEquals("example_meta11", key.registryName());
        assertEquals("block.examplemod.example_meta11", key.modernKey("examplemod"));
    }

    /**
     * Prose the mod hung off the block has no modern counterpart. Rewriting it would invent a
     * key nothing looks up and lose the one that was there, so it must be refused outright.
     */
    @Test
    void refusesKeysThatDoNotNameTheEntry() {
        assertNull(LegacyLangKey.parse("tile.example.tooltip"));
        assertNull(LegacyLangKey.parse("tile.example.desc.0"));
        assertNull(LegacyLangKey.parse("itemGroup.examplemod"));
        assertNull(LegacyLangKey.parse("block.examplemod.example"));
        assertNull(LegacyLangKey.parse("tile..name"));
    }

    /** A dotted name is a name, not a metadata value; only trailing digits are one. */
    @Test
    void keepsANameThatIsNotAMetadataValue() {
        LegacyLangKey dotted = LegacyLangKey.parse("tile.stone.granite.name");
        assertNotNull(dotted);
        assertEquals("stone.granite", dotted.base());
        assertEquals(-1, dotted.meta());

        LegacyLangKey trailingDigits = LegacyLangKey.parse("tile.machine2.name");
        assertNotNull(trailingDigits);
        assertEquals("machine2", trailingDigits.base());
        assertEquals(-1, trailingDigits.meta());
    }
}
