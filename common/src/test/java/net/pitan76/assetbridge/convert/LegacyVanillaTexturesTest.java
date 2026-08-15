package net.pitan76.assetbridge.convert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyVanillaTexturesTest {
    @Test
    void movesTheWoodFromTheFrontToTheBack() {
        assertEquals("block/oak_planks", LegacyVanillaTextures.rename("block/planks_oak"));
        assertEquals("block/spruce_leaves", LegacyVanillaTextures.rename("block/leaves_spruce"));
        assertEquals("block/birch_sapling", LegacyVanillaTextures.rename("block/sapling_birch"));
        // 1.12 called dark oak 'big_oak' in its texture names.
        assertEquals("block/dark_oak_planks", LegacyVanillaTextures.rename("block/planks_big_oak"));
    }

    /** The face stays at the end: log_oak_top is oak_log_top, not oak_top_log. */
    @Test
    void keepsTheFaceAtTheEnd() {
        assertEquals("block/oak_log", LegacyVanillaTextures.rename("block/log_oak"));
        assertEquals("block/oak_log_top", LegacyVanillaTextures.rename("block/log_oak_top"));
        assertEquals("block/jungle_log_top", LegacyVanillaTextures.rename("block/log_jungle_top"));
    }

    @Test
    void movesTheColourFromTheFrontToTheBack() {
        assertEquals("block/black_terracotta", LegacyVanillaTextures.rename("block/hardened_clay_stained_black"));
        assertEquals("block/red_wool", LegacyVanillaTextures.rename("block/wool_colored_red"));
        assertEquals("block/white_stained_glass", LegacyVanillaTextures.rename("block/glass_white"));
        assertEquals("block/blue_concrete", LegacyVanillaTextures.rename("block/concrete_blue"));
        assertEquals("block/blue_concrete_powder", LegacyVanillaTextures.rename("block/concrete_powder_blue"));
        // 'silver' was renamed as well as moved.
        assertEquals("block/light_gray_wool", LegacyVanillaTextures.rename("block/wool_colored_silver"));
    }

    /** The longer prefix has to win, or the rest is read as a colour that is not one. */
    @Test
    void prefersTheLongerPrefix() {
        assertEquals("block/lime_stained_glass_pane_top", LegacyVanillaTextures.rename("block/glass_pane_top_lime"));
    }

    @Test
    void handlesTheDoorsWhoseWoodSitsInTheMiddle() {
        assertEquals("block/oak_door_bottom", LegacyVanillaTextures.rename("block/door_oak_lower"));
        assertEquals("block/acacia_door_top", LegacyVanillaTextures.rename("block/door_acacia_upper"));
    }

    @Test
    void knowsTheOnesRenamedWithoutAPattern() {
        assertEquals("block/piston_top", LegacyVanillaTextures.rename("block/piston_top_normal"));
        assertEquals("block/terracotta", LegacyVanillaTextures.rename("block/hardened_clay"));
        assertEquals("block/grass_block_side", LegacyVanillaTextures.rename("block/grass_side"));
        assertEquals("block/cobweb", LegacyVanillaTextures.rename("block/web"));
        assertEquals("block/bricks", LegacyVanillaTextures.rename("block/brick"));
    }

    /**
     * A rule that fires on a name it does not understand would send a working reference
     * somewhere that does not exist, so each one only accepts a qualifier that really existed.
     */
    @Test
    void leavesNamesThatOnlyLookLikeARuleAlone() {
        assertNull(LegacyVanillaTextures.rename("block/glass"));
        assertNull(LegacyVanillaTextures.rename("block/glass_pane_top"));
        assertNull(LegacyVanillaTextures.rename("block/planks_mahogany"));
        assertNull(LegacyVanillaTextures.rename("block/log_oak_diagonal"));
        assertNull(LegacyVanillaTextures.rename("block/oak_planks"));
        assertNull(LegacyVanillaTextures.rename("block/stone"));
    }

    /** Item and entity textures were not part of this rename, and neither is a bare name. */
    @Test
    void onlyTouchesBlockTextures() {
        assertNull(LegacyVanillaTextures.rename("item/planks_oak"));
        assertNull(LegacyVanillaTextures.rename("planks_oak"));
    }
}
