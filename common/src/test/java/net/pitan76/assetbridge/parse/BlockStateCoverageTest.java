package net.pitan76.assetbridge.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateCoverageTest {
    @Test
    void leavesAFullyCoveredBlockStateAlone() {
        assertNull(complete("""
                {"variants": {"facing=north": {"model": "m:block/n"},
                              "facing=south": {"model": "m:block/s"}}}"""));
    }

    @Test
    void leavesTheEmptyVariantKeyAlone() {
        // The empty key covers every state whatever the properties are.
        assertNull(complete("""
                {"variants": {"": {"model": "m:block/a"}, "facing=north": {"model": "m:block/n"}}}"""));
    }

    @Test
    void fillsInAMissingVariantCombination() {
        JsonObject completed = complete("""
                {"variants": {"facing=north,half=top": {"model": "m:block/a"},
                              "facing=south,half=top": {"model": "m:block/b"},
                              "facing=north,half=bottom": {"model": "m:block/c"}}}""");

        assertNotNull(completed);
        JsonObject variants = completed.getAsJsonObject("variants");
        assertEquals(4, variants.size());
        assertTrue(variants.has("facing=south,half=bottom"));
        assertEquals("m:block/fallback",
                variants.getAsJsonObject("facing=south,half=bottom").get("model").getAsString());
        assertEquals(1, BlockStateCoverage.missingCount(json("""
                {"variants": {"facing=north,half=top": {"model": "m:block/a"},
                              "facing=south,half=top": {"model": "m:block/b"},
                              "facing=north,half=bottom": {"model": "m:block/c"}}}"""), completed));
    }

    @Test
    void treatsAPartialVariantKeyAsCoveringEveryRemainingValue() {
        // "facing=north" alone matches both halves, so only the south states are missing.
        JsonObject completed = complete("""
                {"variants": {"facing=north": {"model": "m:block/a"},
                              "facing=south,half=top": {"model": "m:block/b"}}}""");

        assertNotNull(completed);
        assertEquals(3, completed.getAsJsonObject("variants").size());
        assertTrue(completed.getAsJsonObject("variants").has("facing=south,half=bottom"));
    }

    @Test
    void keepsTheWeightsOfTheFallbackVariant() {
        JsonObject source = json("""
                {"variants": {"facing=north,half=top": [{"model": "m:block/a", "weight": 2},
                                                        {"model": "m:block/b"}]}}""");
        JsonElement fallback = BlockStateParser.findVariant(source);
        assertNotNull(fallback);

        JsonObject completed = BlockStateCoverage.complete(source, definition(), fallback);

        assertNotNull(completed);
        assertEquals(2, completed.getAsJsonObject("variants").getAsJsonArray("facing=south,half=bottom")
                .get(0).getAsJsonObject().get("weight").getAsInt());
    }

    @Test
    void leavesMultipartWithAnUnconditionalPartAlone() {
        assertNull(complete("""
                {"multipart": [{"apply": {"model": "m:block/core"}},
                               {"when": {"facing": "north"}, "apply": {"model": "m:block/n"}}]}"""));
    }

    @Test
    void treatsAlternativeValuesInAMultipartConditionAsCovered() {
        // "north|south" spans every value facing has, so every state matches the first part.
        assertNull(complete("""
                {"multipart": [{"when": {"facing": "north|south"}, "apply": {"model": "m:block/a"}},
                               {"when": {"half": "top"}, "apply": {"model": "m:block/b"}}]}"""));
    }

    @Test
    void appendsPartsOnlyForStatesNoConditionMatches() {
        JsonObject completed = complete("""
                {"multipart": [{"when": {"facing": "north"}, "apply": {"model": "m:block/a"}}]}""");

        assertNotNull(completed);
        // north/top and north/bottom are covered; the two south states are not.
        assertEquals(3, completed.getAsJsonArray("multipart").size());
        JsonObject added = completed.getAsJsonArray("multipart").get(1).getAsJsonObject();
        assertEquals("south", added.getAsJsonObject("when").get("facing").getAsString());
        assertEquals("m:block/fallback", added.getAsJsonObject("apply").get("model").getAsString());
    }

    @Test
    void doesNothingForAPropertyFreeBlock() {
        assertNull(BlockStateCoverage.complete(json("""
                {"variants": {"": {"model": "m:block/a"}}}"""), BridgedStateDefinition.empty(), fallback()));
    }

    /** facing=north|south and half=top|bottom: four states in total. */
    private static BridgedStateDefinition definition() {
        return BlockStatePropertyParser.parse(json("""
                {"variants": {"facing=north,half=top": {}, "facing=south,half=bottom": {}}}"""));
    }

    private static JsonElement fallback() {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", "m:block/fallback");
        return variant;
    }

    private static JsonObject complete(String source) {
        return BlockStateCoverage.complete(json(source), definition(), fallback());
    }

    private static JsonObject json(String source) {
        JsonObject parsed = Json.parse(source);
        assertNotNull(parsed);
        return parsed;
    }
}
