package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelConverterTest {
    private static final AssetPath PATH =
            new AssetPath(AssetPath.PackKind.CLIENT, "examplemod", "models/block/foo.json");

    private final ModelConverter converter = new ModelConverter();

    @Test
    void renamesPreFlatteningDirectories() {
        JsonObject result = convert("""
                {"parent": "blocks/cube_all", "textures": {"all": "examplemod:blocks/foo"}}""",
                AssetVersion.LEGACY);

        assertEquals("block/cube_all", result.get("parent").getAsString());
        assertEquals("examplemod:blocks/foo", result.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void renamesLegacyItemDirectories() {
        JsonObject result = convert("""
                {"parent": "items/generated", "textures": {"layer0": "items/foo"}}""",
                AssetVersion.LEGACY);

        assertEquals("item/generated", result.get("parent").getAsString());
        assertEquals("item/foo", result.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void leavesTextureVariableReferencesAlone() {
        // '#all' points at another entry in the same map, not at a file.
        JsonObject result = convert("""
                {"parent": "blocks/cube_all", "textures": {"side": "#all", "all": "blocks/foo"}}""",
                AssetVersion.LEGACY);

        assertEquals("#all", result.getAsJsonObject("textures").get("side").getAsString());
        assertEquals("block/foo", result.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void stripsKeysThatArePostDatingThisVersion() {
        JsonObject result = convert("""
                {"parent": "block/cube_all", "oversized_in_gui": true}""", AssetVersion.ATLASES);

        assertFalse(result.has("oversized_in_gui"));
        assertEquals("block/cube_all", result.get("parent").getAsString());
    }

    @Test
    void returnsTheOriginalBytesWhenNothingChanges() {
        byte[] data = """
                {"parent": "block/cube_all", "textures": {"all": "examplemod:block/foo"}}"""
                .getBytes(StandardCharsets.UTF_8);

        // Same array instance: an untouched resource must not be re-serialised.
        assertSame(data, converter.convert(PATH, data, AssetVersion.MODERN));
        assertSame(data, converter.convert(PATH, data, AssetVersion.LEGACY));
    }

    @Test
    void dropsUnparseableModels() {
        byte[] data = "not json at all {".getBytes(StandardCharsets.UTF_8);

        assertNull(converter.convert(PATH, data, AssetVersion.MODERN));
    }

    private JsonObject convert(String json, AssetVersion from) {
        byte[] result = converter.convert(PATH, json.getBytes(StandardCharsets.UTF_8), from);
        assertNotNull(result);
        JsonObject parsed = Json.parse(new String(result, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
