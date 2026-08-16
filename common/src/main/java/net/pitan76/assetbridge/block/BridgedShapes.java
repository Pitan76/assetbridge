package net.pitan76.assetbridge.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.pitan76.assetbridge.shape.BlockShape;
import net.pitan76.assetbridge.shape.ShapeBox;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the boxes worked out from a block's models into the {@code VoxelShape} the game asks
 * for, one per state.
 *
 * <p>Built once, when the block is created: a shape is immutable and every state's is known in
 * advance, so nothing has to be worked out while the player is walking into it.
 */
public class BridgedShapes {
    private BridgedShapes() {
    }

    /** @return the shape of each state that has one; states left out are full cubes */
    public static Map<BlockState, VoxelShape> build(StateDefinition<?, BlockState> definition, BlockShape shape) {
        Map<BlockState, VoxelShape> shapes = new HashMap<>();
        // The same box list comes up for state after state, and turning one into a VoxelShape
        // is the expensive part.
        Map<List<ShapeBox>, VoxelShape> built = new HashMap<>();

        for (BlockState state : definition.getPossibleStates()) {
            List<ShapeBox> boxes = shape.boxesFor(valuesOf(state));
            if (boxes == null || boxes.isEmpty()) continue;

            VoxelShape voxels = built.get(boxes);
            if (voxels == null) {
                voxels = toVoxelShape(boxes);
                built.put(boxes, voxels);
            }
            shapes.put(state, voxels);
        }
        return shapes;
    }

    private static VoxelShape toVoxelShape(List<ShapeBox> boxes) {
        VoxelShape shape = Shapes.empty();
        for (ShapeBox box : boxes) {
            shape = Shapes.or(shape, Shapes.box(
                    box.minX / 16.0, box.minY / 16.0, box.minZ / 16.0,
                    box.maxX / 16.0, box.maxY / 16.0, box.maxZ / 16.0));
        }
        return shape.optimize();
    }

    /** A state as the blockstate file writes it: property name to the value's own name. */
    private static Map<String, String> valuesOf(BlockState state) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), nameOf(state, property));
        }
        return values;
    }

    private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
