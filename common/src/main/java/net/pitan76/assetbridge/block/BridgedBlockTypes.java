package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.block.kind.BridgedDoorBlock;
import net.pitan76.assetbridge.block.kind.BridgedFenceBlock;
import net.pitan76.assetbridge.block.kind.BridgedFenceGateBlock;
import net.pitan76.assetbridge.block.kind.BridgedLadderBlock;
import net.pitan76.assetbridge.block.kind.BridgedPaneBlock;
import net.pitan76.assetbridge.block.kind.BridgedSlabBlock;
import net.pitan76.assetbridge.block.kind.BridgedStairsBlock;
import net.pitan76.assetbridge.block.kind.BridgedTrapdoorBlock;
import net.pitan76.assetbridge.block.kind.BridgedWallBlock;
import net.pitan76.assetbridge.shape.BlockKind;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the vanilla block a bridged block was recognised as.
 *
 * <p>Whether that recognition is safe was already settled while the bundle was built
 * ({@link BlockKind#accepts}); this only turns the answer into an instance. All of these
 * declare more properties than the blockstate file uses, which is fine: a variant key names
 * only the properties it cares about, so the file still covers every state.
 */
public class BridgedBlockTypes {
    private BridgedBlockTypes() {
    }

    /** @return the block, or {@code null} for a kind this version cannot build */
    @Nullable
    public static Block create(BlockKind kind, ResourceLocation id) {
        // None of these are full cubes, so none of them may cull their neighbours' faces.
        Block.Properties properties = BridgedBlock.propertiesFor(id, true);

        switch (kind) {
            case STAIRS:
                return new BridgedStairsBlock(properties);
            case SLAB:
                return new BridgedSlabBlock(properties);
            case FENCE:
                return new BridgedFenceBlock(properties);
            case PANE:
                return new BridgedPaneBlock(properties);
            case WALL:
                return new BridgedWallBlock(properties);
            case FENCE_GATE:
                return new BridgedFenceGateBlock(properties);
            case DOOR:
                return new BridgedDoorBlock(properties);
            case TRAPDOOR:
                return new BridgedTrapdoorBlock(properties);
            case LADDER:
                return new BridgedLadderBlock(properties);
            default:
                return null;
        }
    }
}
