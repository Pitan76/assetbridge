package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.RuntimePack;
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

    /**
     * A vanilla template a mod names by an id this version no longer has is the one case where
     * trusting the {@code minecraft:} namespace goes wrong: the reference resolves on the
     * version the mod was built for, and here the whole block renders as the missing texture.
     */
    @Test
    void renamesVanillaTemplatesThatWereRenamed() {
        assertEquals("block/slab", parentOf("block/half_slab"));
        assertEquals("block/slab_top", parentOf("block/upper_slab"));
        assertEquals("block/cube_column_horizontal", parentOf("block/column_side"));
        // Explicitly namespaced is the same reference.
        assertEquals("minecraft:block/slab", parentOf("minecraft:block/half_slab"));
    }

    /**
     * 1.20 replaced four door templates with eight. On this build (1.18.2) the four are what
     * exists, so a 1.20+ mod's door has to be pointed back at them; the open states were the
     * opposite hinge plus a rotation the mod's blockstate already carries.
     */
    @Test
    void mapsPost1_20DoorTemplatesBackToTheFourThisVersionHas() {
        if (RuntimePack.resourcePackFormat() < 15) {
            assertEquals("block/door_bottom", parentOf("block/door_bottom_left"));
            assertEquals("block/door_bottom_rh", parentOf("block/door_bottom_left_open"));
            assertEquals("block/door_bottom_rh", parentOf("block/door_bottom_right"));
            assertEquals("block/door_bottom", parentOf("block/door_bottom_right_open"));
            assertEquals("block/door_top", parentOf("block/door_top_left"));
            assertEquals("block/door_top_rh", parentOf("block/door_top_left_open"));
            assertEquals("block/door_top_rh", parentOf("block/door_top_right"));
            assertEquals("block/door_top", parentOf("block/door_top_right_open"));
        } else {
            assertEquals("block/door_bottom_left", parentOf("block/door_bottom_left"));
            assertEquals("block/door_bottom_left_open", parentOf("block/door_bottom_left_open"));
            assertEquals("block/door_bottom_right", parentOf("block/door_bottom_right"));
            assertEquals("block/door_bottom_right_open", parentOf("block/door_bottom_right_open"));
            assertEquals("block/door_top_left", parentOf("block/door_top_left"));
            assertEquals("block/door_top_left_open", parentOf("block/door_top_left_open"));
            assertEquals("block/door_top_right", parentOf("block/door_top_right"));
            assertEquals("block/door_top_right_open", parentOf("block/door_top_right_open"));
        }
    }

    @Test
    void leavesVanillaTemplatesThisVersionStillHasAlone() {
        assertEquals("block/cube_all", parentOf("block/cube_all"));
        assertEquals("block/door_bottom", parentOf("block/door_bottom"));
        assertEquals("builtin/generated", parentOf("builtin/generated"));
        // A mod may have a model of its own by that name; only vanilla's is remapped.
        assertEquals("examplemod:block/half_slab", parentOf("examplemod:block/half_slab"));
    }

    private String parentOf(String parent) {
        return convert("{\"parent\": \"" + parent + "\"}", AssetVersion.LEGACY).get("parent").getAsString();
    }

    /**
     * Mod ids only became lower-case-by-rule in 1.13, so a pre-1.13 archive writes its own name
     * however it likes. 1.13+ rejects the whole model as an invalid resource location for it,
     * and the archive's files were lowercased on the way in anyway.
     */
    @Test
    void lowercasesTheNamespaceOfAReferenceAsWellAsThePath() {
        JsonObject result = convert("{\"parent\": \"BambooMod:block/Foo\","
                + " \"textures\": {\"cross\": \"BambooMod:blocks/Bamboo\"}}", AssetVersion.LEGACY);

        assertEquals("bamboomod:block/foo", result.get("parent").getAsString());
        assertEquals("bamboomod:block/bamboo", result.getAsJsonObject("textures").get("cross").getAsString());
    }

    @Test
    void renamesPreFlatteningDirectories() {
        JsonObject result = convert("\n                {\"parent\": \"blocks/cube_all\", \"textures\": {\"all\": \"examplemod:blocks/foo\"}}",
                AssetVersion.LEGACY);

        assertEquals("block/cube_all", result.get("parent").getAsString());
        // A mod's own sprites are flattened too: the archive's textures/blocks/ is relocated
        // to textures/block/ on the way in, so the reference has to follow it.
        assertEquals("examplemod:block/foo", result.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void leavesModNamespacedParentsAlone() {
        // Model directories were never flattened, so a mod keeping models under
        // models/blocks/ still has them there.
        JsonObject result = convert("\n                {\"parent\": \"examplemod:blocks/base\"}", AssetVersion.LEGACY);

        assertEquals("examplemod:blocks/base", result.get("parent").getAsString());
    }

    @Test
    void renamesLegacyItemDirectories() {
        JsonObject result = convert("\n                {\"parent\": \"items/generated\", \"textures\": {\"layer0\": \"items/foo\"}}",
                AssetVersion.LEGACY);

        assertEquals("item/generated", result.get("parent").getAsString());
        assertEquals("item/foo", result.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void leavesTextureVariableReferencesAlone() {
        // '#all' points at another entry in the same map, not at a file.
        JsonObject result = convert("\n                {\"parent\": \"blocks/cube_all\", \"textures\": {\"side\": \"#all\", \"all\": \"blocks/foo\"}}",
                AssetVersion.LEGACY);

        assertEquals("#all", result.getAsJsonObject("textures").get("side").getAsString());
        assertEquals("block/foo", result.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void stripsKeysThatArePostDatingThisVersion() {
        JsonObject result = convert("\n                {\"parent\": \"block/cube_all\", \"oversized_in_gui\": true}", AssetVersion.ATLASES);

        assertFalse(result.has("oversized_in_gui"));
        assertEquals("block/cube_all", result.get("parent").getAsString());
    }

    @Test
    void returnsTheOriginalBytesWhenNothingChanges() {
        byte[] data = "\n                {\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"examplemod:block/foo\"}}"
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
