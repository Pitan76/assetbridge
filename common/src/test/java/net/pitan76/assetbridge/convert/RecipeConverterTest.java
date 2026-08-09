package net.pitan76.assetbridge.convert;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.junit.jupiter.api.Test;

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

    private static byte[] convert(String recipe, AssetVersion from) {
        return CONVERTER.convert(PATH, recipe.getBytes(StandardCharsets.UTF_8), from);
    }
}
