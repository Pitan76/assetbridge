package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
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
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "othermod", "models/block/base.json", "{\"textures\":{\"all\":\"othermod:block/base\"}}");
        put(assets, "examplemod", "models/block/foo.json", "{\"parent\":\"othermod:block/base\"}");

        assertEquals(0, ModelReferenceResolver.resolve(assets));
    }

    @Test
    void leavesAModelThatInheritsFromVanilla() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo.json",
                "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"examplemod:block/foo\"}}");

        assertEquals(0, ModelReferenceResolver.resolve(assets));
    }

    @Test
    void replacesABlockModelWhoseParentIsMissing() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"textures\":{\"all\":\"examplemod:block/foo\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(assets));

        JsonObject model = read(assets, "examplemod", "models/block/foo.json");
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        // The texture it named is kept, so the block is still recognisable.
        assertEquals("examplemod:block/foo",
                model.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void replacesAnItemModelWithTheItemShape() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/item/wand.json",
                "{\"parent\":\"missingmod:item/base\",\"textures\":{\"layer0\":\"examplemod:item/wand\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(assets));

        JsonObject model = read(assets, "examplemod", "models/item/wand.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("examplemod:item/wand",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void onlyDropsTheParentOfAModelThatHasItsOwnGeometry() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"elements\":[{\"from\":[0,0,0]}]}");

        assertEquals(1, ModelReferenceResolver.resolve(assets));

        JsonObject model = read(assets, "examplemod", "models/block/foo.json");
        assertFalse(model.has("parent"));
        assertEquals(1, model.getAsJsonArray("elements").size());
    }

    @Test
    void ignoresATextureThatOnlyPointsAtTheLostParent() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo.json",
                "{\"parent\":\"missingmod:block/base\",\"textures\":{\"all\":\"#side\"}}");

        assertEquals(1, ModelReferenceResolver.resolve(assets));

        // '#side' would have been resolved by the parent that is gone, so it is dropped
        // rather than written out as a texture id.
        assertFalse(read(assets, "examplemod", "models/block/foo.json").has("textures"));
    }

    @Test
    void repairsEachBrokenLinkOfAChainAtItsOwnNode() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/middle.json", "{\"parent\":\"missingmod:block/base\"}");
        put(assets, "examplemod", "models/block/leaf.json", "{\"parent\":\"examplemod:block/middle\"}");

        // Only the middle one is broken; the leaf's parent is present either way.
        assertEquals(1, ModelReferenceResolver.resolve(assets));

        assertEquals("minecraft:block/cube_all",
                read(assets, "examplemod", "models/block/middle.json").get("parent").getAsString());
        assertEquals("examplemod:block/middle",
                read(assets, "examplemod", "models/block/leaf.json").get("parent").getAsString());
    }

    @Test
    void leavesABlockStateWhoseModelsAreThere() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo.json", "{\"parent\":\"block/cube_all\"}");
        put(assets, "examplemod", "blockstates/foo.json",
                "{\"variants\":{\"\":{\"model\":\"examplemod:block/foo\"}}}");

        assertEquals(0, ModelReferenceResolver.resolveBlockStates(assets));
    }

    /**
     * A block the archive genuinely cannot draw &mdash; a Forge fluid, whose model, textures and
     * all live in a loader we do not have &mdash; is left saying so. An untextured cube renders
     * exactly as magenta as the missing model while claiming to have loaded, and costs a warning
     * per face on the way.
     */
    @Test
    void leavesAVariantAloneWhenTheArchiveOffersNoStandIn() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "blockstates/foo.json",
                "{\"variants\":{\"\":{\"model\":\"forge:block/fluid\"}}}");

        assertEquals(0, ModelReferenceResolver.resolveBlockStates(assets));
        assertEquals("forge:block/fluid", modelOfEmptyVariant(assets));
    }

    /** The block's own working model keeps the family look where only some variants broke. */
    @Test
    void prefersAWorkingModelFromTheSameBlockState() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/foo_open.json", "{\"parent\":\"block/cube_all\"}");
        put(assets, "examplemod", "blockstates/foo.json",
                "{\"variants\":{\"open=true\":{\"model\":\"examplemod:block/foo_open\"},"
                        + "\"open=false\":{\"model\":\"missingmod:block/base\"}}}");

        assertEquals(1, ModelReferenceResolver.resolveBlockStates(assets));

        JsonObject variants = read(assets, "examplemod", "blockstates/foo.json").getAsJsonObject("variants");
        assertEquals("examplemod:block/foo_open", variants.getAsJsonObject("open=false").get("model").getAsString());
        assertEquals("examplemod:block/foo_open", variants.getAsJsonObject("open=true").get("model").getAsString());
    }

    /**
     * Looked up, not guessed: a block whose sprite the archive did ship comes out recognisable
     * rather than magenta, and one whose sprite it did not is left to the plain vanilla cube.
     */
    @Test
    void dressesTheStandInWithTheBlocksOwnTextureWhenThereIsOne() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "textures/block/foo.png", "png bytes");
        put(assets, "examplemod", "blockstates/foo.json",
                "{\"variants\":{\"\":{\"model\":\"forge:block/fluid\"}}}");

        assertEquals(1, ModelReferenceResolver.resolveBlockStates(assets));
        assertEquals("examplemod:block/foo", modelOfEmptyVariant(assets));

        JsonObject standIn = read(assets, "examplemod", "models/block/foo.json");
        assertEquals("minecraft:block/cube_all", standIn.get("parent").getAsString());
        assertEquals("examplemod:block/foo", standIn.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void repairsWeightedAndMultipartReferencesToo() throws IOException {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/post.json", "{\"parent\":\"block/cube_all\"}");
        put(assets, "examplemod", "blockstates/fence.json",
                "{\"multipart\":[{\"apply\":{\"model\":\"examplemod:block/post\"}},"
                        + "{\"when\":{\"north\":\"true\"},\"apply\":[{\"model\":\"missingmod:block/side\",\"weight\":3},"
                        + "{\"model\":\"missingmod:block/side2\"}]}]}");

        assertEquals(2, ModelReferenceResolver.resolveBlockStates(assets));

        JsonObject blockState = read(assets, "examplemod", "blockstates/fence.json");
        String repaired = blockState.getAsJsonArray("multipart").get(1).getAsJsonObject()
                .getAsJsonArray("apply").get(0).getAsJsonObject().get("model").getAsString();
        assertEquals("examplemod:block/post", repaired);
    }

    private static String modelOfEmptyVariant(BridgedAssetManager assets) throws IOException {
        return read(assets, "examplemod", "blockstates/foo.json")
                .getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString();
    }

    private static void put(BridgedAssetManager assets, String namespace, String path, String json) {
        assets.putResource(new AssetPath(AssetPath.PackKind.CLIENT, namespace, path),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject read(BridgedAssetManager assets, String namespace, String path) throws IOException {
        byte[] data = assets.readResource(new AssetPath(AssetPath.PackKind.CLIENT, namespace, path));
        assertNotNull(data);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
