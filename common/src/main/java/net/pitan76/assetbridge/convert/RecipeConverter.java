package net.pitan76.assetbridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;

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
 * <p>What survives is passed through unchanged (unless conversion is required). Whether the
 * items it names exist is not checked, and cannot be: the registries are not filled at this point.
 */
public class RecipeConverter implements AssetConverter {
    private static final Set<String> SUPPORTED_TYPES;

    static {
        //? if >=1.20 {
        /*SUPPORTED_TYPES = Set.of(
                "minecraft:crafting_shaped",
                "minecraft:crafting_shapeless",
                "minecraft:smelting",
                "minecraft:blasting",
                "minecraft:smoking",
                "minecraft:campfire_cooking",
                "minecraft:stonecutting",
                "minecraft:smithing_transform",
                "minecraft:smithing_trim"
        );
        *///?} else {
        SUPPORTED_TYPES = Set.of(
                "minecraft:crafting_shaped",
                "minecraft:crafting_shapeless",
                "minecraft:smelting",
                "minecraft:blasting",
                "minecraft:smoking",
                "minecraft:campfire_cooking",
                "minecraft:stonecutting",
                "minecraft:smithing"
        );
        //?}
    }

    @Override
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        if (from.resolved() == AssetVersion.LEGACY) return null;

        JsonObject json = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (json == null) return null;

        if (containsFutureVanillaItems(json)) return null;

        // レシピ結果 (result) の 1.20.5+ 形式 ("id") と古い形式 ("item") の相互変換
        if (json.has("result")) {
            JsonElement resultEl = json.get("result");
            if (resultEl.isJsonObject()) {
                JsonObject resultObj = resultEl.getAsJsonObject();
                boolean isModern = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
                if (isModern) {
                    if (resultObj.has("item") && !resultObj.has("id")) {
                        resultObj.add("id", resultObj.get("item"));
                        resultObj.remove("item");
                    }
                } else {
                    if (resultObj.has("id") && !resultObj.has("item")) {
                        resultObj.add("item", resultObj.get("id"));
                        resultObj.remove("id");
                    }
                }
            }
        }

        // 製錬系レシピ結果のオブジェクトから文字列への平坦化 (1.19.2以前向け)
        if (json.has("result") && json.has("type")) {
            JsonElement typeEl = json.get("type");
            if (typeEl != null && typeEl.isJsonPrimitive()) {
                String rType = qualify(typeEl.getAsString());
                Set<String> cookingTypes = Set.of(
                        "minecraft:smelting",
                        "minecraft:blasting",
                        "minecraft:smoking",
                        "minecraft:campfire_cooking"
                );
                if (cookingTypes.contains(rType)) {
                    boolean isLegacyCookingResult = !net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.ATLASES);
                    if (isLegacyCookingResult) {
                        JsonElement resultEl = json.get("result");
                        if (resultEl.isJsonObject()) {
                            JsonObject resultObj = resultEl.getAsJsonObject();
                            String item = null;
                            if (resultObj.has("item")) {
                                item = resultObj.get("item").getAsString();
                            } else if (resultObj.has("id")) {
                                item = resultObj.get("id").getAsString();
                            }
                            if (item != null) {
                                json.addProperty("result", item);
                            }
                        }
                    }
                }
            }
        }

        // プラットフォームに応じたタグの変換
        convertTags(json, isFabric());
        data = Json.toString(json).getBytes(StandardCharsets.UTF_8);

        JsonElement typeEl = json.get("type");
        if (typeEl == null || !typeEl.isJsonPrimitive()) return null;
        String type = qualify(typeEl.getAsString());

        // 動作環境のバージョンに応じた JSON の相互変換
        //? if >=1.20 {
        /*
        if (type.equals("minecraft:smithing")) {
            // 古い smithing レシピを、1.20+ 用 of smithing_transform レシピに変換
            json.addProperty("type", "minecraft:smithing_transform");
            if (!json.has("template")) {
                JsonObject template = new JsonObject();
                template.addProperty("item", "minecraft:air"); // 必須の template にダミー値を割り当て
                json.add("template", template);
            }
            data = Json.toString(json).getBytes(StandardCharsets.UTF_8);
            type = "minecraft:smithing_transform";
        }
        */
        //?} else {
        if (type.equals("minecraft:smithing_transform")) {
            // 新しい smithing_transform レシピを、1.19.2以前の古い smithing レシピに変換
            json.addProperty("type", "minecraft:smithing");
            json.remove("template"); // template キーは古いバージョンでは不正なため削除
            data = Json.toString(json).getBytes(StandardCharsets.UTF_8);
            type = "minecraft:smithing";
        }
        //?}

        return SUPPORTED_TYPES.contains(type) ? data : null;
    }

    public static boolean isFabric() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void convertTags(JsonElement element, boolean toFabric) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (key.equals("tag") && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String tag = value.getAsString();
                    obj.addProperty("tag", mapTagName(tag, toFabric));
                } else {
                    convertTags(value, toFabric);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                convertTags(child, toFabric);
            }
        }
    }

    public static String mapTagName(String tag, boolean toFabric) {
        if (toFabric) {
            if (tag.startsWith("forge:")) {
                String path = tag.substring(6);
                int slashIdx = path.indexOf('/');
                if (slashIdx > 0 && slashIdx < path.length() - 1) {
                    String category = path.substring(0, slashIdx);
                    String material = path.substring(slashIdx + 1);
                    return "c:" + material + "_" + category;
                }
                return "c:" + path;
            }
        } else {
            if (tag.startsWith("c:")) {
                String path = tag.substring(2);
                String[] categories = {"ingots", "ores", "gems", "dusts", "nuggets", "rods", "crops", "seeds", "slices"};
                for (String cat : categories) {
                    if (path.endsWith("_" + cat) && path.length() > cat.length() + 1) {
                        String material = path.substring(0, path.length() - cat.length() - 1);
                        return "forge:" + cat + "/" + material;
                    }
                }
                return "forge:" + path;
            }
        }
        return tag;
    }

    private static boolean containsFutureVanillaItems(JsonObject json) {
        String jsonStr = Json.toString(json);
        //? if <=1.18 {
        if (jsonStr.contains("minecraft:mangrove") ||
            jsonStr.contains("minecraft:mud") ||
            jsonStr.contains("minecraft:echo_shard") ||
            jsonStr.contains("minecraft:recovery_compass") ||
            jsonStr.contains("minecraft:reinforced_deepslate")) {
            return true;
        }
        //?}
        //? if <=1.19 {
        if (jsonStr.contains("minecraft:cherry") ||
            jsonStr.contains("minecraft:bamboo_planks") ||
            jsonStr.contains("minecraft:bamboo_mosaic") ||
            jsonStr.contains("minecraft:smithing_template") ||
            jsonStr.contains("minecraft:pink_petals") ||
            jsonStr.contains("minecraft:sniffer")) {
            return true;
        }
        //?}
        //? if <=1.20 {
        if (jsonStr.contains("minecraft:crafter") ||
            jsonStr.contains("minecraft:trial_key") ||
            jsonStr.contains("minecraft:mace") ||
            jsonStr.contains("minecraft:breeze_rod") ||
            jsonStr.contains("minecraft:copper_bulb") ||
            jsonStr.contains("minecraft:copper_grate")) {
            return true;
        }
        //?}
        return false;
    }

    private static String qualify(String type) {
        return type.indexOf(':') < 0 ? "minecraft:" + type : type;
    }
}
