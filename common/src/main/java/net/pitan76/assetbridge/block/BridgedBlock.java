package net.pitan76.assetbridge.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;

/**
 * A block that carries the properties recovered from an external blockstate file, so that
 * file can be served unchanged and every variant resolves.
 *
 * <p>No behaviour is attached to the properties: they exist purely so the right model is
 * picked. Deciding a state on placement is a later step.
 */
public class BridgedBlock extends Block {
    /**
     * {@code createBlockStateDefinition} runs inside {@code super(...)}, before any field of
     * this class can be assigned, so the definition is handed over out of band.
     */
    private static final ThreadLocal<BridgedStateDefinition> PENDING = new ThreadLocal<>();

    private BridgedBlock(BridgedStateDefinition states) {
        super(Properties.of(Material.STONE).strength(1.5F, 6.0F));
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

    private BlockState defaultStateOf(BridgedStateDefinition states) {
        BlockState state = getStateDefinition().any();
        for (BridgedProperty bridged : states.properties()) {
            Property<?> property = getStateDefinition().getProperty(bridged.name());
            if (property != null) state = withValue(state, property, bridged.defaultValue());
        }
        return state;
    }

    private static Property<?> toVanilla(BridgedProperty property) {
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
