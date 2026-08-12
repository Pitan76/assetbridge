package net.pitan76.assetbridge.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places the bridged blocks the way a player would and checks the state that comes out.
 *
 * <p>The orientation logic in {@code BridgedBlock#getStateForPlacement} needs a real
 * {@code BlockPlaceContext}, a level and a player, so it cannot be covered by the plain JUnit
 * tests in {@code :common}.
 */
public class BridgedBlockPlacementTest implements FabricGameTest {
    /** The block the test clicks on; anything solid and non-replaceable will do. */
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);
    private static final BlockPos PLACED = new BlockPos(1, 2, 1);

    @GameTest(template = EMPTY_STRUCTURE)
    public void facingFollowsTheDirectionLookedAt(GameTestHelper helper) {
        place(helper, GameTestBlocks.FACING, 0.0F, 0.0F);

        // Looking straight ahead at yaw 0 is looking south, and a block faces its placer.
        helper.assertBlockProperty(PLACED, BlockStateProperties.FACING, Direction.NORTH);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void facingCanPointVertically(GameTestHelper helper) {
        // A six-way block uses the nearest looking direction, so pitch counts.
        place(helper, GameTestBlocks.FACING, 0.0F, -90.0F);

        helper.assertBlockProperty(PLACED, BlockStateProperties.FACING, Direction.DOWN);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void horizontalFacingIgnoresPitch(GameTestHelper helper) {
        // Yaw 90 is looking west; the pitch must not push this to UP or DOWN.
        place(helper, GameTestBlocks.HORIZONTAL_FACING, 90.0F, -90.0F);

        helper.assertBlockProperty(PLACED, BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void axisFollowsTheClickedFace(GameTestHelper helper) {
        // The support is below, so the clicked face is the top one: a vertical axis.
        place(helper, GameTestBlocks.AXIS, 0.0F, 0.0F);

        helper.assertBlockProperty(PLACED, BlockStateProperties.AXIS, Direction.Axis.Y);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownPropertyKeepsItsDefault(GameTestHelper helper) {
        // A property with no vanilla counterpart carries no placement behaviour, so the block
        // has to come out on the first value listed in the blockstate file.
        place(helper, GameTestBlocks.CUSTOM_PROPERTY, 0.0F, 0.0F);

        helper.assertBlockState(PLACED, state -> {
            Property<?> variant = state.getBlock().getStateDefinition().getProperty("variant");
            return variant != null && state.getValue(variant).equals("plain");
        }, () -> "expected variant=plain");
        helper.succeed();
    }

    /**
     * Clicks the top of {@link #SUPPORT} with the block's item, which places it at
     * {@link #PLACED}.
     */
    private static void place(GameTestHelper helper, Block block, float yRot, float xRot) {
        helper.setBlock(SUPPORT, Blocks.STONE);

        BlockPos support = helper.absolutePos(SUPPORT);
        Player player = helper.makeMockPlayer();
        // Standing away from the target keeps the placement from being blocked by the player.
        player.setPos(support.getX() + 0.5D, support.getY() + 1.0D, support.getZ() + 4.5D);
        player.setYRot(yRot);
        player.yRotO = yRot;
        player.setXRot(xRot);
        player.xRotO = xRot;

        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D), Direction.UP, support, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertBlockPresent(block, PLACED);
    }
}
