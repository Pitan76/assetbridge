package net.pitan76.assetbridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
            List.of("all", "texture", "particle", "layer0", "up", "side", "end", "north", "0");

    private ModelReferenceResolver() {
    }

    /** @return how many models had to be replaced */
    public static int resolve(AssetBundle bundle) {
        int repaired = 0;
        // Copied: the loop replaces resources as it goes.
        for (AssetPath path : List.copyOf(bundle.resources().keySet())) {
            if (!isModel(path)) continue;

            JsonObject model = read(bundle, path);
            if (model == null) continue;

            String parent = parentOf(model);
            if (parent == null || resolves(bundle, parent)) continue;

            AssetBridge.LOGGER.warn("Model {} inherits from {}, which is not available; "
                    + "substituting a stand-in", path, parent);
            bundle.putResource(path, Json.toString(substituteFor(model, path))
                    .getBytes(StandardCharsets.UTF_8));
            repaired++;
        }
        return repaired;
    }

    private static boolean isModel(AssetPath path) {
        return path.kind() == AssetPath.PackKind.CLIENT
                && path.path().startsWith("models/") && path.path().endsWith(".json");
    }

    /**
     * Anything in the {@code minecraft} namespace is taken on trust: the vanilla models
     * cannot be enumerated from here, and a pack that inherits from one is the normal case.
     */
    private static boolean resolves(AssetBundle bundle, String parent) {
        int colon = parent.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : parent.substring(0, colon);
        String name = colon < 0 ? parent : parent.substring(colon + 1);
        if (namespace.equals("minecraft")) return true;

        return bundle.hasResource(new AssetPath(AssetPath.PackKind.CLIENT, namespace,
                "models/" + name + ".json"));
    }

    /**
     * A model that describes its own geometry only needs the parent dropped. One that has
     * nothing but a parent is replaced by the simplest vanilla shape of its kind, keeping
     * whichever texture it named so the block is still recognisable.
     */
    private static JsonObject substituteFor(JsonObject model, AssetPath path) {
        if (model.has("elements")) {
            JsonObject standalone = model.deepCopy();
            standalone.remove("parent");
            return standalone;
        }

        boolean item = path.path().startsWith("models/item/");
        JsonObject substitute = new JsonObject();
        substitute.addProperty("parent", item ? "minecraft:item/generated" : "minecraft:block/cube_all");

        String texture = someTextureOf(model);
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
        JsonElement textures = model.get("textures");
        if (textures == null || !textures.isJsonObject()) return null;
        JsonObject byKey = textures.getAsJsonObject();

        for (String key : TEXTURE_KEYS) {
            String value = stringOrNull(byKey, key);
            // A '#' value points at another key of the parent we just lost.
            if (value != null && !value.startsWith("#")) return value;
        }
        for (String key : byKey.keySet()) {
            String value = stringOrNull(byKey, key);
            if (value != null && !value.startsWith("#")) return value;
        }
        return null;
    }

    @Nullable
    private static String parentOf(JsonObject model) {
        return stringOrNull(model, "parent");
    }

    @Nullable
    private static String stringOrNull(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    @Nullable
    private static JsonObject read(AssetBundle bundle, AssetPath path) {
        try {
            byte[] data = bundle.readResource(path);
            return data == null ? null : Json.parse(new String(data, StandardCharsets.UTF_8));
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Could not read {}", path, e);
            return null;
        }
    }
}
