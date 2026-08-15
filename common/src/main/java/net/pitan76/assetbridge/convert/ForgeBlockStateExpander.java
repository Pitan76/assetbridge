package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expands a Forge blockstate (the {@code "forge_marker": 1} format used by 1.10&ndash;1.12 mods)
 * into the vanilla blockstate the model loader understands.
 *
 * <p>The format exists to avoid writing out a combinatorial explosion by hand, so it is what
 * mods reach for on doors, fences, stairs and anything else with several properties. Vanilla
 * has no idea what it is: {@code variants} maps a <em>property name</em> to a map of value to
 * partial definition, and the real variants are the cartesian product of those, merged over a
 * shared {@code defaults} block. Left unexpanded, no {@code model} key is reachable at the
 * depth {@link net.pitan76.assetbridge.parse.BlockStateParser} looks, the block is dropped for
 * having no model, and only its item model survives &mdash; which is why a door used to show up
 * as an item and never as a block.
 *
 * <p>Two Forge-only features have no vanilla equivalent in a blockstate and are absorbed here:
 * <ul>
 *   <li>{@code textures} on a variant retextures the model. A vanilla variant cannot, so a
 *       derived model ({@code {"parent": original, "textures": ...}}) is generated per distinct
 *       texture set and the variant points at that instead. Identical sets share one model.</li>
 *   <li>{@code inventory} names the item model rather than a block state. It is returned
 *       separately so the pipeline can write it as {@code models/item/<block>.json}.</li>
 * </ul>
 *
 * <p>{@code transform}, {@code submodel} and {@code custom} are dropped: they are interpreted by
 * Forge's own model loader, which is not present. Dropping them costs some geometry but never
 * fails the load.
 */
public class ForgeBlockStateExpander {
    /** Matches {@code BlockStateCoverage}: past this the file is left to the fallback path. */
    private static final int MAX_COMBINATIONS = 4096;

    /** Variant keys vanilla understands as-is; everything else is Forge-only. */
    private static final String[] PASSTHROUGH_KEYS = {"x", "y", "uvlock", "weight"};

    private ForgeBlockStateExpander() {
    }

    public static boolean isForgeFormat(@Nullable JsonObject blockState) {
        return blockState != null && blockState.has("forge_marker");
    }

    /** The expanded blockstate plus the assets that had to be synthesised to express it. */
    public static class Result {
        private final JsonObject blockState;
        private final Map<String, JsonObject> generatedModels;
        private final JsonObject inventoryModel;
        private final int variantCount;

        Result(JsonObject blockState, Map<String, JsonObject> generatedModels,
               @Nullable JsonObject inventoryModel, int variantCount) {
            this.blockState = blockState;
            this.generatedModels = generatedModels;
            this.inventoryModel = inventoryModel;
            this.variantCount = variantCount;
        }

        public JsonObject blockState() {
            return blockState;
        }

        /** Model name under {@code models/block/} to its JSON. Empty when no retexturing was used. */
        public Map<String, JsonObject> generatedModels() {
            return generatedModels;
        }

        /** The {@code inventory} variant as an item model, or {@code null} if none was declared. */
        @Nullable
        public JsonObject inventoryModel() {
            return inventoryModel;
        }

        public int variantCount() {
            return variantCount;
        }
    }

