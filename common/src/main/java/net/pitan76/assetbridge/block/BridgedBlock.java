package net.pitan76.assetbridge.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * A block that carries the properties recovered from an external blockstate file, so that
 * file can be served unchanged and every variant resolves.
 *
 * <p>The only behaviour attached to those properties is choosing an orientation on placement.
 * Anything beyond that belongs to the mod the assets came from and is out of scope.
 */
public class BridgedBlock extends Block {
    /**
     * {@code createBlockStateDefinition} runs inside {@code super(...)}, before any field of
     * this class can be assigned, so the definition is handed over out of band.
     */
    private static final ThreadLocal<BridgedStateDefinition> PENDING = new ThreadLocal<>();

    @Nullable
    private final DirectionProperty facing;
    @Nullable
    private final EnumProperty<Direction.Axis> axis;

    private BridgedBlock(BridgedStateDefinition states) {
        super(Properties.of(Material.STONE).strength(1.5F, 6.0F));

        this.facing = directionProperty();
        this.axis = axisProperty();
        registerDefaultState(defaultStateOf(states));
    }

    public static BridgedBlock create(BridgedStateDefinition states) {
        PENDING.set(states);
        try {
            return new BridgedBlock(states);
        } finally {
            PENDING.remove();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        BridgedStateDefinition states = PENDING.get();
        if (states == null) return;

        for (BridgedProperty property : states.properties()) {
            builder.add(toVanilla(property));
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();

        if (facing != null) {
            // A six-way block follows where the player is looking; a horizontal one faces them.
            Direction direction = facing == BlockStateProperties.FACING
                    ? context.getNearestLookingDirection().getOpposite()
                    : context.getHorizontalDirection().getOpposite();
            if (facing.getPossibleValues().contains(direction)) {
                state = state.setValue(facing, direction);
            }
        }
        if (axis != null) {
            Direction.Axis clicked = context.getClickedFace().getAxis();
            if (axis.getPossibleValues().contains(clicked)) {
                state = state.setValue(axis, clicked);
            }
        }
        return state;
    }

    @Nullable
    private DirectionProperty directionProperty() {
        Property<?> property = getStateDefinition().getProperty("facing");
        return property instanceof DirectionProperty direction ? direction : null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private EnumProperty<Direction.Axis> axisProperty() {
        Property<?> property = getStateDefinition().getProperty("axis");
        if (property == BlockStateProperties.AXIS || property == BlockStateProperties.HORIZONTAL_AXIS) {
            return (EnumProperty<Direction.Axis>) property;
        }
        return null;
    }

    private BlockState defaultStateOf(BridgedStateDefinition states) {
        BlockState state = getStateDefinition().any();
        for (BridgedProperty bridged : states.properties()) {
            Property<?> property = getStateDefinition().getProperty(bridged.name());
            if (property != null) state = withValue(state, property, bridged.defaultValue());
        }
        return state;
    }

    private static Property<?> toVanilla(BridgedProperty property) {
        // A vanilla property is preferred where it matches exactly, because its type is what
        // makes orientation on placement possible.
        Property<?> known = KnownProperties.match(property);
        if (known != null) return known;

        return switch (property.kind()) {
            case BOOLEAN -> BooleanProperty.create(property.name());
            case INTEGER -> IntegerProperty.create(property.name(), property.min(), property.max());
            case STRING -> StringProperty.create(property.name(), property.values());
        };
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }
}
