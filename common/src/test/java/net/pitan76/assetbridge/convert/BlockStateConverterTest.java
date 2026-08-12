package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlockStateConverterTest {
    private static final AssetPath PATH = AssetPath.blockState("oldmod", "foo");

    private final BlockStateConverter converter = new BlockStateConverter();

    @Test
    void rewritesThePre113NormalVariantKey() {
        JsonObject result = convert("\n                {\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}", AssetVersion.LEGACY);

        assertEquals(new HashSet<>(Arrays.asList("")), keysOf(result.getAsJsonObject("variants")));
    }

    @Test
    void keepsOtherVariantKeysWhenRewritingNormal() {
        JsonObject result = convert("\n                {\"variants\": {\"normal\": {\"model\": \"oldmod:block/a\"}, \"facing=north\": {\"model\": \"oldmod:block/b\"}}}",
                AssetVersion.LEGACY);

        assertEquals(new HashSet<>(Arrays.asList("", "facing=north")), keysOf(result.getAsJsonObject("variants")));
    }

    @Test
    void qualifiesBareModelReferences() {
        // Pre-1.13 blockstate model paths are relative to models/block/.
        JsonObject result = convert("\n                {\"variants\": {\"normal\": {\"model\": \"cube_all\"}}}", AssetVersion.LEGACY);

        assertEquals("block/cube_all",
                result.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void qualifiesModelReferencesInsideWeightedVariantsAndMultipart() {
        JsonObject variants = convert("\n                {\"variants\": {\"normal\": [{\"model\": \"a\"}, {\"model\": \"b\"}]}}", AssetVersion.LEGACY);
        assertEquals("block/a", variants.getAsJsonObject("variants").getAsJsonArray("")
                .get(0).getAsJsonObject().get("model").getAsString());

        JsonObject multipart = convert("\n                {\"multipart\": [{\"when\": {\"north\": \"true\"}, \"apply\": {\"model\": \"side\"}}]}", AssetVersion.LEGACY);
        assertEquals("block/side", multipart.getAsJsonArray("multipart").get(0).getAsJsonObject()
                .getAsJsonObject("apply").get("model").getAsString());
    }

    @Test
    void leavesAlreadyQualifiedReferencesAlone() {
        JsonObject result = convert("\n                {\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}", AssetVersion.LEGACY);

        assertEquals("oldmod:block/foo",
                result.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void touchesNothingOnCurrentVersionAssets() {
        byte[] data = "{\"variants\": {\"\": {\"model\": \"newmod:block/foo\"}}}".getBytes(StandardCharsets.UTF_8);

        assertSame(data, converter.convert(PATH, data, AssetVersion.MODERN));
        assertSame(data, converter.convert(PATH, data, AssetVersion.ATLASES));
    }

    @Test
    void returnsTheOriginalWhenALegacyFileNeedsNoChange() {
        byte[] data = "{\"variants\": {\"facing=north\": {\"model\": \"oldmod:block/foo\"}}}"
                .getBytes(StandardCharsets.UTF_8);

        assertSame(data, converter.convert(PATH, data, AssetVersion.LEGACY));
    }

    @Test
    void keepsUsableVariantWeights() {
        byte[] data = "{\"variants\": {\"\": [{\"model\": \"m:block/a\", \"weight\": 3}, {\"model\": \"m:block/b\"}]}}"
                .getBytes(StandardCharsets.UTF_8);

        assertSame(data, converter.convert(PATH, data, AssetVersion.LEGACY));
    }

    @Test
    void pullsUnusableWeightsBackToOne() {
        // A weight of zero or below makes the weighted draw fail and the state renders nothing.
        JsonObject result = convert("\n                {\"variants\": {\"\": [{\"model\": \"m:block/a\", \"weight\": 0}, {\"model\": \"m:block/b\", \"weight\": -2}]}}",
                AssetVersion.MODERN);

        JsonArray variants = result.getAsJsonObject("variants").getAsJsonArray("");
        assertEquals(1, variants.get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals(1, variants.get(1).getAsJsonObject().get("weight").getAsInt());
    }

    @Test
    void pullsFractionalAndNonNumericWeightsBackToOne() {
        JsonObject result = convert("\n                {\"multipart\": [{\"apply\": [{\"model\": \"m:block/a\", \"weight\": 1.5},\n                                          {\"model\": \"m:block/b\", \"weight\": \"many\"}]}]}", AssetVersion.MODERN);

        JsonArray applied = result.getAsJsonArray("multipart").get(0).getAsJsonObject().getAsJsonArray("apply");
        assertEquals(1, applied.get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals(1, applied.get(1).getAsJsonObject().get("weight").getAsInt());
    }

    @Test
    void dropsUnparseableBlockStates() {
        assertNull(converter.convert(PATH, "{ broken".getBytes(StandardCharsets.UTF_8), AssetVersion.LEGACY));
    }

    private JsonObject convert(String json, AssetVersion from) {
        byte[] result = converter.convert(PATH, json.getBytes(StandardCharsets.UTF_8), from);
        assertNotNull(result);
        JsonObject parsed = Json.parse(new String(result, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }

    private static Set<String> keysOf(JsonObject obj) {
        Set<String> keys = new java.util.HashSet<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            keys.add(entry.getKey());
        }
        return keys;
    }
}
