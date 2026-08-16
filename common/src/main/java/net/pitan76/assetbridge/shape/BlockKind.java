package net.pitan76.assetbridge.shape;

import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A kind of vanilla block a bridged block can be registered as, instead of the generic
 * {@link net.pitan76.assetbridge.block.BridgedBlock}.
 *
 * <p>What the assets say about a block is what it looks like, and a mod that draws stairs draws
 * them by inheriting from the vanilla stairs model. Taking that at its word buys the block the
 * whole of the vanilla behaviour — the corner shapes, the placement, the collision — instead of
 * an approximation of its outline.
 *
 * <p>It also has to be safe: the blockstate file is served unchanged, so every property in it
 * must be one the vanilla class has, with values that class accepts. {@link #accepts} is what
 * makes that a check rather than a hope, and a block that fails it stays a
 * {@code BridgedBlock}. The values here are the vanilla state definitions and change with a
 * Minecraft version about as often as the block names themselves.
 */
public enum BlockKind {
    STAIRS(properties()
            .with("facing", "north", "south", "west", "east")
            .with("half", "top", "bottom")
            .with("shape", "straight", "inner_left", "inner_right", "outer_left", "outer_right")
            .with("waterlogged", "true", "false"),
            "facing", "half"),

    SLAB(properties()
            .with("type", "top", "bottom", "double")
            .with("waterlogged", "true", "false"),
            "type"),

    FENCE(properties()
            .with("north", "true", "false")
            .with("east", "true", "false")
            .with("south", "true", "false")
            .with("west", "true", "false")
            .with("waterlogged", "true", "false"),
            "north", "east", "south", "west"),

    /** Glass pane and iron bars: the same state definition as a fence, a different model. */
    PANE(properties()
            .with("north", "true", "false")
            .with("east", "true", "false")
            .with("south", "true", "false")
            .with("west", "true", "false")
            .with("waterlogged", "true", "false"),
            "north", "east", "south", "west"),

    WALL(properties()
            .with("up", "true", "false")
            .with("north", "none", "low", "tall")
            .with("east", "none", "low", "tall")
            .with("south", "none", "low", "tall")
            .with("west", "none", "low", "tall")
            .with("waterlogged", "true", "false"),
            "up", "north", "east", "south", "west"),

    FENCE_GATE(properties()
            .with("facing", "north", "south", "west", "east")
            .with("open", "true", "false")
            .with("powered", "true", "false")
            .with("in_wall", "true", "false"),
            "facing", "open"),

    DOOR(properties()
            .with("facing", "north", "south", "west", "east")
            .with("half", "upper", "lower")
            .with("hinge", "left", "right")
            .with("open", "true", "false")
            .with("powered", "true", "false"),
            "facing", "half", "hinge"),

    TRAPDOOR(properties()
            .with("facing", "north", "south", "west", "east")
            .with("half", "top", "bottom")
            .with("open", "true", "false")
            .with("powered", "true", "false")
            .with("waterlogged", "true", "false"),
            "facing", "half", "open"),

    LADDER(properties()
            .with("facing", "north", "south", "west", "east")
            .with("waterlogged", "true", "false"),
            "facing");

    private final Map<String, Set<String>> allowed;
    private final Set<String> required;

    BlockKind(Spec spec, String... required) {
        this.allowed = Collections.unmodifiableMap(spec.allowed);
        this.required = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(required)));
    }

    /**
     * Whether a block whose blockstate declares {@code states} can be registered as this kind.
     *
     * <p>Two things have to hold. Every property the file uses must exist on the vanilla block
     * with the values the file uses, or the model loader would reject the file it is served.
     * And the properties that carry the kind's behaviour must all be there, because a "stairs"
     * with no facing is a model that borrowed the look, not a staircase.
     */
    public boolean accepts(BridgedStateDefinition states) {
        Set<String> present = new HashSet<>();
        for (BridgedProperty property : states.properties()) {
            Set<String> values = allowed.get(property.name());
            if (values == null || !values.containsAll(property.values())) return false;
            present.add(property.name());
        }
        return present.containsAll(required);
    }

    /** The vanilla model names that mean this kind, matched against a model's parent chain. */
    @Nullable
    static BlockKind byModelName(String name) {
        if (name.equals("ladder")) return LADDER;
        if (name.contains("trapdoor")) return TRAPDOOR;
        if (name.contains("fence_gate")) return FENCE_GATE;
        if (name.contains("fence")) return FENCE;
        if (name.contains("wall_post") || name.contains("wall_side") || name.contains("wall_inventory")) return WALL;
        if (name.contains("pane_post") || name.contains("pane_side") || name.contains("pane_noside")) return PANE;
        if (name.contains("stairs")) return STAIRS;
        if (name.equals("slab") || name.equals("slab_top") || name.endsWith("_slab") || name.endsWith("_slab_top")) return SLAB;
        if (name.startsWith("door_")) return DOOR;
        return null;
    }

    private static Spec properties() {
        return new Spec();
    }

    /** Builder for the enum constants; readable enough to keep the table honest. */
    private static class Spec {
        private final Map<String, Set<String>> allowed = new LinkedHashMap<>();

        Spec with(String name, String... values) {
            allowed.put(name, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values))));
            return this;
        }
    }

    /** Exposed for the block layer, which has to build the right vanilla class for the kind. */
    public List<String> requiredProperties() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(required));
    }
}
