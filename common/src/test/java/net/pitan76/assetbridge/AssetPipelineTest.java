package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.test.TestArchives;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPipelineTest {
    private static final java.util.function.Predicate<String> NOTHING_LOADED = namespace -> false;

    @Test
    void discoversABlockAndItsResources() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json",
                "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"examplemod:block/foo\"}}",
                "assets/examplemod/textures/block/foo.png", "png bytes"
        )));

        assertEquals(1, bundle.blocks().size());
        BridgedBlockAsset block = bundle.blocks().get(0);
        assertEquals("examplemod:foo", block.id());
        assertEquals("examplemod:block/foo", block.modelId());
        assertEquals("example-mod.jar", block.sourceArchive());
        assertEquals(AssetVersion.MODERN, block.version());
    }

    @Test
    void qualifiesModelReferencesWithTheirNamespace() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"block/foo\"}}}"
        )));

        assertEquals("examplemod:block/foo", bundle.blocks().get(0).modelId());
    }

    @Test
    void passesTheBlockStateThroughAndRecoversItsProperties() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north\": {\"model\": \"examplemod:block/north\", \"y\": 90},"
                        + " \"facing=south\": {\"model\": \"examplemod:block/south\"}}}"
        )));

        JsonObject served = json(bundle, AssetPath.blockState("examplemod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        // Every variant survives, rotations included, so the block renders like the original.
        assertEquals(Set.of("facing=north", "facing=south"), variants.keySet());
        assertEquals(90, variants.getAsJsonObject("facing=north").get("y").getAsInt());

        BridgedStateDefinition states = bundle.blocks().get(0).states();
        assertEquals(List.of("facing"), states.properties().stream().map(BridgedProperty::name).toList());
        assertEquals(List.of("north", "south"), states.properties().get(0).values());
    }

    @Test
    void fallsBackToASingleVariantWhenAPropertyCannotBeRegistered() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north-east\": {\"model\": \"examplemod:block/foo\"}}}"
        )));

        // Serving the original would make the model loader fail on the unknown property.
        JsonObject served = json(bundle, AssetPath.blockState("examplemod", "foo"));
        assertEquals(Set.of(""), served.getAsJsonObject("variants").keySet());
        assertEquals("examplemod:block/foo",
                served.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertTrue(bundle.blocks().get(0).states().isEmpty());
    }

    @Test
    void rewritesLegacyBlockStatesSoTheyCanBePassedThrough() {
        AssetBundle bundle = build(TestArchives.archive("old-mod.jar", 3, Map.of(
                "assets/oldmod/blockstates/foo.json", "{\"variants\": {\"normal\": {\"model\": \"cube_all\"}}}"
        )));

        JsonObject served = json(bundle, AssetPath.blockState("oldmod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        assertEquals(Set.of(""), variants.keySet());
        assertEquals("block/cube_all", variants.getAsJsonObject("").get("model").getAsString());
        assertTrue(bundle.blocks().get(0).states().isEmpty());
    }

    @Test
    void generatesAnItemModelOnlyWhenTheArchiveHasNone() {
        AssetBundle generated = build(TestArchives.archive("a.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}"
        )));
        assertEquals("examplemod:block/foo",
                json(generated, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());

        AssetBundle shipped = build(TestArchives.archive("b.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/custom_item\"}"
        )));
        assertEquals("examplemod:block/custom_item",
                json(shipped, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());
    }

    @Test
    void appliesTheConversionForTheArchivesOwnVersion() {
        // pack_format 3 is pre-flattening, so 'blocks/' has to become 'block/'.
        AssetBundle bundle = build(TestArchives.archive("old-mod.jar", 3, Map.of(
                "assets/oldmod/blockstates/foo.json",
                "{\"variants\": {\"normal\": {\"model\": \"oldmod:blocks/foo\"}}}",
                "assets/oldmod/models/block/foo.json",
                "{\"parent\": \"blocks/cube_all\", \"textures\": {\"all\": \"oldmod:blocks/foo\"}}"
        )));

        JsonObject model = json(bundle, new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "models/block/foo.json"));
        assertEquals("block/cube_all", model.get("parent").getAsString());
        assertEquals(AssetVersion.LEGACY, bundle.blocks().get(0).version());
    }

    @Test
    void keepsArchivesOfDifferentVersionsIndependent() {
        AssetBundle bundle = build(
                TestArchives.archive("old.jar", 3, Map.of(
                        "assets/oldmod/blockstates/foo.json",
                        "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}",
                        "assets/oldmod/models/block/foo.json", "{\"parent\": \"blocks/cube_all\"}")),
                TestArchives.archive("new.jar", 8, Map.of(
                        "assets/newmod/blockstates/bar.json",
                        "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}",
                        "assets/newmod/models/block/bar.json", "{\"parent\": \"blocks/cube_all\"}")));

        // Only the legacy archive gets the directory rename.
        assertEquals("block/cube_all",
                json(bundle, new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "models/block/foo.json"))
                        .get("parent").getAsString());
        assertEquals("blocks/cube_all",
                json(bundle, new AssetPath(AssetPath.PackKind.CLIENT, "newmod", "models/block/bar.json"))
                        .get("parent").getAsString());
    }

    @Test
    void skipsNamespacesThatALoadedModAlreadyOwns() {
        AssetBundle bundle = AssetPipeline.build(List.of(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json", "{\"parent\": \"block/cube_all\"}",
                "assets/othermod/blockstates/bar.json",
                "{\"variants\": {\"\": {\"model\": \"othermod:block/bar\"}}}"
        ))), "examplemod"::equals);

        // Neither the block nor its resources may reach the bundle, or the real mod's
        // appearance would be overridden by ours.
        assertEquals(List.of("othermod:bar"), bundle.blocks().stream().map(BridgedBlockAsset::id).toList());
        assertTrue(bundle.resources().keySet().stream().noneMatch(path -> path.namespace().equals("examplemod")));
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateBlockIds() {
        AssetBundle bundle = build(
                TestArchives.archive("first.jar", 8, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/first\"}}}")),
                TestArchives.archive("second.jar", 8, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/second\"}}}")));

        assertEquals(1, bundle.blocks().size());
        assertEquals("first.jar", bundle.blocks().get(0).sourceArchive());
    }

    @Test
    void skipsBlockStatesItCannotUse() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json",
                "assets/examplemod/blockstates/modelless.json", "{\"variants\": {}}",
                "assets/examplemod/blockstates/fine.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/fine\"}}}"
        )));

        assertEquals(List.of("examplemod:fine"), bundle.blocks().stream().map(BridgedBlockAsset::id).toList());
    }

    @Test
    void producesNothingForAnEmptyDirectory() {
        assertTrue(AssetPipeline.build(List.of(), NOTHING_LOADED).isEmpty());
    }

    @Test
    void doesNotCarryOriginalBlockStatesIntoTheBundle() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", 8, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json"
        )));

        // A blockstate we could not use must not be served either.
        assertFalse(bundle.hasResource(AssetPath.blockState("examplemod", "broken")));
    }

    private static AssetBundle build(AssetArchive... archives) {
        return AssetPipeline.build(List.of(archives), NOTHING_LOADED);
    }

    private static JsonObject json(AssetBundle bundle, AssetPath path) {
        byte[] data = bundle.resources().get(path);
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
