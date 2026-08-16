package net.pitan76.assetbridge.block.kind;

import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * A bridged block whose model says it is a trapdoor.
 *
 * <p>The {@code BlockSetType} 1.20 added decides the sounds and whether it opens by hand; oak
 * is the wooden default, which is what a trapdoor with no other information should be.
 */
public class BridgedTrapdoorBlock extends TrapDoorBlock {
    public BridgedTrapdoorBlock(Properties properties) {
        //? if >=1.21 {
        /*super(net.minecraft.world.level.block.state.properties.BlockSetType.OAK, properties);
        *///?} elif >=1.20 {
        /*super(properties, net.minecraft.world.level.block.state.properties.BlockSetType.OAK);
        *///?} else {
        super(properties);
        //?}
    }
}
