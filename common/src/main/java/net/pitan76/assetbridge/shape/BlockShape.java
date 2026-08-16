package net.pitan76.assetbridge.shape;

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
    }
}
