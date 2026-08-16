package net.pitan76.assetbridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Repairs models whose {@code parent} points at something that is not there.
 *
 * <p>External archives reference each other freely: a mod's model can inherit from a model
 * belonging to a mod the player did not put in {@code mods/assetbridge/}, or from one whose
 * namespace was skipped because a real mod owns it. Minecraft answers an unresolvable
 * parent with the missing model — the black and magenta cube — for the whole block, which
 * is a worse outcome than an approximate shape.
 *
 * <p>Only the direct parent of each model is checked. A broken link deeper in a chain is
 * repaired when that model's own turn comes, so the result does not depend on the order the
 * models happen to be visited in, and a parent cycle needs no special handling.
 */
public class ModelReferenceResolver {
    /** Texture keys worth inheriting into the replacement, most representative first. */
    private static final List<String> TEXTURE_KEYS =
            Arrays.asList("all", "texture", "particle", "layer0", "up", "side", "end", "north", "0");

    private ModelReferenceResolver() {
    }

    /** @return how many models had to be replaced */
    public static int resolve(BridgedAssetManager assets) {
        int repaired = 0;
        // Copied: the loop replaces resources as it goes.
        for (AssetPath path : new ArrayList<>(assets.resources().keySet())) {
            if (!isModel(path)) continue;

            JsonObject model = assets.readJson(path);
            if (model == null) continue;

            String parent = Json.string(model, "parent");
            if (parent == null || resolves(assets, parent)) continue;

            AssetBridge.LOGGER.warn("Model {} inherits from {}, which is not available; "
                    + "substituting a stand-in", path, parent);
            assets.putResource(path, Json.toString(substituteFor(model, path))
                    .getBytes(StandardCharsets.UTF_8));
            repaired++;
        }
        return repaired;
    }

    /**
     * The same repair for the models a blockstate names directly.
     *
     * <p>{@link #resolve} follows {@code parent} links, which leaves the other way a model gets
     * referenced untouched: a variant naming a model straight out. A mod that draws its block
     * with a loader Asset Bridge does not have &mdash; {@code forge:block/fluid}, say &mdash; puts
     * that in its blockstate rather than in a model of its own, so nothing in the bundle ever
     * mentions it as a parent and the block reaches the game as the missing model.
     *
     * <p>Where only some variants are broken the block's own working model stands in for them,
     * which keeps the family look. Where all of them are, the block falls back to the simplest
     * vanilla shape wearing the texture named after it &mdash; but only if the archive shipped
     * one. A shape with no texture to put on it is not an improvement on the missing model: it
     * renders just as magenta, while claiming to have loaded and costing a warning per face.
     * Blocks the archive genuinely cannot draw, a Forge fluid say, are left to say so.
     *
     * <p>Runs after {@link #resolve}, so a model it might substitute in has already been
     * repaired itself.
     *
     * @return how many variant references had to be pointed somewhere else
     */
    public static int resolveBlockStates(BridgedAssetManager assets) {
        int repaired = 0;
        // Copied: a stand-in may add a model to the bundle as it goes.
        for (AssetPath path : new ArrayList<>(assets.resources().keySet())) {
            if (path.category() != AssetPath.Category.BLOCKSTATE) continue;

            String name = path.blockStateName();
            if (name == null) continue;

            JsonObject blockState = assets.readJson(path);
            if (blockState == null || !namesAnUnavailableModel(assets, blockState)) continue;

            String replacement = firstResolvingModel(assets, blockState);
            if (replacement == null) replacement = standInModelFor(assets, path.namespace(), name);
            if (replacement == null) {
                AssetBridge.LOGGER.warn("Blockstate {} names a model that is not available, and the archive "
                        + "has nothing to stand in for it; leaving it as it is", path);
                continue;
            }

            int changed = repointUnavailable(assets, blockState, replacement);
            if (changed == 0) continue;

            AssetBridge.LOGGER.warn("Blockstate {} names {} model(s) that are not available; "
                    + "pointing them at {}", path, changed, replacement);
            assets.putResource(path, Json.toString(blockState).getBytes(StandardCharsets.UTF_8));
            repaired += changed;
        }
        return repaired;
    }

    /** Checked before anything is built, so an intact blockstate costs one walk and nothing else. */
    private static boolean namesAnUnavailableModel(BridgedAssetManager assets, JsonElement element) {
        return findModel(assets, element, false) != null;
    }

    @Nullable
    private static String firstResolvingModel(BridgedAssetManager assets, JsonElement element) {
        return findModel(assets, element, true);
    }

