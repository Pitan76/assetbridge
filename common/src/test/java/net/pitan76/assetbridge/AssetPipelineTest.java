package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;
import net.pitan76.assetbridge.asset.BridgedItemAsset;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.test.TestArchives;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPipelineTest {
    private static final java.util.function.Predicate<String> NOTHING_LOADED = namespace -> false;

    @Test
    void discoversABlockAndItsResources() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
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
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"block/foo\"}}}"
        )));

        assertEquals("examplemod:block/foo", bundle.blocks().get(0).modelId());
    }

    @Test
    void passesTheBlockStateThroughAndRecoversItsProperties() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
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
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
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
        AssetBundle bundle = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, Map.of(
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
        AssetBundle generated = build(TestArchives.archive("a.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}"
        )));
        assertEquals("examplemod:block/foo",
                json(generated, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());

        AssetBundle shipped = build(TestArchives.archive("b.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/custom_item\"}"
        )));
        assertEquals("examplemod:block/custom_item",
                json(shipped, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());
    }

    @Test
    void appliesTheConversionForTheArchivesOwnVersion() {
        // Pre-flattening assets, so 'blocks/' has to become 'block/'.
        AssetBundle bundle = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, Map.of(
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
    void fixesLegacyShapedContentRegardlessOfTheDeclaredVersion() {
        // A mod that declares a current version can still ship a pre-flattening model, and
        // 'blocks/' simply does not resolve here, so the fix keys off the content.
        AssetBundle bundle = build(
                TestArchives.archive("old.jar", AssetVersion.LEGACY, Map.of(
                        "assets/oldmod/blockstates/foo.json",
                        "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}",
                        "assets/oldmod/models/block/foo.json", "{\"parent\": \"blocks/cube_all\"}")),
                TestArchives.archive("new.jar", AssetVersion.MODERN, Map.of(
                        "assets/newmod/blockstates/bar.json",
                        "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}",
                        "assets/newmod/models/block/bar.json", "{\"parent\": \"blocks/cube_all\"}")));

        for (String namespace : List.of("oldmod", "newmod")) {
            String model = namespace.equals("oldmod") ? "foo" : "bar";
            assertEquals("block/cube_all",
                    json(bundle, new AssetPath(AssetPath.PackKind.CLIENT, namespace, "models/block/" + model + ".json"))
                            .get("parent").getAsString(), namespace);
        }
    }

    @Test
    void recordsEachArchivesOwnVersion() {
        AssetBundle bundle = build(
                TestArchives.archive("old.jar", AssetVersion.LEGACY, Map.of(
                        "assets/oldmod/blockstates/foo.json",
                        "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}")),
                TestArchives.archive("new.jar", AssetVersion.FUTURE, Map.of(
                        "assets/newmod/blockstates/bar.json",
                        "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}")));

        assertEquals(List.of(AssetVersion.LEGACY, AssetVersion.FUTURE),
                bundle.blocks().stream().map(BridgedBlockAsset::version).toList());
    }

    @Test
    void skipsNamespacesThatALoadedModAlreadyOwns() {
        AssetBundle bundle = AssetPipeline.build(List.of(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
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
                TestArchives.archive("first.jar", AssetVersion.MODERN, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/first\"}}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/second\"}}}")));

        assertEquals(1, bundle.blocks().size());
        assertEquals("first.jar", bundle.blocks().get(0).sourceArchive());
    }

    @Test
    void skipsBlockStatesItCannotUse() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json",
                "assets/examplemod/blockstates/modelless.json", "{\"variants\": {}}",
                "assets/examplemod/blockstates/fine.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/fine\"}}}"
        )));

        assertEquals(List.of("examplemod:fine"), bundle.blocks().stream().map(BridgedBlockAsset::id).toList());
    }

    @Test
    void registersItemModelsThatNoBlockClaims() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/models/item/wand.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/wand\"}}",
                "assets/examplemod/textures/item/wand.png", "png bytes"
        )));

        assertEquals(List.of("examplemod:wand"), bundle.items().stream().map(BridgedItemAsset::id).toList());
        assertEquals("example-mod.jar", bundle.items().get(0).sourceArchive());
    }

    @Test
    void doesNotRegisterAStandaloneItemForABridgedBlock() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/foo\"}",
                "assets/examplemod/models/item/wand.json", "{\"parent\": \"item/generated\"}"
        )));

        // 'foo' is already covered by the block's own BlockItem.
        assertEquals(List.of("examplemod:wand"), bundle.items().stream().map(BridgedItemAsset::id).toList());
    }

    @Test
    void prefersItemDefinitionsOverGuessingFromItemModels() {
        AssetBundle bundle = build(TestArchives.archive("new-mod.jar", AssetVersion.FUTURE, Map.of(
                // 1.21.4+ ships an authoritative list of items; the item models next to it
                // include shared fragments and block items that are not separate items.
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}",
                "assets/newmod/models/item/wand.json", "{\"parent\": \"item/generated\"}",
                "assets/newmod/models/item/leftover.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of("newmod:wand"), bundle.items().stream().map(BridgedItemAsset::id).toList());
    }

    @Test
    void doesNotServeItemDefinitionsThisVersionCannotRead() {
        AssetBundle bundle = build(TestArchives.archive("new-mod.jar", AssetVersion.FUTURE, Map.of(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}"
        )));

        assertFalse(bundle.hasResource(new AssetPath(AssetPath.PackKind.CLIENT, "newmod", "items/wand.json")));
    }

    @Test
    void stillUsesItemModelsForNamespacesWithoutDefinitions() {
        AssetBundle bundle = build(TestArchives.archive("mixed.jar", AssetVersion.FUTURE, Map.of(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}",
                "assets/oldmod/models/item/gem.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of("newmod:wand", "oldmod:gem"),
                bundle.items().stream().map(BridgedItemAsset::id).sorted().toList());
    }

    @Test
    void ignoresNestedItemModelFragments() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/models/item/parts/handle.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of(), bundle.items());
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateItemIds() {
        AssetBundle bundle = build(
                TestArchives.archive("first.jar", AssetVersion.MODERN, Map.of("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/first\"}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Map.of("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/second\"}}")));

        assertEquals(1, bundle.items().size());
        assertEquals("first.jar", bundle.items().get(0).sourceArchive());
        assertEquals("examplemod:item/first",
                json(bundle, new AssetPath(AssetPath.PackKind.CLIENT, "examplemod", "models/item/wand.json"))
                        .getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void producesNothingForAnEmptyDirectory() {
        assertTrue(AssetPipeline.build(List.of(), NOTHING_LOADED).isEmpty());
    }

    @Test
    void doesNotCarryOriginalBlockStatesIntoTheBundle() {
        AssetBundle bundle = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json"
        )));

        // A blockstate we could not use must not be served either.
        assertFalse(bundle.hasResource(AssetPath.blockState("examplemod", "broken")));
    }

    private static AssetBundle build(AssetArchive... archives) {
        return AssetPipeline.build(List.of(archives), NOTHING_LOADED);
    }

    private static JsonObject json(AssetBundle bundle, AssetPath path) {
        byte[] data = assertDoesNotThrow(() -> bundle.readResource(path));
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