    /**
     * @param blockName the blockstate file name, used to name any generated model
     * @return the vanilla-form blockstate; never {@code null}, though its variants may be empty
     *         if the file declared nothing usable
     */
    public static Result expand(JsonObject root, String namespace, String blockName) {
        JsonObject defaults = Json.object(root, "defaults");
        JsonObject variants = Json.object(root, "variants");

        // Property name -> value -> partial definition. Insertion ordered so the variant keys
        // (and any generated model names) come out the same on every run.
        Map<String, Map<String, JsonElement>> properties = new LinkedHashMap<>();
        Map<String, JsonElement> explicit = new LinkedHashMap<>();
        JsonElement inventory = null;

        if (variants != null) {
            for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (key.equals("inventory")) {
                    inventory = value;
                    continue;
                }
                if (isExplicitVariant(key, value)) {
                    explicit.put(key, value);
                    continue;
                }
                if (!value.isJsonObject()) continue;

                Map<String, JsonElement> byValue = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> sub : value.getAsJsonObject().entrySet()) {
                    byValue.put(sub.getKey(), sub.getValue());
                }
                if (!byValue.isEmpty()) properties.put(key, byValue);
            }
        }

        ModelPool pool = new ModelPool(namespace, sanitize(blockName));
        JsonObject outVariants = new JsonObject();

        expandProduct(properties, defaults, pool, outVariants);
        addExplicit(explicit, defaults, pool, outVariants, !properties.isEmpty());

        // A file with nothing but 'defaults' is legal Forge and means one property-free state.
        if (outVariants.size() == 0) {
            JsonObject only = new Merged().apply(defaults).toVariant(pool);
            if (only != null) outVariants.add("", only);
        }

        JsonObject blockState = new JsonObject();
        blockState.add("variants", outVariants);

        return new Result(blockState, pool.models, inventoryModel(inventory, defaults), outVariants.size());
    }

    /**
     * Forge writes a property expansion as an object and a whole variant as an array, so the
     * shape is what tells them apart. A key that already names a state ({@code facing=north},
     * or the empty key) is explicit whatever its shape, and so is an object that carries a
     * {@code model} directly &mdash; that is a variant, not a map of them.
     */
    private static boolean isExplicitVariant(String key, JsonElement value) {
        if (key.isEmpty() || key.indexOf('=') >= 0) return true;
        if (value.isJsonArray()) return true;
        return value.isJsonObject() && value.getAsJsonObject().has("model");
    }

    private static void expandProduct(Map<String, Map<String, JsonElement>> properties, @Nullable JsonObject defaults,
                                      ModelPool pool, JsonObject out) {
        if (properties.isEmpty()) return;

        List<String> names = new ArrayList<>(properties.keySet());
        List<List<Map.Entry<String, JsonElement>>> choices = new ArrayList<>(names.size());
        long total = 1;
        for (String name : names) {
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(properties.get(name).entrySet());
            choices.add(entries);
            total *= entries.size();
            // Bail out before building a list we are going to throw away anyway.
            if (total > MAX_COMBINATIONS) return;
        }

        int[] cursor = new int[names.size()];
        while (true) {
            StringBuilder key = new StringBuilder();
            Merged merged = new Merged().apply(defaults);
            for (int i = 0; i < names.size(); i++) {
                Map.Entry<String, JsonElement> chosen = choices.get(i).get(cursor[i]);
                if (key.length() > 0) key.append(',');
                key.append(names.get(i)).append('=').append(chosen.getKey());
                merged.apply(chosen.getValue());
            }
            // A variant with no model is Forge's way of saying "render nothing here";
            // leaving the key out lets BlockStateCoverage fill it with the fallback.
            JsonObject variant = merged.toVariant(pool);
            if (variant != null) out.add(key.toString(), variant);

            int i = names.size() - 1;
            while (i >= 0) {
                cursor[i]++;
                if (cursor[i] < choices.get(i).size()) break;
                cursor[i] = 0;
                i--;
            }
            if (i < 0) return;
        }
    }

    private static void addExplicit(Map<String, JsonElement> explicit, @Nullable JsonObject defaults,
                                    ModelPool pool, JsonObject out, boolean hasProperties) {
        for (Map.Entry<String, JsonElement> entry : explicit.entrySet()) {
            // 'normal' is the pre-1.13 spelling of the property-free state.
            String key = entry.getKey().equals("normal") ? "" : entry.getKey();
            // A property-free state cannot coexist with per-property keys; the expansion wins.
            if (key.isEmpty() && hasProperties) continue;

            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                JsonArray weighted = new JsonArray();
                for (JsonElement element : value.getAsJsonArray()) {
                    JsonObject variant = new Merged().apply(defaults).apply(element).toVariant(pool);
                    if (variant != null) weighted.add(variant);
                }
                if (weighted.size() == 1) {
                    out.add(key, weighted.get(0));
                } else if (weighted.size() > 1) {
                    out.add(key, weighted);
                }
            } else {
                JsonObject variant = new Merged().apply(defaults).apply(value).toVariant(pool);
                if (variant != null) out.add(key, variant);
            }
        }
    }

    @Nullable
    private static JsonObject inventoryModel(@Nullable JsonElement inventory, @Nullable JsonObject defaults) {
        if (inventory == null) return null;

        Merged merged = new Merged().apply(defaults);
        if (inventory.isJsonArray()) {
            JsonArray array = inventory.getAsJsonArray();
            if (array.size() == 0) return null;
            merged.apply(array.get(0));
        } else {
            merged.apply(inventory);
        }
        if (merged.model == null) return null;

        JsonObject model = new JsonObject();
        model.addProperty("parent", BlockStateConverter.qualifyModelReference(merged.model));
        if (merged.textures.size() > 0) model.add("textures", Json.copy(merged.textures));
        return model;
    }

    /** Minecraft rejects a model path with anything but lower case, digits, {@code _} and {@code /}. */
    private static String sanitize(String name) {
        return name.replace('/', '_').toLowerCase(Locale.ROOT);
    }

    /**
     * The result of folding {@code defaults} and every matching partial together, in the order
     * Forge applies them: later wins for a scalar, textures accumulate key by key.
     */
    private static class Merged {
        private String model;
        private final JsonObject textures = new JsonObject();
        private final JsonObject extras = new JsonObject();

        Merged apply(@Nullable JsonElement element) {
            if (element == null) return this;
            // A weighted partial is a list; only its first entry can be merged into a scalar.
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                return array.size() == 0 ? this : apply(array.get(0));
            }
            if (!element.isJsonObject()) return this;

            JsonObject object = element.getAsJsonObject();
            JsonElement modelValue = object.get("model");
            if (modelValue != null) {
                // An explicit null means "no model", and has to be able to clear an inherited one.
                model = modelValue.isJsonPrimitive() ? modelValue.getAsString() : null;
            }
            JsonObject overrides = Json.object(object, "textures");
            if (overrides != null) {
                for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
                    if (entry.getValue().isJsonNull()) {
                        textures.remove(entry.getKey());
                    } else {
                        textures.add(entry.getKey(), entry.getValue());
                    }
                }
            }
            for (String key : PASSTHROUGH_KEYS) {
                if (object.has(key)) extras.add(key, object.get(key));
            }
            return this;
        }

        /** @return the vanilla variant, or {@code null} when nothing would be rendered */
        @Nullable
        JsonObject toVariant(ModelPool pool) {
            if (model == null) return null;

            JsonObject variant = new JsonObject();
            variant.addProperty("model", textures.size() > 0 ? pool.derive(model, textures) : model);
            for (Map.Entry<String, JsonElement> extra : extras.entrySet()) {
                variant.add(extra.getKey(), extra.getValue());
            }
            return variant;
        }
    }

    /**
     * Hands out the derived models that stand in for Forge's per-variant retexturing, one per
     * distinct (parent, textures) pair so that a blockstate reusing the same set &mdash; which is
     * the whole point of the format &mdash; does not multiply the model files.
     */
    private static class ModelPool {
        private final String namespace;
        private final String blockName;
        private final Map<String, String> references = new LinkedHashMap<>();
        private final Map<String, JsonObject> models = new LinkedHashMap<>();

        ModelPool(String namespace, String blockName) {
            this.namespace = namespace;
            this.blockName = blockName;
        }

        String derive(String parent, JsonObject textures) {
            // Qualified here rather than left to BlockStateConverter: that only rewrites the
            // 'model' of a variant, and this ends up as the 'parent' of a model instead.
            String qualifiedParent = BlockStateConverter.qualifyModelReference(parent);
            String key = qualifiedParent + " " + Json.toString(textures);

            String existing = references.get(key);
            if (existing != null) return existing;

            String name = blockName + "_ab" + models.size();
            JsonObject model = new JsonObject();
            model.addProperty("parent", qualifiedParent);
            model.add("textures", Json.copy(textures));
            models.put(name, model);

            String reference = namespace + ":block/" + name;
            references.put(key, reference);
            return reference;
        }
    }
}
