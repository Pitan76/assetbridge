package net.pitan76.assetbridge.block.kind;

import net.minecraft.world.level.block.DoorBlock;

/**
 * A bridged block whose model says it is a door.
 *
 * <p>1.20 gave doors a {@code BlockSetType}, which decides the sounds and whether hand-opening
 * is allowed; oak is the wooden default and keeps the door usable.
 */
public class BridgedDoorBlock extends DoorBlock {
    public BridgedDoorBlock(Properties properties) {
        //? if >=1.21 {
        /*super(net.minecraft.world.level.block.state.properties.BlockSetType.OAK, properties);
        *///?} elif >=1.20 {
        /*super(properties, net.minecraft.world.level.block.state.properties.BlockSetType.OAK);
        *///?} else {
        super(properties);
        //?}
    }
}
