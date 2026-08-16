package net.pitan76.assetbridge.shape;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of one bridged block, per state.
 *
 * <p>A blockstate file names a different model for each variant, and the same block can be a
 * slab in one state and a tall plant in another, so the shape is kept the way the file is
 * written: a list of variants, each with the conditions its key spelled out. The first variant
 * whose conditions all hold decides the shape, which is how Minecraft reads the file itself.
 */
public class BlockShape {
    private final List<Variant> variants;

    public BlockShape(List<Variant> variants) {
        this.variants = new ArrayList<>(variants);
    }

    public List<Variant> variants() {
        return Collections.unmodifiableList(variants);
    }

    public boolean isEmpty() {
        return variants.isEmpty();
    }

    /**
     * @param values the state's property values by name
     * @return the boxes to use, or {@code null} when this state has no shape of its own and
     *         should stay the full cube
     */
    @Nullable
    public List<ShapeBox> boxesFor(Map<String, String> values) {
        for (Variant variant : variants) {
            if (variant.matches(values)) return variant.boxes();
        }
        return null;
    }

    /** One entry of a blockstate's {@code variants}, reduced to what a shape needs. */
    public static class Variant {
        private final Map<String, String> conditions;
        private final List<ShapeBox> boxes;

        public Variant(Map<String, String> conditions, List<ShapeBox> boxes) {
            this.conditions = new LinkedHashMap<>(conditions);
            this.boxes = new ArrayList<>(boxes);
        }

        public Map<String, String> conditions() {
            return Collections.unmodifiableMap(conditions);
        }

        public List<ShapeBox> boxes() {
            return Collections.unmodifiableList(boxes);
        }

        /**
         * A variant key names only the properties it cares about, so a state matches when it
         * agrees on those and is free in the rest. A property the block does not have at all
         * never matches, which drops a variant left over from a file we could not register in
         * full rather than letting it claim every state.
         */
        public boolean matches(Map<String, String> values) {
            for (Map.Entry<String, String> condition : conditions.entrySet()) {
                if (!condition.getValue().equals(values.get(condition.getKey()))) return false;
            }
            return true;
        }
    }
}
