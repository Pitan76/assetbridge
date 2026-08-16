package net.pitan76.assetbridge.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.parse.VariantKey;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Works out a block's shape from the models its blockstate names.
 *
 * <p>The blockstate is used rather than the block's representative model alone, because that is
 * where the shape actually is: which model a state uses, and how far it is turned, are both in
 * the file, and a variant's {@code x} and {@code y} turn the boxes with the model.
 */
public class BlockShapes {
    private static final List<ShapeBox> FULL_CUBE =
            Collections.singletonList(new ShapeBox(0, 0, 0, 16, 16, 16));

    private BlockShapes() {
    }

    /**
     * @param blockState the converted blockstate file, as it will be served
     * @return the shape, or {@code null} when every state is a full cube or nothing could be
     *         read — either way the block is left exactly as it was
     */
    @Nullable
    public static BlockShape of(ModelGeometry models, JsonObject blockState) {
        JsonObject variants = Json.object(blockState, "variants");
        if (variants != null) return fromVariants(models, variants);

        JsonArray multipart = Json.array(blockState, "multipart");
        if (multipart != null) return fromMultipart(models, multipart);

        return null;
    }

    @Nullable
    private static BlockShape fromVariants(ModelGeometry models, JsonObject variants) {
        List<BlockShape.Variant> built = new ArrayList<>();
        boolean shaped = false;

        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            JsonObject variant = firstObject(entry.getValue());
            if (variant == null) return null;

            Map<String, String> conditions = VariantKey.parse(entry.getKey());
            if (conditions == null) return null;

            List<ShapeBox> boxes = boxesOf(models, variant);
            if (boxes == null) {
                // This state really is a full cube. It still has to be listed, so a state it
                // covers is not claimed by a later variant that happens to be shaped.
                built.add(new BlockShape.Variant(conditions, FULL_CUBE));
                continue;
            }
            shaped = true;
            built.add(new BlockShape.Variant(conditions, boxes));
        }
        return shaped ? new BlockShape(built) : null;
    }

    /**
     * A multipart block is assembled from the parts whose conditions hold, so its shape is
     * their union. Only the parts that apply to every state — the ones with no {@code when} —
     * are used: they are the block's body, and a conditional part is an attachment to a
     * neighbour, which is exactly the kind of thing the player should not be blocked by.
     */
    @Nullable
    private static BlockShape fromMultipart(ModelGeometry models, JsonArray multipart) {
        List<ShapeBox> boxes = new ArrayList<>();
        for (JsonElement element : multipart) {
            if (!element.isJsonObject()) continue;

            JsonObject part = element.getAsJsonObject();
            if (part.has("when")) continue;

            JsonElement apply = part.get("apply");
            if (apply == null) continue;

            JsonObject variant = firstObject(apply);
            if (variant == null) continue;

            List<ShapeBox> partBoxes = boxesOf(models, variant);
            if (partBoxes == null) return null; // an unconditional full cube: nothing to narrow
            boxes.addAll(partBoxes);
        }
        if (boxes.isEmpty()) return null;

        return new BlockShape(Collections.singletonList(
                new BlockShape.Variant(Collections.<String, String>emptyMap(), boxes)));
    }

    /** The boxes one variant object describes, turned the way that variant turns its model. */
    @Nullable
    private static List<ShapeBox> boxesOf(ModelGeometry models, JsonObject variant) {
        String model = Json.string(variant, "model");
        if (model == null) return null;

        List<ShapeBox> boxes = models.resolve(model).boxes();
        if (boxes == null) return null;

        int x = intOf(variant, "x");
        int y = intOf(variant, "y");
        if (x == 0 && y == 0) return boxes;

        List<ShapeBox> turned = new ArrayList<>(boxes.size());
        for (ShapeBox box : boxes) {
            turned.add(box.rotateX(x).rotateY(y));
        }
        return turned;
    }

    /** A variant is an object, or a weighted list of them; the first one is representative. */
    @Nullable
    private static JsonObject firstObject(JsonElement element) {
        if (element.isJsonObject()) return element.getAsJsonObject();
        if (!element.isJsonArray()) return null;

        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) return child.getAsJsonObject();
        }
        return null;
    }

    private static int intOf(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return 0;
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
