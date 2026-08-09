package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelReferenceResolverTest {
    @Test
    void leavesAModelWhoseParentIsThere() {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "othermod", "models/block/base.json", "{\"textures\":{\"all\":\"othermod:block/base\"}}");
        put(bundle, "examplemod", "models/block/foo.json", "{\"parent\":\"othermod:block/base\"}");

        assertEquals(0, ModelReferenceResolver.resolve(bundle));
    }

    @Test
    void leavesAModelThatInheritsFromVanilla() {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/block/foo.json",
                "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"examplemod:block/foo\"}}");

        assertEquals(0, ModelReferenceResolver.resolve(bundle));
    }

    @Test
    void replacesABlockModelWhoseParentIsMissing() throws IOException {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"textures\":{\"all\":\"examplemod:block/foo\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(bundle));

        JsonObject model = read(bundle, "examplemod", "models/block/foo.json");
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        // The texture it named is kept, so the block is still recognisable.
        assertEquals("examplemod:block/foo",
                model.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void replacesAnItemModelWithTheItemShape() throws IOException {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/item/wand.json",
                "{\"parent\":\"missingmod:item/base\",\"textures\":{\"layer0\":\"examplemod:item/wand\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(bundle));

        JsonObject model = read(bundle, "examplemod", "models/item/wand.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("examplemod:item/wand",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void onlyDropsTheParentOfAModelThatHasItsOwnGeometry() throws IOException {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"elements\":[{\"from\":[0,0,0]}]}");

        assertEquals(1, ModelReferenceResolver.resolve(bundle));

        JsonObject model = read(bundle, "examplemod", "models/block/foo.json");
        assertFalse(model.has("parent"));
        assertEquals(1, model.getAsJsonArray("elements").size());
    }

    @Test
    void ignoresATextureThatOnlyPointsAtTheLostParent() throws IOException {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"textures\":{\"all\":\"#side\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(bundle));

        // '#side' would have been resolved by the parent that is gone, so it is dropped
        // rather than written out as a texture id.
        assertFalse(read(bundle, "examplemod", "models/block/foo.json").has("textures"));
    }

    @Test
    void repairsEachBrokenLinkOfAChainAtItsOwnNode() throws IOException {
        AssetBundle bundle = new AssetBundle();
        put(bundle, "examplemod", "models/block/middle.json", "{\"parent\":\"missingmod:block/base\"}");
        put(bundle, "examplemod", "models/block/leaf.json", "{\"parent\":\"examplemod:block/middle\"}");

        // Only the middle one is broken; the leaf's parent is present either way.
        assertEquals(1, ModelReferenceResolver.resolve(bundle));

        assertEquals("minecraft:block/cube_all",
                read(bundle, "examplemod", "models/block/middle.json").get("parent").getAsString());
        assertEquals("examplemod:block/middle",
                read(bundle, "examplemod", "models/block/leaf.json").get("parent").getAsString());
    }

    private static void put(AssetBundle bundle, String namespace, String path, String json) {
        bundle.putResource(new AssetPath(AssetPath.PackKind.CLIENT, namespace, path),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject read(AssetBundle bundle, String namespace, String path) throws IOException {
        byte[] data = bundle.readResource(new AssetPath(AssetPath.PackKind.CLIENT, namespace, path));
        assertNotNull(data);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
