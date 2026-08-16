package net.pitan76.assetbridge.block.kind;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/**
 * A bridged block whose model says it is a staircase.
 *
 * <p>The base state a staircase carries is only consulted for the behaviour of the block it was
 * cut from, which a bridged block does not have; stone stands in for it.
 */
public class BridgedStairsBlock extends StairBlock {
    public BridgedStairsBlock(Properties properties) {
        // Present on every supported version, including the ones where Forge adds a second,
        // supplier-taking constructor beside it.
        super(Blocks.STONE.defaultBlockState(), properties);
    }
}
