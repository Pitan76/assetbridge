package net.pitan76.assetbridge.convert;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecipeConverterTest {
    private static final RecipeConverter CONVERTER = new RecipeConverter();
    private static final AssetPath PATH =
            new AssetPath(AssetPath.PackKind.SERVER, "examplemod", "recipes/foo.json");

    @Test
    void passesAVanillaRecipeThrough() {
        String recipe = "{\"type\":\"minecraft:crafting_shapeless\","
                + "\"ingredients\":[{\"item\":\"examplemod:foo\"}],"
                + "\"result\":{\"item\":\"examplemod:bar\"}}";

        byte[] converted = convert(recipe, AssetVersion.MODERN);

        assertNotNull(converted);
        boolean isModern = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
        String expected = isModern ? "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"examplemod:foo\"}],\"result\":{\"id\":\"examplemod:bar\"}}" : recipe;
        assertEquals(expected, new String(converted, StandardCharsets.UTF_8));
    }

    @Test
    void acceptsATypeWithoutItsNamespace() {
        // 1.13/1.14 recipes commonly leave "minecraft:" off.
        assertNotNull(convert("{\"type\":\"crafting_shaped\"}", AssetVersion.FLATTENED));
    }

    @Test
    void dropsAModdedRecipeType() {
        // No serializer is registered for it, so it would only produce a load error.
        assertNull(convert("{\"type\":\"create:mixing\"}", AssetVersion.MODERN));
    }

    @Test
    void dropsEveryLegacyRecipe() {
        // 1.12 item ids carry a metadata number, which cannot be mapped onto a modern id.
        assertNull(convert("{\"type\":\"crafting_shaped\"}", AssetVersion.LEGACY));
    }

    @Test
    void dropsWhatItCannotRead() {
        assertNull(convert("{ not json", AssetVersion.MODERN));
        assertNull(convert("{\"result\":{\"item\":\"examplemod:bar\"}}", AssetVersion.MODERN));
        assertNull(convert("{\"type\":{\"nested\":true}}", AssetVersion.MODERN));
    }

    private static boolean isModernSmithing() {
        String testJson = "{\"type\":\"minecraft:smithing_transform\"}";
        byte[] res = convert(testJson, AssetVersion.MODERN);
        if (res == null) return false;
        String resStr = new String(res, StandardCharsets.UTF_8);
        return resStr.contains("smithing_transform");
    }

    private static boolean is1_18OrBelow() {
        String testJson = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:mangrove_planks\"}],\"result\":{\"item\":\"examplemod:foo\"}}";
        byte[] res = convert(testJson, AssetVersion.MODERN);
        return res == null;
    }

    @Test
    void convertsSmithingRecipeTypes() {
        if (isModernSmithing()) {
            // 1.20以上（smithing_transform が期待される）
            String oldSmithing = "{\"type\":\"minecraft:smithing\",\"base\":{\"item\":\"minecraft:sword\"},\"addition\":{\"item\":\"minecraft:diamond\"}}";
            byte[] converted = convert(oldSmithing, AssetVersion.MODERN);
            assertNotNull(converted);
            String result = new String(converted, StandardCharsets.UTF_8);
            // type が smithing_transform になり、template が補填されていること
            assertEquals("minecraft:smithing_transform", net.pitan76.assetbridge.util.Json.parse(result).get("type").getAsString());
            assertEquals("minecraft:air", net.pitan76.assetbridge.util.Json.parse(result).getAsJsonObject("template").get("item").getAsString());
        } else {
            // 1.19.2以下（smithing が期待される）
            String newSmithing = "{\"type\":\"minecraft:smithing_transform\",\"template\":{\"item\":\"minecraft:template\"},\"base\":{\"item\":\"minecraft:sword\"},\"addition\":{\"item\":\"minecraft:diamond\"}}";
            byte[] converted = convert(newSmithing, AssetVersion.ATLASES);
            assertNotNull(converted);
            String result = new String(converted, StandardCharsets.UTF_8);
            // type が smithing になり、template が除去されていること
            assertEquals("minecraft:smithing", net.pitan76.assetbridge.util.Json.parse(result).get("type").getAsString());
            org.junit.jupiter.api.Assertions.assertFalse(net.pitan76.assetbridge.util.Json.parse(result).getAsJsonObject().has("template"));
        }
    }

    @Test
    void dropsFutureVanillaItems() {
        if (is1_18OrBelow()) {
            assertNull(convert("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:mangrove_planks\"}],\"result\":{\"item\":\"examplemod:foo\"}}", AssetVersion.MODERN));
            assertNull(convert("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:reinforced_deepslate\"}],\"result\":{\"item\":\"examplemod:foo\"}}", AssetVersion.MODERN));
        } else {
            assertNotNull(convert("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:mangrove_planks\"}],\"result\":{\"item\":\"examplemod:foo\"}}", AssetVersion.MODERN));
            assertNotNull(convert("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:reinforced_deepslate\"}],\"result\":{\"item\":\"examplemod:foo\"}}", AssetVersion.MODERN));
        }
    }

    @Test
    void convertsRecipeTags() {
        if (RecipeConverter.isFabric()) {
            // Fabric環境用の変換テスト (forge -> c)
            // forge:ingots/iron -> c:iron_ingots
            String json1 = "{\"type\":\"minecraft:crafting_shaped\",\"key\":{\"#\":{\"tag\":\"forge:ingots/iron\"}},\"pattern\":[\"#\"],\"result\":{\"item\":\"examplemod:foo\"}}";
            byte[] converted1 = convert(json1, AssetVersion.MODERN);
            assertNotNull(converted1);
            String result1 = new String(converted1, StandardCharsets.UTF_8);
            JsonObject parsed1 = net.pitan76.assetbridge.util.Json.parse(result1);
            assertEquals("c:iron_ingots", parsed1.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString());

            // forge:slices/raw_fishes -> c:raw_fishes_slices
            String json2 = "{\"type\":\"minecraft:crafting_shaped\",\"key\":{\"#\":{\"tag\":\"forge:slices/raw_fishes\"}},\"pattern\":[\"#\"],\"result\":{\"item\":\"examplemod:foo\"}}";
            byte[] converted2 = convert(json2, AssetVersion.MODERN);
            assertNotNull(converted2);
            String result2 = new String(converted2, StandardCharsets.UTF_8);
            JsonObject parsed2 = net.pitan76.assetbridge.util.Json.parse(result2);
            assertEquals("c:raw_fishes_slices", parsed2.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString());
        } else {
            // Forge環境用の変換テスト (c -> forge)
            // c:iron_ingots -> forge:ingots/iron
            String json1 = "{\"type\":\"minecraft:crafting_shaped\",\"key\":{\"#\":{\"tag\":\"c:iron_ingots\"}},\"pattern\":[\"#\"],\"result\":{\"item\":\"examplemod:foo\"}}";
            byte[] converted1 = convert(json1, AssetVersion.MODERN);
            assertNotNull(converted1);
            String result1 = new String(converted1, StandardCharsets.UTF_8);
            JsonObject parsed1 = net.pitan76.assetbridge.util.Json.parse(result1);
            assertEquals("forge:ingots/iron", parsed1.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString());

            // c:raw_fishes_slices -> forge:slices/raw_fishes
            String json2 = "{\"type\":\"minecraft:crafting_shaped\",\"key\":{\"#\":{\"tag\":\"c:raw_fishes_slices\"}},\"pattern\":[\"#\"],\"result\":{\"item\":\"examplemod:foo\"}}";
            byte[] converted2 = convert(json2, AssetVersion.MODERN);
            assertNotNull(converted2);
            String result2 = new String(converted2, StandardCharsets.UTF_8);
            JsonObject parsed2 = net.pitan76.assetbridge.util.Json.parse(result2);
            assertEquals("forge:slices/raw_fishes", parsed2.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString());
        }
    }

    @Test
    void convertsRecipeResultIdAndItem() {
        boolean isModern = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);

        if (isModern) {
            // 1.20.5+ 環境: item -> id への変換を検証
            String json = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"item\":\"examplemod:foo\",\"count\":4}}";
            byte[] converted = convert(json, AssetVersion.MODERN);
            assertNotNull(converted);
            String result = new String(converted, StandardCharsets.UTF_8);
            JsonObject parsed = net.pitan76.assetbridge.util.Json.parse(result);
            assertEquals("examplemod:foo", parsed.getAsJsonObject("result").get("id").getAsString());
        } else {
            // 1.20.1- 環境: id -> item への変換を検証
            String json = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"id\":\"examplemod:foo\",\"count\":4}}";
            byte[] converted = convert(json, AssetVersion.MODERN);
            assertNotNull(converted);
            String result = new String(converted, StandardCharsets.UTF_8);
            JsonObject parsed = net.pitan76.assetbridge.util.Json.parse(result);
            assertEquals("examplemod:foo", parsed.getAsJsonObject("result").get("item").getAsString());
        }
    @Test
    void flattensLegacyCookingRecipeResult() {
        boolean isLegacyCooking = !net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.ATLASES);

        String json = "{\"type\":\"minecraft:blasting\",\"cookingtime\":200,\"experience\":0.0,\"ingredient\":{\"item\":\"minecraft:enchanted_book\"},\"result\":{\"id\":\"astralenchant:enchantment_shard\",\"count\":1}}";
        byte[] converted = convert(json, AssetVersion.MODERN);
        assertNotNull(converted);
        String result = new String(converted, StandardCharsets.UTF_8);
        JsonObject parsed = net.pitan76.assetbridge.util.Json.parse(result);

        if (isLegacyCooking) {
            assertEquals("astralenchant:enchantment_shard", parsed.get("result").getAsString());
        } else {
            boolean isModern = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
            JsonObject resObj = parsed.getAsJsonObject("result");
            if (isModern) {
                assertEquals("astralenchant:enchantment_shard", resObj.get("id").getAsString());
            } else {
                assertEquals("astralenchant:enchantment_shard", resObj.get("item").getAsString());
            }
        }
    }

    private static byte[] convert(String recipe, AssetVersion from) {
        return CONVERTER.convert(PATH, recipe.getBytes(StandardCharsets.UTF_8), from);
    }
}
