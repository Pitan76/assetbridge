package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
        JsonObject result = convert("""
                {"variants": {"normal": {"model": "oldmod:block/foo"}}}""", AssetVersion.LEGACY);

        assertEquals(Set.of(""), result.getAsJsonObject("variants").keySet());
    }

    @Test
    void keepsOtherVariantKeysWhenRewritingNormal() {
        JsonObject result = convert("""
                {"variants": {"normal": {"model": "oldmod:block/a"}, "facing=north": {"model": "oldmod:block/b"}}}""",
                AssetVersion.LEGACY);

        assertEquals(Set.of("", "facing=north"), result.getAsJsonObject("variants").keySet());
    }

    @Test
    void qualifiesBareModelReferences() {
        // Pre-1.13 blockstate model paths are relative to models/block/.
        JsonObject result = convert("""
                {"variants": {"normal": {"model": "cube_all"}}}""", AssetVersion.LEGACY);

        assertEquals("block/cube_all",
                result.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void qualifiesModelReferencesInsideWeightedVariantsAndMultipart() {
        JsonObject variants = convert("""
                {"variants": {"normal": [{"model": "a"}, {"model": "b"}]}}""", AssetVersion.LEGACY);
        assertEquals("block/a", variants.getAsJsonObject("variants").getAsJsonArray("")
                .get(0).getAsJsonObject().get("model").getAsString());

        JsonObject multipart = convert("""
                {"multipart": [{"when": {"north": "true"}, "apply": {"model": "side"}}]}""", AssetVersion.LEGACY);
        assertEquals("block/side", multipart.getAsJsonArray("multipart").get(0).getAsJsonObject()
                .getAsJsonObject("apply").get("model").getAsString());
    }

    @Test
    void leavesAlreadyQualifiedReferencesAlone() {
        JsonObject result = convert("""
                {"variants": {"normal": {"model": "oldmod:block/foo"}}}""", AssetVersion.LEGACY);

        assertEquals("oldmod:block/foo",
                result.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void touchesNothingOnCurrentVersionAssets() {
        byte[] data = "{\"variants\": {\"\": {\"model\": \"newmod:block/foo\"}}}".getBytes(StandardCharsets.UTF_8);

        assertSame(data, converter.convert(PATH, data, AssetVersion.MODERN));
        assertSame(data, converter.convert(PATH, data, AssetVersion.FUTURE));
    }

    @Test
    void returnsTheOriginalWhenALegacyFileNeedsNoChange() {
        byte[] data = "{\"variants\": {\"facing=north\": {\"model\": \"oldmod:block/foo\"}}}"
                .getBytes(StandardCharsets.UTF_8);

        assertSame(data, converter.convert(PATH, data, AssetVersion.LEGACY));
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
}
