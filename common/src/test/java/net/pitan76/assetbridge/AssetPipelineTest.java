package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.asset.BridgedItemDefinition;
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
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json",
                "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"examplemod:block/foo\"}}",
                "assets/examplemod/textures/block/foo.png", "png bytes"
        )));

        assertEquals(1, assets.blocks().size());
        BridgedBlockDefinition block = assets.blocks().get(0);
        assertEquals("examplemod:foo", block.id());
        assertEquals("examplemod:block/foo", block.modelId());
        assertEquals("example-mod.jar", block.sourceArchive());
        assertEquals(AssetVersion.MODERN, block.version());
    }

    @Test
    void qualifiesModelReferencesWithTheirNamespace() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"block/foo\"}}}"
        )));

        assertEquals("examplemod:block/foo", assets.blocks().get(0).modelId());
    }

    @Test
    void passesTheBlockStateThroughAndRecoversItsProperties() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north\": {\"model\": \"examplemod:block/north\", \"y\": 90},"
                        + " \"facing=south\": {\"model\": \"examplemod:block/south\"}}}"
        )));

        JsonObject served = json(assets, AssetPath.blockState("examplemod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        // Every variant survives, rotations included, so the block renders like the original.
        assertEquals(Set.of("facing=north", "facing=south"), variants.keySet());
        assertEquals(90, variants.getAsJsonObject("facing=north").get("y").getAsInt());

        BridgedStateDefinition states = assets.blocks().get(0).states();
        assertEquals(List.of("facing"), states.properties().stream().map(BridgedProperty::name).toList());
        assertEquals(List.of("north", "south"), states.properties().get(0).values());
    }

    @Test
    void fallsBackToASingleVariantWhenAPropertyCannotBeRegistered() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north-east\": {\"model\": \"examplemod:block/foo\"}}}"
        )));

        // Serving the original would make the model loader fail on the unknown property.
        JsonObject served = json(assets, AssetPath.blockState("examplemod", "foo"));
        assertEquals(Set.of(""), served.getAsJsonObject("variants").keySet());
        assertEquals("examplemod:block/foo",
                served.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertTrue(assets.blocks().get(0).states().isEmpty());
    }

    @Test
    void rewritesLegacyBlockStatesSoTheyCanBePassedThrough() {
        BridgedAssetManager assets = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, Map.of(
                "assets/oldmod/blockstates/foo.json", "{\"variants\": {\"normal\": {\"model\": \"cube_all\"}}}"
        )));

        JsonObject served = json(assets, AssetPath.blockState("oldmod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        assertEquals(Set.of(""), variants.keySet());
        assertEquals("block/cube_all", variants.getAsJsonObject("").get("model").getAsString());
        assertTrue(assets.blocks().get(0).states().isEmpty());
    }

    @Test
    void generatesAnItemModelOnlyWhenTheArchiveHasNone() {
        BridgedAssetManager generated = build(TestArchives.archive("a.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json", "{}"
        )));
        assertEquals("examplemod:block/foo",
                json(generated, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());

        BridgedAssetManager shipped = build(TestArchives.archive("b.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json", "{}",
                "assets/examplemod/models/block/custom_item.json", "{}",
                "assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/custom_item\"}"
        )));
        assertEquals("examplemod:block/custom_item",
                json(shipped, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());
    }

    @Test
    void appliesTheConversionForTheArchivesOwnVersion() {
        // Pre-flattening assets, so 'blocks/' has to become 'block/'.
        BridgedAssetManager assets = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, Map.of(
                "assets/oldmod/blockstates/foo.json",
                "{\"variants\": {\"normal\": {\"model\": \"oldmod:blocks/foo\"}}}",
                "assets/oldmod/models/block/foo.json",
                "{\"parent\": \"blocks/cube_all\", \"textures\": {\"all\": \"oldmod:blocks/foo\"}}"
        )));

        JsonObject model = json(assets, new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "models/block/foo.json"));
        assertEquals("block/cube_all", model.get("parent").getAsString());
        assertEquals(AssetVersion.LEGACY, assets.blocks().get(0).version());
    }

    @Test
    void fixesLegacyShapedContentRegardlessOfTheDeclaredVersion() {
        // A mod that declares a current version can still ship a pre-flattening model, and
        // 'blocks/' simply does not resolve here, so the fix keys off the content.
        BridgedAssetManager assets = build(
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
                    json(assets, new AssetPath(AssetPath.PackKind.CLIENT, namespace, "models/block/" + model + ".json"))
                            .get("parent").getAsString(), namespace);
        }
    }

    @Test
    void recordsEachArchivesOwnVersion() {
        BridgedAssetManager assets = build(
                TestArchives.archive("old.jar", AssetVersion.LEGACY, Map.of(
                        "assets/oldmod/blockstates/foo.json",
                        "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}")),
                TestArchives.archive("new.jar", AssetVersion.ATLASES, Map.of(
                        "assets/newmod/blockstates/bar.json",
                        "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}")));

        assertEquals(List.of(AssetVersion.LEGACY, AssetVersion.ATLASES),
                assets.blocks().stream().map(BridgedBlockDefinition::version).toList());
    }

    @Test
    void skipsNamespacesThatALoadedModAlreadyOwns() {
        BridgedAssetManager assets = AssetPipeline.build(List.of(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/block/foo.json", "{\"parent\": \"block/cube_all\"}",
                "assets/othermod/blockstates/bar.json",
                "{\"variants\": {\"\": {\"model\": \"othermod:block/bar\"}}}"
        ))), "examplemod"::equals);

        // Neither the block nor its resources may reach the assets, or the real mod's
        // appearance would be overridden by ours.
        assertEquals(List.of("othermod:bar"), assets.blocks().stream().map(BridgedBlockDefinition::id).toList());
        assertTrue(assets.resources().keySet().stream().noneMatch(path -> path.namespace().equals("examplemod")));
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateBlockIds() {
        BridgedAssetManager assets = build(
                TestArchives.archive("first.jar", AssetVersion.MODERN, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/first\"}}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Map.of("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/second\"}}}")));

        assertEquals(1, assets.blocks().size());
        assertEquals("first.jar", assets.blocks().get(0).sourceArchive());
    }

    @Test
    void skipsBlockStatesItCannotUse() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json",
                "assets/examplemod/blockstates/modelless.json", "{\"variants\": {}}",
                "assets/examplemod/blockstates/fine.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/fine\"}}}"
        )));

        assertEquals(List.of("examplemod:fine"), assets.blocks().stream().map(BridgedBlockDefinition::id).toList());
    }

    @Test
    void registersItemModelsThatNoBlockClaims() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/models/item/wand.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/wand\"}}",
                "assets/examplemod/textures/item/wand.png", "png bytes"
        )));

        assertEquals(List.of("examplemod:wand"), assets.items().stream().map(BridgedItemDefinition::id).toList());
        assertEquals("example-mod.jar", assets.items().get(0).sourceArchive());
    }

    @Test
    void doesNotRegisterAStandaloneItemForABridgedBlock() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}",
                "assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/foo\"}",
                "assets/examplemod/models/item/wand.json", "{\"parent\": \"item/generated\"}"
        )));

        // 'foo' is already covered by the block's own BlockItem.
        assertEquals(List.of("examplemod:wand"), assets.items().stream().map(BridgedItemDefinition::id).toList());
    }

    @Test
    void prefersItemDefinitionsOverGuessingFromItemModels() {
        BridgedAssetManager assets = build(TestArchives.archive("new-mod.jar", AssetVersion.ATLASES, Map.of(
                // 1.21.4+ ships an authoritative list of items; the item models next to it
                // include shared fragments and block items that are not separate items.
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}",
                "assets/newmod/models/item/wand.json", "{\"parent\": \"item/generated\"}",
                "assets/newmod/models/item/leftover.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of("newmod:wand"), assets.items().stream().map(BridgedItemDefinition::id).toList());
    }

    @Test
    void doesNotServeItemDefinitionsThisVersionCannotRead() {
        BridgedAssetManager assets = build(TestArchives.archive("new-mod.jar", AssetVersion.ATLASES, Map.of(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}"
        )));

        assertFalse(assets.hasResource(new AssetPath(AssetPath.PackKind.CLIENT, "newmod", "items/wand.json")));
    }

    @Test
    void stillUsesItemModelsForNamespacesWithoutDefinitions() {
        BridgedAssetManager assets = build(TestArchives.archive("mixed.jar", AssetVersion.ATLASES, Map.of(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}",
                "assets/oldmod/models/item/gem.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of("newmod:wand", "oldmod:gem"),
                assets.items().stream().map(BridgedItemDefinition::id).sorted().toList());
    }

    @Test
    void ignoresNestedItemModelFragments() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/models/item/parts/handle.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(List.of(), assets.items());
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateItemIds() {
        BridgedAssetManager assets = build(
                TestArchives.archive("first.jar", AssetVersion.MODERN, Map.of("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/first\"}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Map.of("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/second\"}}")));

        assertEquals(1, assets.items().size());
        assertEquals("first.jar", assets.items().get(0).sourceArchive());
        assertEquals("examplemod:item/first",
                json(assets, new AssetPath(AssetPath.PackKind.CLIENT, "examplemod", "models/item/wand.json"))
                        .getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void producesNothingForAnEmptyDirectory() {
        assertTrue(AssetPipeline.build(List.of(), NOTHING_LOADED).isEmpty());
    }

    @Test
    void doesNotCarryOriginalBlockStatesIntoTheBundle() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/broken.json", "{ not json"
        )));

        // A blockstate we could not use must not be served either.
        assertFalse(assets.hasResource(AssetPath.blockState("examplemod", "broken")));
    }

    @Test
    void replacesAGeneratedItemModelWhoseBlockModelNeverArrived() {
        // The blockstate names a model the archive does not contain, so the item model
        // generated from it would inherit from nothing.
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}"
        )));

        assertEquals("minecraft:item/generated",
                json(assets, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());
    }

    @Test
    void bridgesAnimationMetadataByteForByte() {
        // A .mcmeta is what makes a texture animate. Nothing in it is version specific, so
        // it must reach the pack exactly as the archive wrote it.
        String mcmeta = "{\"animation\": {\"frametime\": 2, \"frames\": [0, 1, 2, 1]}}";
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Map.of(
                "assets/examplemod/textures/block/foo.png", "png bytes",
                "assets/examplemod/textures/block/foo.png.mcmeta", mcmeta
        )));

        AssetPath path = new AssetPath(AssetPath.PackKind.CLIENT, "examplemod",
                "textures/block/foo.png.mcmeta");
        byte[] served = assertDoesNotThrow(() -> assets.readResource(path));
        assertNotNull(served, "missing resource: " + path);
        assertEquals(mcmeta, new String(served, StandardCharsets.UTF_8));
    }

    private static BridgedAssetManager build(AssetArchive... archives) {
        return AssetPipeline.build(List.of(archives), NOTHING_LOADED);
    }

    private static JsonObject json(BridgedAssetManager assets, AssetPath path) {
        byte[] data = assertDoesNotThrow(() -> assets.readResource(path));
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
