package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.pitan76.assetbridge.block.kind.BridgedDoorBlock;
import net.pitan76.assetbridge.block.kind.BridgedFenceGateBlock;
import net.pitan76.assetbridge.block.kind.BridgedLadderBlock;
import net.pitan76.assetbridge.block.kind.BridgedPaneBlock;
import net.pitan76.assetbridge.block.kind.BridgedStairsBlock;
import net.pitan76.assetbridge.block.kind.BridgedTrapdoorBlock;
import net.pitan76.assetbridge.shape.BlockKind;

/**
 * Builds the vanilla block a bridged block was recognised as.
 *
 * <p>Whether that recognition is safe was already settled while the bundle was built
 * ({@link BlockKind#accepts}); this only turns the answer into an instance. All of these
 * declare more properties than the blockstate file uses, which is fine: a variant key names
 * only the properties it cares about, so the file still covers every state.
 *
 * <p>Where a vanilla class can simply be constructed it is, and the {@code kind} package holds
 * only the ones that cannot: a constructor that is not public, or one whose arguments changed
 * with a Minecraft version.
 */
public class BridgedBlockTypes {
    private BridgedBlockTypes() {
    }

    public static Block create(BlockKind kind, ResourceLocation id) {
        // None of these are full cubes, so none of them may cull their neighbours' faces.
        Block.Properties properties = BridgedBlock.propertiesFor(id, true);

        switch (kind) {
            case STAIRS:
                return new BridgedStairsBlock(properties);
            case SLAB:
                return new SlabBlock(properties);
            case FENCE:
                return new FenceBlock(properties);
            case PANE:
                return new BridgedPaneBlock(properties);
            case WALL:
                return new WallBlock(properties);
            case FENCE_GATE:
                return new BridgedFenceGateBlock(properties);
            case DOOR:
                return new BridgedDoorBlock(properties);
            case TRAPDOOR:
                return new BridgedTrapdoorBlock(properties);
            case LADDER:
                return new BridgedLadderBlock(properties);
            default:
                // Every kind is handled above; a new constant must be added here as well.
                throw new IllegalArgumentException("No block class for kind " + kind);
        }
    }
}
