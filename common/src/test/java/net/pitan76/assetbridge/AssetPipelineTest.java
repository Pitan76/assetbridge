package net.pitan76.assetbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.asset.BridgedItemDefinition;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.test.TestArchives;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPipelineTest {
    private static final Predicate<String> NOTHING_LOADED = namespace -> false;

    /**
     * The pipeline asks {@code Features} whether the metadata expansion is on, and nothing is
     * on until a config has been read. In the game {@code AssetBridge.init} reads one before
     * building; here that has to be done by hand, or every feature reads as switched off.
     */
    @BeforeAll
    static void loadDefaultFeatureConfig(@TempDir Path gameDir) {
        Features.loadConfig(gameDir);
    }

    @Test
    void discoversABlockAndItsResources() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        
        resources.put("assets/examplemod/models/block/foo.json",
                "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"examplemod:block/foo\"}}");
        
        resources.put("assets/examplemod/textures/block/foo.png", "png bytes");
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        assertEquals(1, assets.blocks().size());
        BridgedBlockDefinition block = assets.blocks().get(0);
        assertEquals("examplemod:foo", block.id());
        assertEquals("examplemod:block/foo", block.modelId());
        assertEquals("example-mod.jar", block.sourceArchive());
        assertEquals(AssetVersion.MODERN, block.version());
    }

    @Test
    void qualifiesModelReferencesWithTheirNamespace() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Collections.singletonMap(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"block/foo\"}}}"
        )));

        assertEquals("examplemod:block/foo", assets.blocks().get(0).modelId());
    }

    @Test
    void passesTheBlockStateThroughAndRecoversItsProperties() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Collections.singletonMap(
                "assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north\": {\"model\": \"examplemod:block/north\", \"y\": 90},"
                        + " \"facing=south\": {\"model\": \"examplemod:block/south\"}}}"
        )));

        JsonObject served = json(assets, AssetPath.blockState("examplemod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        // Every variant survives, rotations included, so the block renders like the original.
        assertEquals(new HashSet<>(Arrays.asList("facing=north", "facing=south")), keysOf(variants));
        assertEquals(90, variants.getAsJsonObject("facing=north").get("y").getAsInt());

        BridgedStateDefinition states = assets.blocks().get(0).states();
        assertEquals(Arrays.asList("facing"), states.properties().stream().map(BridgedProperty::name).collect(Collectors.toList()));
        assertEquals(Arrays.asList("north", "south"), states.properties().get(0).values());
    }

    @Test
    void fallsBackToASingleVariantWhenAPropertyCannotBeRegistered() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"facing=north-east\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/models/block/foo.json", "{\"parent\": \"block/cube_all\"}");
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        // Serving the original would make the model loader fail on the unknown property.
        JsonObject served = json(assets, AssetPath.blockState("examplemod", "foo"));
        assertEquals(new HashSet<>(Arrays.asList("")), keysOf(served.getAsJsonObject("variants")));
        assertEquals("examplemod:block/foo",
                served.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertTrue(assets.blocks().get(0).states().isEmpty());
    }

    @Test
    void rewritesLegacyBlockStatesSoTheyCanBePassedThrough() {
        BridgedAssetManager assets = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, Collections.singletonMap(
                "assets/oldmod/blockstates/foo.json", "{\"variants\": {\"normal\": {\"model\": \"cube_all\"}}}"
        )));

        JsonObject served = json(assets, AssetPath.blockState("oldmod", "foo"));
        JsonObject variants = served.getAsJsonObject("variants");

        assertEquals(new HashSet<>(Arrays.asList("")), keysOf(variants));
        assertEquals("block/cube_all", variants.getAsJsonObject("").get("model").getAsString());
        assertTrue(assets.blocks().get(0).states().isEmpty());
    }

    @Test
    void generatesAnItemModelOnlyWhenTheArchiveHasNone() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/models/block/foo.json", "{}");
        
        BridgedAssetManager generated = build(TestArchives.archive("a.jar", AssetVersion.MODERN, resources));
        assertEquals("examplemod:block/foo",
                json(generated, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());

        Map<String, String> resources2 = new HashMap<>();
        resources2.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources2.put("assets/examplemod/models/block/foo.json", "{}");
        resources2.put("assets/examplemod/models/block/custom_item.json", "{}");
        resources2.put("assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/custom_item\"}");
        
        BridgedAssetManager shipped = build(TestArchives.archive("b.jar", AssetVersion.MODERN, resources2));
        assertEquals("examplemod:block/custom_item",
                json(shipped, AssetPath.itemModel("examplemod", "foo")).get("parent").getAsString());
    }

    @Test
    void appliesTheConversionForTheArchivesOwnVersion() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/oldmod/blockstates/foo.json",
                "{\"variants\": {\"normal\": {\"model\": \"oldmod:blocks/foo\"}}}");
        resources.put("assets/oldmod/models/block/foo.json",
                "{\"parent\": \"blocks/cube_all\", \"textures\": {\"all\": \"oldmod:blocks/foo\"}}");
        
        // Pre-flattening assets, so 'blocks/' has to become 'block/'.
        BridgedAssetManager assets = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, resources));

        JsonObject model = json(assets, new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "models/block/foo.json"));
        assertEquals("block/cube_all", model.get("parent").getAsString());
        assertEquals(AssetVersion.LEGACY, assets.blocks().get(0).version());
    }

    @Test
    void servesLegacySpritesFromTheFlattenedDirectory() throws Exception {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/oldmod/blockstates/foo.json",
                "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}");
        resources.put("assets/oldmod/models/block/foo.json",
                "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"oldmod:blocks/foo\"}}");
        resources.put("assets/oldmod/textures/blocks/foo.png", "png bytes");
        resources.put("assets/oldmod/textures/blocks/foo.png.mcmeta", "{}");
        resources.put("assets/oldmod/textures/items/wand.png", "png bytes");
        
        // From 1.19.3 the block atlas is defined as the contents of textures/block/, so a
        // sprite left in the plural directory is never stitched and the model renders as
        // missing. Both the file and the reference to it have to move.
        BridgedAssetManager assets = build(TestArchives.archive("old-mod.jar", AssetVersion.LEGACY, resources));

        assertNotNull(assets.readResource(
                new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "textures/block/foo.png")));
        assertNotNull(assets.readResource(
                new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "textures/block/foo.png.mcmeta")));
        assertNotNull(assets.readResource(
                new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "textures/item/wand.png")));

        JsonObject model = json(assets, new AssetPath(AssetPath.PackKind.CLIENT, "oldmod", "models/block/foo.json"));
        assertEquals("oldmod:block/foo", model.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void fixesLegacyShapedContentRegardlessOfTheDeclaredVersion() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/oldmod/blockstates/foo.json",
                "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}");
        resources.put("assets/oldmod/models/block/foo.json",
                "{\"parent\": \"blocks/cube_all\", \"textures\": {\"all\": \"oldmod:blocks/foo\"}}");
        
        Map<String, String> resources2 = new HashMap<>();
        resources2.put("assets/newmod/blockstates/bar.json",
                "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}");
        resources2.put("assets/newmod/models/block/bar.json", "{\"parent\": \"blocks/cube_all\"}");
        
        // A mod that declares a current version can still ship a pre-flattening model, and
        // 'blocks/' simply does not resolve here, so the fix keys off the content.
        BridgedAssetManager assets = build(
                TestArchives.archive("old.jar", AssetVersion.LEGACY, resources),
                TestArchives.archive("new.jar", AssetVersion.MODERN, resources2));

        for (String namespace : Arrays.asList("oldmod", "newmod")) {
            String model = namespace.equals("oldmod") ? "foo" : "bar";
            assertEquals("block/cube_all",
                    json(assets, new AssetPath(AssetPath.PackKind.CLIENT, namespace, "models/block/" + model + ".json"))
                            .get("parent").getAsString(), namespace);
        }
    }

    @Test
    void recordsEachArchivesOwnVersion() {
        BridgedAssetManager assets = build(
                TestArchives.archive("old.jar", AssetVersion.LEGACY, Collections.singletonMap(
                        "assets/oldmod/blockstates/foo.json",
                        "{\"variants\": {\"normal\": {\"model\": \"oldmod:block/foo\"}}}")),
                TestArchives.archive("new.jar", AssetVersion.ATLASES, Collections.singletonMap(
                        "assets/newmod/blockstates/bar.json",
                        "{\"variants\": {\"\": {\"model\": \"newmod:block/bar\"}}}")));

        assertEquals(Arrays.asList(AssetVersion.LEGACY, AssetVersion.ATLASES),
                assets.blocks().stream().map(BridgedBlockDefinition::version).collect(Collectors.toList()));
    }

    @Test
    void skipsNamespacesThatALoadedModAlreadyOwns() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/models/block/foo.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/othermod/blockstates/bar.json",
                "{\"variants\": {\"\": {\"model\": \"othermod:block/bar\"}}}");
        
        BridgedAssetManager assets = AssetPipeline.build(Arrays.asList(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources)), "examplemod"::equals);

        // Neither the block nor its resources may reach the assets, or the real mod's
        // appearance would be overridden by ours.
        assertEquals(Arrays.asList("othermod:bar"), assets.blocks().stream().map(BridgedBlockDefinition::id).collect(Collectors.toList()));
        assertTrue(assets.resources().keySet().stream().noneMatch(path -> path.namespace().equals("examplemod")));
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateBlockIds() {
        BridgedAssetManager assets = build(
                TestArchives.archive("first.jar", AssetVersion.MODERN, Collections.singletonMap("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/first\"}}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Collections.singletonMap("assets/examplemod/blockstates/foo.json",
                        "{\"variants\": {\"\": {\"model\": \"examplemod:block/second\"}}}")));

        assertEquals(1, assets.blocks().size());
        assertEquals("first.jar", assets.blocks().get(0).sourceArchive());
    }

    @Test
    void skipsBlockStatesItCannotUse() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/broken.json", "{ not json");
        resources.put("assets/examplemod/blockstates/modelless.json", "{\"variants\": {}}");
        resources.put("assets/examplemod/blockstates/fine.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/fine\"}}}");
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        assertEquals(Arrays.asList("examplemod:fine"), assets.blocks().stream().map(BridgedBlockDefinition::id).collect(Collectors.toList()));
    }

    @Test
    void registersItemModelsThatNoBlockClaims() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/models/item/wand.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/wand\"}}");
        resources.put("assets/examplemod/textures/item/wand.png", "png bytes");
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        assertEquals(Arrays.asList("examplemod:wand"), assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toList()));
        assertEquals("example-mod.jar", assets.items().get(0).sourceArchive());
    }

    @Test
    void doesNotRegisterAStandaloneItemForABridgedBlock() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/models/block/foo.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/models/item/foo.json", "{\"parent\": \"examplemod:block/foo\"}");
        resources.put("assets/examplemod/models/item/wand.json", "{\"parent\": \"item/generated\"}");
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        // 'foo' is already covered by the block's own BlockItem.
        assertEquals(Arrays.asList("examplemod:wand"), assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toList()));
    }

    @Test
    void prefersItemDefinitionsOverGuessingFromItemModels() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}");
        resources.put("assets/newmod/models/item/wand.json", "{\"parent\": \"item/generated\"}");
        resources.put("assets/newmod/models/item/leftover.json", "{\"parent\": \"item/generated\"}");
        
        BridgedAssetManager assets = build(TestArchives.archive("new-mod.jar", AssetVersion.ATLASES, resources));

        assertEquals(Arrays.asList("newmod:wand"), assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toList()));
    }

    //? if >=26 {
    /*@Test
    void doesNotServeItemDefinitionsThisVersionCannotRead() {
        BridgedAssetManager assets = build(TestArchives.archive("new-mod.jar", AssetVersion.ATLASES, Map.of(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}"
        )));

        assertTrue(assets.hasResource(new AssetPath(AssetPath.PackKind.CLIENT, "newmod", "items/wand.json")));
    }
    *///?} else {
    @Test
    void doesNotServeItemDefinitionsThisVersionCannotRead() {
        BridgedAssetManager assets = build(TestArchives.archive("new-mod.jar", AssetVersion.ATLASES, Collections.singletonMap(
                "assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}"
        )));

        assertFalse(assets.hasResource(new AssetPath(AssetPath.PackKind.CLIENT, "newmod", "items/wand.json")));
    }
    //?}

    @Test
    void stillUsesItemModelsForNamespacesWithoutDefinitions() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/newmod/items/wand.json", "{\"model\": {\"type\": \"minecraft:model\"}}");
        resources.put("assets/oldmod/models/item/gem.json", "{\"parent\": \"item/generated\"}");
        
        BridgedAssetManager assets = build(TestArchives.archive("mixed.jar", AssetVersion.ATLASES, resources));

        assertEquals(Arrays.asList("newmod:wand", "oldmod:gem"),
                assets.items().stream().map(BridgedItemDefinition::id).sorted().collect(Collectors.toList()));
    }

    @Test
    void ignoresNestedItemModelFragments() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Collections.singletonMap(
                "assets/examplemod/models/item/parts/handle.json", "{\"parent\": \"item/generated\"}"
        )));

        assertEquals(Collections.emptyList(), assets.items());
    }

    @Test
    void keepsTheFirstArchiveOnDuplicateItemIds() {
        BridgedAssetManager assets = build(
                TestArchives.archive("first.jar", AssetVersion.MODERN, Collections.singletonMap("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/first\"}}")),
                TestArchives.archive("second.jar", AssetVersion.MODERN, Collections.singletonMap("assets/examplemod/models/item/wand.json",
                        "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:item/second\"}}")));

        assertEquals(1, assets.items().size());
        assertEquals("first.jar", assets.items().get(0).sourceArchive());
        assertEquals("examplemod:item/first",
                json(assets, new AssetPath(AssetPath.PackKind.CLIENT, "examplemod", "models/item/wand.json"))
                        .getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void producesNothingForAnEmptyDirectory() {
        assertTrue(AssetPipeline.build(Collections.emptyList(), NOTHING_LOADED).isEmpty());
    }

    @Test
    void doesNotCarryOriginalBlockStatesIntoTheBundle() {
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Collections.singletonMap(
                "assets/examplemod/blockstates/broken.json", "{ not json"
        )));

        // A blockstate we could not use must not be served either.
        assertFalse(assets.hasResource(AssetPath.blockState("examplemod", "broken")));
    }

    @Test
    void replacesAGeneratedItemModelWhoseBlockModelNeverArrived() {
        // The blockstate names a model the archive does not contain, so the item model
        // generated from it would inherit from nothing.
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, Collections.singletonMap(
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

        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/textures/block/foo.png", "png bytes");
        resources.put("assets/examplemod/textures/block/foo.png.mcmeta", mcmeta);
        
        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, resources));

        AssetPath path = new AssetPath(AssetPath.PackKind.CLIENT, "examplemod",
                "textures/block/foo.png.mcmeta");
        byte[] served = assertDoesNotThrow(() -> assets.readResource(path));
        assertNotNull(served, "missing resource: " + path);
        assertEquals(mcmeta, new String(served, StandardCharsets.UTF_8));
    }

    /**
     * The symptom this covers: a door used to reach the game as an item and never as a block.
     * Mods write doors in Forge's blockstate format, where no {@code model} sits at the depth
     * the parser looks, so the block was dropped for having no model and only its item model
     * survived.
     */
    @Test
    void registersABlockWrittenInTheForgeBlockStateFormat() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/oak_door.json", "{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"textures\": {\"bottom\": \"examplemod:blocks/door_lower\"}},"
                + "\"variants\": {"
                + "  \"half\": {\"lower\": {\"model\": \"examplemod:block/door_bottom\"},"
                + "             \"upper\": {\"model\": \"examplemod:block/door_top\"}},"
                + "  \"facing\": {\"east\": {}, \"north\": {\"y\": 90}}}}");
        resources.put("assets/examplemod/models/block/door_bottom.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/models/block/door_top.json", "{\"parent\": \"block/cube_all\"}");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals(1, assets.blocks().size());
        assertEquals("examplemod:oak_door", assets.blocks().get(0).id());

        BridgedStateDefinition states = assets.blocks().get(0).states();
        assertEquals(new HashSet<>(Arrays.asList("half", "facing")),
                states.properties().stream().map(BridgedProperty::name).collect(Collectors.toSet()));

        JsonObject served = json(assets, AssetPath.blockState("examplemod", "oak_door"));
        JsonObject variants = served.getAsJsonObject("variants");
        assertEquals(4, variants.size());
        assertTrue(variants.has("half=lower,facing=east"));
        assertTrue(variants.has("half=upper,facing=north"));
    }

    /** A Forge variant may retexture its model; a vanilla one cannot, so it needs a model of its own. */
    @Test
    void servesTheModelsAForgeBlockStateNeededSynthesising() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json", "{"
                + "\"forge_marker\": 1,"
                + "\"defaults\": {\"model\": \"cube_all\"},"
                + "\"variants\": {\"variant\": {"
                + "  \"red\": {\"textures\": {\"all\": \"examplemod:blocks/red\"}},"
                + "  \"blue\": {\"textures\": {\"all\": \"examplemod:blocks/blue\"}}}}}");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        String reference = json(assets, AssetPath.blockState("examplemod", "foo"))
                .getAsJsonObject("variants").getAsJsonObject("variant=red").get("model").getAsString();
        assertEquals("examplemod:block/foo_ab0", reference);

        JsonObject generated = json(assets, AssetPath.blockModel("examplemod", "foo_ab0"));
        assertEquals("block/cube_all", generated.get("parent").getAsString());
        // The generated model goes through the same conversion an archive's own model does,
        // so its pre-1.13 texture directory is flattened with everything else.
        assertEquals("examplemod:block/red", generated.getAsJsonObject("textures").get("all").getAsString());
    }

    /**
     * Pre-1.13 packed several blocks behind one id as metadata values. Which value meant what
     * is only visible in the language file, and only recoverable as a state when the blockstate
     * leaves one reading: a single property lining up one-to-one with the values found.
     */
    @Test
    void registersASubBlockPerMetadataValue() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"variant=red\": {\"model\": \"examplemod:block/red\"},"
                        + " \"variant=blue\": {\"model\": \"examplemod:block/blue\"}}}");
        resources.put("assets/examplemod/models/block/red.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/models/block/blue.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/lang/en_us.lang",
                "tile.foo.0.name=Red Foo\ntile.foo.1.name=Blue Foo\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        Map<String, String> models = assets.blocks().stream()
                .collect(Collectors.toMap(BridgedBlockDefinition::id, BridgedBlockDefinition::modelId));
        assertEquals(new HashSet<>(Arrays.asList("examplemod:foo", "examplemod:foo_meta1")), models.keySet());
        // Metadata 1 is the second variant, and the sub-block is property-free with just that one.
        assertEquals("examplemod:block/blue", models.get("examplemod:foo_meta1"));

        JsonObject served = json(assets, AssetPath.blockState("examplemod", "foo_meta1"));
        assertEquals(Collections.singleton(""), keysOf(served.getAsJsonObject("variants")));
        assertEquals("examplemod:block/blue",
                served.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    /** The names have to follow the ids, or the sub-block shows up as a raw translation key. */
    @Test
    void namesEveryMetadataValueItRegistered() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"variant=red\": {\"model\": \"examplemod:block/red\"},"
                        + " \"variant=blue\": {\"model\": \"examplemod:block/blue\"}}}");
        resources.put("assets/examplemod/models/block/red.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/models/block/blue.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/lang/en_us.lang",
                "tile.foo.0.name=Red Foo\ntile.foo.1.name=Blue Foo\ntile.foo.tooltip=Some prose\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        JsonObject lang = json(assets, AssetPath.lang("examplemod", "en_us"));
        assertEquals("Red Foo", lang.get("block.examplemod.foo").getAsString());
        assertEquals("Blue Foo", lang.get("block.examplemod.foo_meta1").getAsString());
        // Not a name key: it has no modern counterpart, so it survives exactly as it was.
        assertEquals("Some prose", lang.get("tile.foo.tooltip").getAsString());
    }

    /**
     * When the blockstate cannot say which state a metadata value stood for, guessing one would
     * put the wrong block in the world. The sub-entry still exists, as an item.
     */
    @Test
    void fallsBackToAnItemWhenTheStateCannotBeRecovered() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/lang/en_us.lang",
                "tile.foo.0.name=Foo\ntile.foo.1.name=Other Foo\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals(Collections.singletonList("examplemod:foo"),
                assets.blocks().stream().map(BridgedBlockDefinition::id).collect(Collectors.toList()));
        assertEquals(Collections.singletonList("examplemod:foo_meta1"),
                assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toList()));
        // It has no model of its own, so it looks like the block it was packed with.
        assertEquals("examplemod:item/foo",
                json(assets, AssetPath.itemModel("examplemod", "foo_meta1")).get("parent").getAsString());
    }

    @Test
    void registersASubItemPerMetadataValue() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/models/item/bar.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:items/bar\"}}");
        resources.put("assets/examplemod/lang/en_us.lang",
                "item.bar.0.name=Bar\nitem.bar.1.name=Other Bar\nitem.bar.2.name=Third Bar\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals(new HashSet<>(Arrays.asList("examplemod:bar", "examplemod:bar_meta1", "examplemod:bar_meta2")),
                assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toSet()));
        // Nothing in the archive ties a model to a metadata value, so they share the base one.
        assertEquals("examplemod:item/bar",
                json(assets, AssetPath.itemModel("examplemod", "bar_meta2")).get("parent").getAsString());
    }

    /**
     * Where a mod named its models after the metadata values, the mapping is spelled out in the
     * file names and each sub-item can have its real model rather than the base one.
     */
    @Test
    void givesASubItemItsOwnModelWhenTheArchiveNamedItAfterTheMetadataValue() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/models/item/bar.json", "{\"parent\": \"item/generated\"}");
        resources.put("assets/examplemod/models/item/bar_1.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:items/bar_1\"}}");
        resources.put("assets/examplemod/lang/en_us.lang", "item.bar.0.name=Bar\nitem.bar.1.name=Other Bar\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals("examplemod:item/bar_1",
                json(assets, AssetPath.itemModel("examplemod", "bar_meta1")).get("parent").getAsString());
        // bar_1 is that sub-item, so it must not also be registered under its own name.
        assertEquals(new HashSet<>(Arrays.asList("examplemod:bar", "examplemod:bar_meta1")),
                assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toSet()));
    }

    /** Mods using that naming often ship no base model at all; metadata 0 is then a model too. */
    @Test
    void registersTheBaseEntryFromItsMetadataZeroModel() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/models/item/bar_0.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:items/bar_0\"}}");
        resources.put("assets/examplemod/models/item/bar_1.json",
                "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"examplemod:items/bar_1\"}}");
        resources.put("assets/examplemod/lang/en_us.lang", "item.bar.0.name=Bar\nitem.bar.1.name=Other Bar\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals(new HashSet<>(Arrays.asList("examplemod:bar", "examplemod:bar_meta1")),
                assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toSet()));
        assertEquals("examplemod:item/bar_0",
                json(assets, AssetPath.itemModel("examplemod", "bar")).get("parent").getAsString());
        assertEquals("examplemod:item/bar_1",
                json(assets, AssetPath.itemModel("examplemod", "bar_meta1")).get("parent").getAsString());

        JsonObject lang = json(assets, AssetPath.lang("examplemod", "en_us"));
        assertEquals("Bar", lang.get("item.examplemod.bar").getAsString());
        assertEquals("Other Bar", lang.get("item.examplemod.bar_meta1").getAsString());
    }

    /**
     * A metadata value whose model the mod bound in code to a name of its own choosing cannot be
     * tied back to that value from the assets. The model is still registered as an item in its
     * own right, so the texture is there; only the display name is beyond reach.
     */
    @Test
    void leavesModelsItCannotTieToAMetadataValueAsTheirOwnItems() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/models/item/dust_copper.json", "{\"parent\": \"item/generated\"}");
        resources.put("assets/examplemod/models/item/dust_tin.json", "{\"parent\": \"item/generated\"}");
        resources.put("assets/examplemod/lang/en_us.lang", "item.dust.0.name=Copper Dust\nitem.dust.1.name=Tin Dust\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        // No 'dust' and no 'dust_meta1' were invented out of a language file alone.
        assertEquals(new HashSet<>(Arrays.asList("examplemod:dust_copper", "examplemod:dust_tin")),
                assets.items().stream().map(BridgedItemDefinition::id).collect(Collectors.toSet()));
    }

    /**
     * Plenty of mods write {@code tile.foo.0.name} for a block that has no metadata at all.
     * One value is not a set of sub-entries, and inventing a second block from it would be wrong.
     */
    @Test
    void doesNotExpandASingleMetadataValue() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/foo\"}}}");
        resources.put("assets/examplemod/lang/en_us.lang", "tile.foo.0.name=Foo\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        assertEquals(1, assets.blocks().size());
        assertTrue(assets.items().isEmpty());
        assertEquals("Foo", json(assets, AssetPath.lang("examplemod", "en_us"))
                .get("block.examplemod.foo").getAsString());
    }

    /** An archive that spells the sub-entry out itself knows better than the expansion does. */
    @Test
    void leavesAnExplicitSubBlockAlone() {
        Map<String, String> resources = new HashMap<>();
        resources.put("assets/examplemod/blockstates/foo.json",
                "{\"variants\": {\"variant=red\": {\"model\": \"examplemod:block/red\"},"
                        + " \"variant=blue\": {\"model\": \"examplemod:block/blue\"}}}");
        resources.put("assets/examplemod/models/block/red.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/models/block/blue.json", "{\"parent\": \"block/cube_all\"}");
        resources.put("assets/examplemod/blockstates/foo_meta1.json",
                "{\"variants\": {\"\": {\"model\": \"examplemod:block/handwritten\"}}}");
        resources.put("assets/examplemod/lang/en_us.lang",
                "tile.foo.0.name=Red Foo\ntile.foo.1.name=Blue Foo\n");

        BridgedAssetManager assets = build(TestArchives.archive("example-mod.jar", AssetVersion.LEGACY, resources));

        Map<String, String> models = assets.blocks().stream()
                .collect(Collectors.toMap(BridgedBlockDefinition::id, BridgedBlockDefinition::modelId));
        assertEquals(2, models.size());
        assertEquals("examplemod:block/handwritten", models.get("examplemod:foo_meta1"));
    }

    private static BridgedAssetManager build(AssetArchive... archives) {
        return AssetPipeline.build(Arrays.asList(archives), NOTHING_LOADED);
    }

    private static JsonObject json(BridgedAssetManager assets, AssetPath path) {
        byte[] data = assertDoesNotThrow(() -> assets.readResource(path));
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
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
