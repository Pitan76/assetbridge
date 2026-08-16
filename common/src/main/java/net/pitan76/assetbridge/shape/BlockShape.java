package net.pitan76.assetbridge.shape;

import org.jetbrains.annotations.Nullable;

import net.pitan76.assetbridge.parse.VariantKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The shape of one bridged block, per state.
 *
 * <p>A blockstate file names a different model for each variant, and the same block can be a
 * slab in one state and a tall plant in another, so the shape is kept the way the file is
 * written: a list of variants, each with the conditions its key spelled out. The first variant
 * whose conditions all hold decides the shape, which is how Minecraft reads the file itself.
 *
 * <p>Built once by {@link BlockShapes} and read once by the block layer, so the lists are
 * handed over rather than copied.
 */
public class BlockShape {
    private final List<Variant> variants;

    public BlockShape(List<Variant> variants) {
        this.variants = Collections.unmodifiableList(variants);
    }

    public List<Variant> variants() {
        return variants;
    }

    /**
     * @param values the state's property values by name
     * @return the first variant that claims the state, or {@code null} when none does and the
     *         state should stay the full cube
     */
    @Nullable
    public Variant variantFor(Map<String, String> values) {
        for (Variant variant : variants) {
            if (VariantKey.matches(variant.conditions(), values)) return variant;
        }
        return null;
    }

    /** The boxes {@link #variantFor} settles on, for a caller that wants nothing else. */
    @Nullable
    public List<ShapeBox> boxesFor(Map<String, String> values) {
        Variant variant = variantFor(values);
        return variant == null ? null : variant.boxes();
    }

//    @Nullable
//    public List<ShapeBox> boxesFor(Map<String, String> values) {
//        for (Variant variant : variants) {
//            if (variant.matches(values)) return variant.boxes();
//        }
//        return null;
//    }

    /** One entry of a blockstate's {@code variants}, reduced to what a shape needs. */
    public static class Variant {
        private final Map<String, String> conditions;
        private final List<ShapeBox> boxes;

        public Variant(Map<String, String> conditions, List<ShapeBox> boxes) {
            this.conditions = conditions;
            this.boxes = boxes;
        }

        /** The properties a state must agree on, as the variant's key spelled them out. */
        public Map<String, String> conditions() {
            return conditions;
        }

        public List<ShapeBox> boxes() {
            return boxes;
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
