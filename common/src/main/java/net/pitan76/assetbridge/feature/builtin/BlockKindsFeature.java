package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.feature.Feature;

/**
 * Registers a bridged block as the vanilla block class its model describes: a model that
 * inherits from {@code minecraft:block/stairs} becomes a {@code StairBlock}, a slab a
 * {@code SlabBlock}, and so on.
 *
 * <p>The block then behaves the way the player expects it to — stairs turn at corners, a door
 * opens, a fence connects — rather than merely looking like it should. A block whose blockstate
 * does not fit the vanilla class is registered as a plain block instead, so the file can still
 * be served unchanged.
 *
 * <p>Read by {@code AssetPipeline} while the bundle is built, which is why {@link #apply} has
 * nothing to do.
 */
public class BlockKindsFeature implements Feature {
    public static final String ID = "block_kinds";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Register a bridged block as the vanilla block its model inherits from (stairs, slab, fence, "
                + "wall, pane, gate, door, trapdoor, ladder), so it behaves like one. Off: every block is a plain block.";
    }
}