    /** @return the first model reference whose availability is {@code available}, or {@code null} */
    @Nullable
    private static String findModel(BridgedAssetManager assets, JsonElement element, boolean available) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = findModel(assets, child, available);
                if (found != null) return found;
            }
            return null;
        }
        if (!element.isJsonObject()) return null;

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("model") && value.isJsonPrimitive()) {
                String model = value.getAsString();
                if (resolves(assets, model) == available) return model;
            } else if (value.isJsonObject() || value.isJsonArray()) {
                String found = findModel(assets, value, available);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int repointUnavailable(BridgedAssetManager assets, JsonElement element, String replacement) {
        int changed = 0;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                changed += repointUnavailable(assets, child, replacement);
            }
            return changed;
        }
        if (!element.isJsonObject()) return 0;

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("model") && value.isJsonPrimitive()) {
                if (!resolves(assets, value.getAsString())) {
                    entry.setValue(new com.google.gson.JsonPrimitive(replacement));
                    changed++;
                }
            } else if (value.isJsonObject() || value.isJsonArray()) {
                changed += repointUnavailable(assets, value, replacement);
            }
        }
        return changed;
    }

    /**
     * The model to use when a blockstate names nothing that exists.
     *
     * <p>The archive's own block model of that name where there is one, otherwise a plain cube
     * wearing the texture named after the block. Both are looked up, never guessed: a block
     * whose sprite the archive did ship comes out recognisable instead of magenta, and one
     * where nothing can be confirmed is reported as having no stand-in rather than being given
     * a bare shape that renders no better and warns once per face.
     *
     * @return the model reference, or {@code null} when the archive offers nothing to use
     */
    @Nullable
    private static String standInModelFor(BridgedAssetManager assets, String namespace, String name) {
        AssetPath modelPath = AssetPath.blockModel(namespace, name);
        String reference = namespace + ":block/" + name;
        if (assets.hasResource(modelPath)) return reference;

        AssetPath texture = new AssetPath(AssetPath.PackKind.CLIENT, namespace, "textures/block/" + name + ".png");
        if (!assets.hasResource(texture)) return null;

        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", reference);
        model.add("textures", textures);
        assets.putResource(modelPath, Json.toString(model).getBytes(StandardCharsets.UTF_8));
        return reference;
    }

    private static boolean isModel(AssetPath path) {
        return path.kind() == AssetPath.PackKind.CLIENT
                && path.path().startsWith("models/") && path.path().endsWith(".json");
    }

    /**
     * Anything in the {@code minecraft} namespace is taken on trust: the vanilla models
     * cannot be enumerated from here, and a pack that inherits from one is the normal case.
     */
    private static boolean resolves(BridgedAssetManager assets, String parent) {
        int colon = parent.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : parent.substring(0, colon);
        String path = colon < 0 ? parent : parent.substring(colon + 1);
        if (namespace.equals("minecraft")) return true;

        return assets.hasResource(AssetPath.model(namespace, path));
    }

    /**
     * A model that describes its own geometry only needs the parent dropped. One that has
     * nothing but a parent is replaced by the simplest vanilla shape of its kind, keeping
     * whichever texture it named so the block is still recognisable.
     */
    private static JsonObject substituteFor(JsonObject model, AssetPath path) {
        if (model.has("elements")) {
            JsonObject standalone = Json.copy(model);
            standalone.remove("parent");
            return standalone;
        }

        boolean item = path.path().startsWith("models/item/");
        JsonObject substitute = new JsonObject();
        substitute.addProperty("parent", item ? "minecraft:item/generated" : "minecraft:block/cube_all");

        // For block stand-ins, only use textures from the block atlas (textures/block/).
        // Textures under item/ or items/ live in the item atlas and would be rejected by the
        // block renderer on 26.1+ ("sprite from outside supported atlas").
        String texture = item ? someTextureOf(model) : someBlockTextureOf(model);
        if (texture != null) {
            JsonObject textures = new JsonObject();
            textures.addProperty(item ? "layer0" : "all", texture);
            substitute.add("textures", textures);
        }
        return substitute;
    }

    /** The most representative texture the model names, ignoring {@code #placeholder} refs. */
    @Nullable
    private static String someTextureOf(JsonObject model) {
        JsonObject byKey = Json.object(model, "textures");
        if (byKey == null) return null;

        // A '#' value points at another key of the parent we just lost.
        for (String key : TEXTURE_KEYS) {
            String value = Json.string(byKey, key);
            if (value != null && !value.startsWith("#")) return value;
        }
        for (Map.Entry<String, JsonElement> entry : byKey.entrySet()) {
            String key = entry.getKey();
            String value = Json.string(byKey, key);
            if (value != null && !value.startsWith("#")) return value;
        }
        return null;
    }

    /**
     * Like {@link #someTextureOf}, but skips textures that live in the item atlas
     * ({@code item/} or {@code items/} path segment). Used when building a block stand-in
     * so the substitute never references a sprite outside the block atlas.
     */
    @Nullable
    private static String someBlockTextureOf(JsonObject model) {
        JsonObject byKey = Json.object(model, "textures");
        if (byKey == null) return null;

        for (String key : TEXTURE_KEYS) {
            String value = Json.string(byKey, key);
            if (value != null && !value.startsWith("#") && !isItemAtlasTexture(value)) return value;
        }
        for (Map.Entry<String, JsonElement> entry : byKey.entrySet()) {
            String key = entry.getKey();
            String value = Json.string(byKey, key);
            if (value != null && !value.startsWith("#") && !isItemAtlasTexture(value)) return value;
        }
        return null;
    }

    /**
     * Returns {@code true} when the texture reference path falls under {@code item/} or
     * {@code items/}, which are served from the item atlas rather than the block atlas.
     * Accepts both {@code namespace:item/foo} and bare {@code item/foo} forms.
     */
    private static boolean isItemAtlasTexture(String reference) {
        int colon = reference.indexOf(':');
        String path = colon < 0 ? reference : reference.substring(colon + 1);
        return path.startsWith("item/") || path.startsWith("items/");
    }

}
