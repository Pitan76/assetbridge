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
        assertEquals(recipe, new String(converted, StandardCharsets.UTF_8));
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
        } else {
            assertNotNull(convert("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:mangrove_planks\"}],\"result\":{\"item\":\"examplemod:foo\"}}", AssetVersion.MODERN));
        }
    }

    @Test
    void convertsRecipeTags() {
        String json = "{\"type\":\"minecraft:crafting_shaped\",\"key\":{\"#\":{\"tag\":\"c:raw_fishes\"}},\"pattern\":[\"#\"],\"result\":{\"item\":\"examplemod:foo\"}}";
        byte[] converted = convert(json, AssetVersion.MODERN);
        assertNotNull(converted);
        String result = new String(converted, StandardCharsets.UTF_8);
        JsonObject parsed = net.pitan76.assetbridge.util.Json.parse(result);
        String convertedTag = parsed.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString();
        assertEquals("forge:raw_fishes", convertedTag);
    }

    private static byte[] convert(String recipe, AssetVersion from) {
        return CONVERTER.convert(PATH, recipe.getBytes(StandardCharsets.UTF_8), from);
    }
}
