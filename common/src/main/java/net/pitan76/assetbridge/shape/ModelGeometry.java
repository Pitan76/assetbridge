package net.pitan76.assetbridge.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads what a block model says about the block it draws: the boxes it is made of, and the
 * chain of models it inherited them from.
 *
 * <p>A model that describes no geometry of its own inherits it, so the {@code parent} chain is
 * followed until one does. A chain that ends in the {@code minecraft} namespace ends outside
 * the bundle: those models are not in the archives and cannot be read, so the handful whose
 * shape is worth having is listed in {@link #VANILLA} and everything else is answered with "no
 * shape", which leaves the block a full cube — the same result as before this existed.
 *
 * <p>One instance serves one analysis run and is dropped afterwards. It remembers what it has
 * already resolved, because a model is shared: every block of a mod's stone family inherits
 * from the same handful of parents, and without this each of them would be read out of the
 * archive and parsed again.
 */
public class ModelGeometry {
    /** Beyond this a shape costs more to test against than it is worth; the outer box stands in. */
    private static final int MAX_BOXES = 48;

    /** Guards against a parent cycle in a hand-written archive. */
    private static final int MAX_DEPTH = 16;

    /**
     * The vanilla models a bridged block realistically inherits from whose shape is not a full
     * cube. Anything not listed reads as a full cube, which is what an unshaped block already is.
     */
    private static final Map<String, List<ShapeBox>> VANILLA = vanillaShapes();

    private final BridgedAssetManager assets;
    private final Map<String, Resolved> resolved = new HashMap<>();

    public ModelGeometry(BridgedAssetManager assets) {
        this.assets = assets;
    }

    /** What one model turned out to be, once its parents had been followed. */
    public static class Resolved {
        /** The models walked through, the model itself first and its furthest ancestor last. */
        private final List<String> chain;
        @Nullable
        private final List<ShapeBox> boxes;

        Resolved(List<String> chain, @Nullable List<ShapeBox> boxes) {
            this.chain = chain;
            this.boxes = boxes;
        }

        public List<String> chain() {
            return chain;
        }

        /**
         * The boxes the model is drawn out of, or {@code null} when it is a full cube or
         * nothing about it could be worked out — both of which mean "leave the block as it is".
         */
        @Nullable
        public List<ShapeBox> boxes() {
            return boxes;
        }
    }

    /**
     * Follows {@code modelId} to the model that carries the geometry, in one walk: what kind of
     * block this is and what shape it has are both decided by the same chain, and reading it
     * twice would mean reading every file in it twice.
     */
    public Resolved resolve(String modelId) {
        Resolved cached = resolved.get(modelId);
        if (cached != null) return cached;

        Resolved answer = walk(modelId);
        resolved.put(modelId, answer);
        return answer;
    }

    private Resolved walk(String modelId) {
        List<String> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = modelId;

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            if (!seen.add(current)) break;
            chain.add(current);

            AssetPath path = AssetPath.fromModelId(current);
            if (path == null) break;
            // The vanilla model ends the chain: it is not in the bundle, so neither its
            // elements nor its own parent can be read.
            if (path.namespace().equals("minecraft")) return new Resolved(chain, vanillaBoxes(current));

            JsonObject model = assets.readJson(path);
            if (model == null) break;

            JsonArray elements = Json.array(model, "elements");
            if (elements != null) return new Resolved(chain, boxesOf(elements));

            String parent = Json.string(model, "parent");
            if (parent == null) break;
            current = parent;
        }
        return new Resolved(chain, null);
    }

    /** @return the boxes an {@code elements} array describes, or {@code null} for a full cube */
    @Nullable
    static List<ShapeBox> boxesOf(JsonArray elements) {
        List<ShapeBox> boxes = new ArrayList<>();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) continue;

            ShapeBox box = boxOf(element.getAsJsonObject());
            if (box != null) boxes.add(box);
        }
        if (boxes.isEmpty()) return null;
        // A model made of one full cube is what an unshaped block already is.
        if (boxes.size() == 1 && boxes.get(0).isFullCube()) return null;

        if (boxes.size() > MAX_BOXES) {
            ShapeBox outer = boxes.get(0);
            for (ShapeBox box : boxes) {
                outer = outer.union(box);
            }
            return outer.isFullCube() ? null : Collections.singletonList(outer);
        }
        return boxes;
    }

    @Nullable
    private static ShapeBox boxOf(JsonObject element) {
        double[] from = triple(element, "from");
        double[] to = triple(element, "to");
        if (from == null || to == null) return null;

        ShapeBox box = new ShapeBox(from[0], from[1], from[2], to[0], to[1], to[2]);

        JsonObject rotation = Json.object(element, "rotation");
        if (rotation != null) box = rotated(box, rotation);

        return box.clamped();
    }

    /**
     * A rotated element, as the box that encloses it.
     *
     * <p>An element's own rotation is free-angle, so what it sweeps is not a box any more. The
     * enclosing box is the closest thing that is: it never lets the player through a part of the
     * block that is drawn, at the cost of a little solid air around a diagonal one.
     */
    private static ShapeBox rotated(ShapeBox box, JsonObject rotation) {
        double[] origin = triple(rotation, "origin");
        String axis = Json.string(rotation, "axis");
        JsonElement angleElement = rotation.get("angle");
        if (origin == null || axis == null || angleElement == null || !angleElement.isJsonPrimitive()) return box;

        double angle;
        try {
            angle = angleElement.getAsDouble();
        } catch (NumberFormatException e) {
            return box;
        }
        if (angle == 0) return box;

        double radians = Math.toRadians(angle);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};

        ShapeBox enclosing = null;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double dx = x - origin[0];
                    double dy = y - origin[1];
                    double dz = z - origin[2];

                    double rx = dx;
                    double ry = dy;
                    double rz = dz;
                    if (axis.equals("x")) {
                        ry = dy * cos - dz * sin;
                        rz = dy * sin + dz * cos;
                    } else if (axis.equals("y")) {
                        rx = dx * cos + dz * sin;
                        rz = -dx * sin + dz * cos;
                    } else if (axis.equals("z")) {
                        rx = dx * cos - dy * sin;
                        ry = dx * sin + dy * cos;
                    } else {
                        return box;
                    }

                    ShapeBox corner = new ShapeBox(origin[0] + rx, origin[1] + ry, origin[2] + rz,
                            origin[0] + rx, origin[1] + ry, origin[2] + rz);
                    enclosing = enclosing == null ? corner : enclosing.union(corner);
                }
            }
        }
        return enclosing == null ? box : enclosing;
    }

    @Nullable
    private static double[] triple(JsonObject object, String key) {
        JsonArray array = Json.array(object, key);
        if (array == null || array.size() != 3) return null;

        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive()) return null;
            try {
                values[i] = value.getAsDouble();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return values;
    }

    @Nullable
    private static List<ShapeBox> vanillaBoxes(String modelId) {
        return VANILLA.get(AssetPath.modelName(modelId));
    }

    private static Map<String, List<ShapeBox>> vanillaShapes() {
        Map<String, List<ShapeBox>> shapes = new HashMap<>();
        // The plant shape: what cross-shaped models are given in vanilla.
        List<ShapeBox> cross = Collections.singletonList(new ShapeBox(2, 0, 2, 14, 16, 14));
        shapes.put("cross", cross);
        shapes.put("tinted_cross", cross);
        shapes.put("crop", cross);

        shapes.put("carpet", Collections.singletonList(new ShapeBox(0, 0, 0, 16, 1, 16)));
        shapes.put("slab", Collections.singletonList(new ShapeBox(0, 0, 0, 16, 8, 16)));
        shapes.put("slab_top", Collections.singletonList(new ShapeBox(0, 8, 0, 16, 16, 16)));
        shapes.put("pressure_plate_up", Collections.singletonList(new ShapeBox(1, 0, 1, 15, 1, 15)));
        shapes.put("pressure_plate_down", Collections.singletonList(new ShapeBox(1, 0, 1, 15, 0.5, 15)));
        shapes.put("ladder", Collections.singletonList(new ShapeBox(0, 0, 13, 16, 16, 16)));
        shapes.put("stairs", Arrays.asList(
                new ShapeBox(0, 0, 0, 16, 8, 16),
                new ShapeBox(0, 8, 8, 16, 16, 16)));
        return shapes;
    }
}
