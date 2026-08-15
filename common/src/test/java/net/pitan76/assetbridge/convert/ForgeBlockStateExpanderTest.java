package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeBlockStateExpanderTest {
    @Test
    void recognisesTheFormatByItsMarker() {
        assertTrue(ForgeBlockStateExpander.isForgeFormat(parse("{\"forge_marker\": 1, \"variants\": {}}")));
        assertFalse(ForgeBlockStateExpander.isForgeFormat(parse("{\"variants\": {\"\": {\"model\": \"a\"}}}")));
        assertFalse(ForgeBlockStateExpander.isForgeFormat(null));
    }

    @Test
    void expandsEveryPropertyCombination() {
        // The shape a 1.12 door is written in: three properties, no variant key spelled out.
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"variants\": {"
                + "  \"half\": {\"lower\": {\"model\": \"examplemod:block/door_bottom\"},"
                + "             \"upper\": {\"model\": \"examplemod:block/door_top\"}},"
                + "  \"facing\": {\"east\": {}, \"north\": {\"y\": 90}}"
                + "}}");

        JsonObject variants = variants(result);
        assertEquals(4, variants.size());
        assertTrue(variants.has("half=lower,facing=east"));
        assertTrue(variants.has("half=upper,facing=north"));
        assertEquals("examplemod:block/door_top",
                variants.getAsJsonObject("half=upper,facing=north").get("model").getAsString());
        assertEquals(90, variants.getAsJsonObject("half=upper,facing=north").get("y").getAsInt());
        // 'y' belongs to the facing partial only, so the east states must not have inherited it.
        assertFalse(variants.getAsJsonObject("half=lower,facing=east").has("y"));
    }

    @Test
    void mergesDefaultsUnderEveryVariant() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"examplemod:block/plain\", \"uvlock\": true},"
                + "\"variants\": {\"facing\": {\"east\": {}, \"north\": {\"y\": 90}}}}");

        JsonObject variants = variants(result);
        assertEquals(2, variants.size());
        for (String key : new String[]{"facing=east", "facing=north"}) {
            JsonObject variant = variants.getAsJsonObject(key);
            assertEquals("examplemod:block/plain", variant.get("model").getAsString());
            assertTrue(variant.get("uvlock").getAsBoolean());
        }
    }

    /**
     * Retexturing a shared model is the whole reason mods reach for this format, and it is the
     * one thing a vanilla variant cannot express, so it has to become a model of its own.
     */
    @Test
    void turnsPerVariantTexturesIntoDerivedModels() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"variant\": {"
                + "  \"red\": {\"textures\": {\"all\": \"examplemod:blocks/red\"}},"
                + "  \"blue\": {\"textures\": {\"all\": \"examplemod:blocks/blue\"}}}}}");

        JsonObject variants = variants(result);
        assertEquals(2, result.generatedModels().size());

        String redModel = variants.getAsJsonObject("variant=red").get("model").getAsString();
        assertEquals("examplemod:block/foo_ab0", redModel);

        JsonObject generated = result.generatedModels().get("foo_ab0");
        assertNotNull(generated);
        // A bare 'cube_all' is a pre-1.13 reference to models/block/cube_all.
        assertEquals("block/cube_all", generated.get("parent").getAsString());
        assertEquals("examplemod:blocks/red", generated.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void sharesOneDerivedModelBetweenIdenticalTextureSets() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\", \"textures\": {\"all\": \"examplemod:blocks/foo\"}},"
                + "\"variants\": {\"facing\": {\"east\": {}, \"north\": {\"y\": 90}, \"south\": {\"y\": 180}}}}");

        assertEquals(1, result.generatedModels().size());
        JsonObject variants = variants(result);
        assertEquals("examplemod:block/foo_ab0", variants.getAsJsonObject("facing=east").get("model").getAsString());
        assertEquals("examplemod:block/foo_ab0", variants.getAsJsonObject("facing=south").get("model").getAsString());
    }

    @Test
    void readsTheInventoryVariantAsAnItemModel() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {"
                + "  \"normal\": [{}],"
                + "  \"inventory\": [{\"model\": \"builtin/generated\","
                + "                   \"textures\": {\"layer0\": \"examplemod:items/door\"}}]}}");

        JsonObject inventory = result.inventoryModel();
        assertNotNull(inventory);
        assertEquals("examplemod:items/door", inventory.getAsJsonObject("textures").get("layer0").getAsString());
        // 'inventory' is not a block state, so it must not have become one.
        assertFalse(variants(result).has("inventory"));
    }

    @Test
    void readsAnExplicitVariantWrittenAsAnArray() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"normal\": [{}]}}");

        JsonObject variants = variants(result);
        // 'normal' is the pre-1.13 spelling of the property-free state.
        assertTrue(variants.has(""));
        assertEquals("cube_all", variants.getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void fallsBackToDefaultsWhenNoVariantIsDeclared() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"examplemod:block/plain\"}}");

        JsonObject variants = variants(result);
        assertEquals(1, variants.size());
        assertEquals("examplemod:block/plain", variants.getAsJsonObject("").get("model").getAsString());
    }

    /** Forge reads a null model as "render nothing"; a variant that renders nothing has no key. */
    @Test
    void leavesOutAStateWhoseModelWasCleared() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"half\": {\"lower\": {}, \"upper\": {\"model\": null}}}}");

        JsonObject variants = variants(result);
        assertEquals(1, variants.size());
        assertTrue(variants.has("half=lower"));
    }

    @Test
    void dropsTheForgeOnlyKeysItCannotHonour() {
        ForgeBlockStateExpander.Result result = expand("{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\", \"transform\": \"forge:default-block\","
                + "               \"custom\": {\"loader\": \"forge:obj\"}},"
                + "\"variants\": {\"normal\": [{}]}}");

        JsonObject variant = variants(result).getAsJsonObject("");
        assertFalse(variant.has("transform"));
        assertFalse(variant.has("custom"));
    }

    /** Past the cap the product is not worth building; the file falls through to the normal path. */
    @Test
    void refusesToExpandAnUnreasonableProduct() {
        StringBuilder json = new StringBuilder("{\"forge_marker\": 1, \"variants\": {");
        for (int property = 0; property < 13; property++) {
            if (property > 0) json.append(',');
            json.append("\"p").append(property).append("\": {\"a\": {\"model\": \"m\"}, \"b\": {\"model\": \"m\"}}");
        }
        json.append("}}");

        // 2^13 = 8192 states, over the 4096 cap.
        assertEquals(0, variants(expand(json.toString())).size());
    }

    private static ForgeBlockStateExpander.Result expand(String json) {
        JsonObject parsed = parse(json);
        assertNotNull(parsed);
        return ForgeBlockStateExpander.expand(parsed, "examplemod", "foo");
    }

    private static JsonObject variants(ForgeBlockStateExpander.Result result) {
        JsonObject variants = Json.object(result.blockState(), "variants");
        assertNotNull(variants);
        return variants;
    }

    private static JsonObject parse(String json) {
        return json == null ? null : Json.parse(json);
    }

    @Test
    void generatedModelNamesAreStable() {
        // Two runs over the same input must not produce different model files, or a pack
        // would change under the player between launches.
        Map<String, JsonObject> first = expand("{\"forge_marker\": 1, \"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"variant\": {\"a\": {\"textures\": {\"all\": \"x\"}},"
                + "                             \"b\": {\"textures\": {\"all\": \"y\"}}}}}").generatedModels();
        Map<String, JsonObject> second = expand("{\"forge_marker\": 1, \"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"variant\": {\"a\": {\"textures\": {\"all\": \"x\"}},"
                + "                             \"b\": {\"textures\": {\"all\": \"y\"}}}}}").generatedModels();

        assertEquals(first.keySet(), second.keySet());
        assertNull(first.get("foo_ab2"));
    }
}
