package net.pitan76.assetbridge.block;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.pitan76.assetbridge.asset.BridgedProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class KnownPropertiesTest {
    /** BlockStateProperties pulls in SoundEvents, which needs the registries to exist. */
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matchesTheFourWayFacingProperty() {
        assertSame(BlockStateProperties.HORIZONTAL_FACING,
                match("facing", "north", "south", "east", "west"));
    }

    @Test
    void matchesTheSixWayFacingProperty() {
        assertSame(BlockStateProperties.FACING,
                match("facing", "north", "south", "east", "west", "up", "down"));
    }

    @Test
    void ignoresTheOrderValuesAppearIn() {
        assertNotNull(match("facing", "west", "north", "east", "south"));
    }

    @Test
    void matchesBothAxisProperties() {
        assertSame(BlockStateProperties.AXIS, match("axis", "x", "y", "z"));
        assertSame(BlockStateProperties.HORIZONTAL_AXIS, match("axis", "x", "z"));
    }

    @Test
    void refusesAPartialValueSet() {
        // Using vanilla's property here would give the block values its blockstate file
        // never mentions, leaving those states without a model.
        assertNull(match("facing", "north", "south"));
        assertNull(match("axis", "x", "y"));
    }

    @Test
    void refusesAValueSetWithExtras() {
        assertNull(match("facing", "north", "south", "east", "west", "sideways"));
    }

    @Test
    void refusesUnrelatedNames() {
        assertNull(match("orientation", "north", "south", "east", "west"));
        assertNull(match("half", "top", "bottom"));
    }

    private static Object match(String name, String... values) {
        BridgedProperty property = BridgedProperty.of(name, List.of(values));
        assertNotNull(property);
        return KnownProperties.match(property);
    }
}
