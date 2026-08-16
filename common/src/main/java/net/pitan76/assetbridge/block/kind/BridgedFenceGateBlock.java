package net.pitan76.assetbridge.block.kind;

import net.minecraft.world.level.block.FenceGateBlock;

/**
 * A bridged block whose model says it is a fence gate.
 *
 * <p>1.20 gave gates a {@code WoodType}, which decides only the sound the gate makes; oak is
 * the sound the vanilla wooden gates use and the closest thing to a neutral choice.
 */
public class BridgedFenceGateBlock extends FenceGateBlock {
    public BridgedFenceGateBlock(Properties properties) {
        //? if >=1.21 {
        /*super(net.minecraft.world.level.block.state.properties.WoodType.OAK, properties);
        *///?} elif >=1.20 {
        /*super(properties, net.minecraft.world.level.block.state.properties.WoodType.OAK);
        *///?} else {
        super(properties);
        //?}
    }
}
