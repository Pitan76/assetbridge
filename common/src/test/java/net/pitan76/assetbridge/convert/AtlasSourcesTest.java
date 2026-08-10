package net.pitan76.assetbridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AtlasSourcesTest {
    @Test
    void declaresTheDirectoryAModelActuallyUses() {
        assertEquals("entities/chest", AtlasSources.directoryOf("rubycraft:entities/chest/ruby_chest"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Vanilla already stitches these, recursively.
            "rubycraft:block/ruby_block",
            "rubycraft:block/nested/ruby_block",
            "rubycraft:item/ruby",
            "block/cube_all",
            // Vanilla's own sprites are not ours to declare.
            "minecraft:entity/chest/normal",
            // A texture variable, not a file.
            "#all",
            // No directory to declare.
            "rubycraft:ruby_block"
    })
    void declaresNothingForCoveredReferences(String reference) {
        assertNull(AtlasSources.directoryOf(reference));
    }
}
