package net.pitan76.assetbridge.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.pitan76.assetbridge.asset.BridgedProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Recognises recovered properties that have an exact vanilla counterpart.
 *
 * <p>{@link StringProperty} would render these correctly on its own; the point of using the
 * vanilla property instead is its type. A {@code DirectionProperty} is what lets
 * {@link BridgedBlock} decide an orientation when the block is placed.
 *
 * <p>The match has to be exact — same name, same value set — otherwise a property that merely
 * looks familiar would get vanilla's values rather than the ones the blockstate file uses.
 */
public class KnownProperties {
    private static final Set<String> HORIZONTAL = new HashSet<>(Arrays.asList("north", "south", "west", "east"));
    private static final Set<String> ALL_DIRECTIONS = new HashSet<>(Arrays.asList("north", "south", "west", "east", "up", "down"));
    private static final Set<String> ALL_AXES = new HashSet<>(Arrays.asList("x", "y", "z"));
    private static final Set<String> HORIZONTAL_AXES = new HashSet<>(Arrays.asList("x", "z"));

    private KnownProperties() {
    }

    /** @return the vanilla property to use, or {@code null} to fall back to a StringProperty */
    @Nullable
    public static Property<?> match(BridgedProperty property) {
        Set<String> values = new HashSet<>(property.values());
        if (values.size() != property.values().size()) return null;

        switch (property.name()) {
            case "facing":
                if (values.equals(HORIZONTAL)) return BlockStateProperties.HORIZONTAL_FACING;
                if (values.equals(ALL_DIRECTIONS)) return BlockStateProperties.FACING;
                return null;
            case "axis":
                if (values.equals(ALL_AXES)) return BlockStateProperties.AXIS;
                if (values.equals(HORIZONTAL_AXES)) return BlockStateProperties.HORIZONTAL_AXIS;
                return null;
            default:
                return null;
        }
    }
}
