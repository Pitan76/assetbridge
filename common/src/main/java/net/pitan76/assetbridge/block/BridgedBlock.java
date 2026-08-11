package net.pitan76.assetbridge.block;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
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

    /**
     * Typed as {@code EnumProperty<Direction>} rather than {@code DirectionProperty}: 26.1 dropped
     * the latter, and before it the two were the same thing -- {@code DirectionProperty} extended
     * exactly this. One declaration therefore covers every version.
     */
    @Nullable
    private final EnumProperty<Direction> facing;
    @Nullable
    private final EnumProperty<Direction.Axis> axis;

    private BridgedBlock(ResourceLocation id, BridgedStateDefinition states) {
        super(propertiesFor(id));

        this.facing = directionProperty();
        this.axis = axisProperty();
        registerDefaultState(defaultStateOf(states));
    }

    private static Properties propertiesFor(ResourceLocation id) {
        Properties properties;
        //? if >=26 {
        /*// 26.1 derives the loot table and the translation key from the block's own id, and throws
        // if it was never set, so the id has to be on the properties before construction.
        properties = Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id));
        *///?} elif >=1.20 {
        /*// 1.20 removed Material; a block's sounds and map colour now come from the properties
        // themselves, and the defaults match what Material.STONE gave us.
        properties = Properties.of();
        *///?} else {
        properties = Properties.of(net.minecraft.world.level.material.Material.STONE);
        //?}

        properties = properties.strength(1.5F, 6.0F);
        if (shouldDisableOcclusion(id)) {
            properties = properties.noOcclusion();
        }
        return properties;
    }

    private static boolean shouldDisableOcclusion(ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        return path.contains("stairs")
                || path.contains("slab")
                || path.contains("wall")
                || path.contains("fence")
                || path.contains("pane")
                || path.contains("door")
                || path.contains("gate")
                || path.contains("button")
                || path.contains("pressure_plate")
                || BridgedBlocks.isCutout(id);
    }

    public static BridgedBlock create(ResourceLocation id, BridgedStateDefinition states) {
        PENDING.set(states);
        try {
            return new BridgedBlock(id, states);
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
    @SuppressWarnings("unchecked")
    private EnumProperty<Direction> directionProperty() {
        Property<?> property = getStateDefinition().getProperty("facing");
        // A generic type cannot appear in an `instanceof` pattern, so the element type is checked
        // separately. This is what `instanceof DirectionProperty` amounted to before 26.1 removed
        // that class.
        if (property instanceof EnumProperty<?> && property.getValueClass() == Direction.class) {
            return (EnumProperty<Direction>) property;
        }
        return null;
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
