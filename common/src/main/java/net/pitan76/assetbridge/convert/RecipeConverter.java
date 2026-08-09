package net.pitan76.assetbridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Decides which of an archive's recipes can be served as-is.
 *
 * <p>A recipe is only useful if the running game can read it, which rules out two things.
 * A modded recipe type ({@code create:mixing} and the like) has no serializer here, so it
 * would only produce a load error. And a 1.12-era recipe is written in a format that no
 * longer exists — item ids carry a metadata number, and the type is not namespaced — which
 * is more than a rename away from the current one.
 *
 * <p>What survives is passed through unchanged. Whether the items it names exist is not
 * checked, and cannot be: the registries are not filled at this point. Minecraft skips such
 * a recipe with a log line of its own, which is the same outcome by a slower route.
 */
public class RecipeConverter implements AssetConverter {
    /** The recipe types 1.18.2 ships a serializer for. {@code crafting_special_*} is left out. */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "minecraft:crafting_shaped",
            "minecraft:crafting_shapeless",
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:stonecutting",
            "minecraft:smithing"
    );

    @Override
    @Nullable
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        if (from.resolved() == AssetVersion.LEGACY) return null;

        JsonObject json = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (json == null) return null;

        JsonElement type = json.get("type");
        if (type == null || !type.isJsonPrimitive()) return null;

        return SUPPORTED_TYPES.contains(qualify(type.getAsString())) ? data : null;
    }

    /** 1.13 and 1.14 recipes often leave the {@code minecraft:} namespace off the type. */
    private static String qualify(String type) {
        return type.indexOf(':') < 0 ? "minecraft:" + type : type;
    }
}
