package net.pitan76.assetbridge.shape;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockAnalysisTest {
    @Test
    void recognisesStairsByTheModelTheyInheritFrom() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/marble_stairs.json", "{\"parent\":\"minecraft:block/stairs\"}");
        put(assets, "examplemod", "blockstates/marble_stairs.json",
                "{\"variants\":{\"facing=north,half=bottom\":{\"model\":\"examplemod:block/marble_stairs\"}}}");
        assets.addBlock(block("marble_stairs",
                property("facing", "north", "south", "west", "east"),
                property("half", "top", "bottom"),
                property("shape", "straight", "inner_left", "inner_right", "outer_left", "outer_right")));

        BlockAnalysis.run(assets, true, true);

        BlockAnalysis analysis = assets.analysis("examplemod:marble_stairs");
        assertNotNull(analysis);
        assertEquals(BlockKind.STAIRS, analysis.kind());
        // A vanilla staircase brings a better shape than the model could describe.
        assertNull(analysis.shape());
    }

    @Test
    void doesNotRecogniseAKindWhoseBlockstateWouldNotFit() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/odd_stairs.json", "{\"parent\":\"minecraft:block/stairs\"}");
        put(assets, "examplemod", "blockstates/odd_stairs.json",
                "{\"variants\":{\"facing=up\":{\"model\":\"examplemod:block/odd_stairs\"}}}");
        // A staircase cannot face up, so the file could not be served against a StairBlock.
        assets.addBlock(block("odd_stairs",
                property("facing", "north", "south", "west", "east", "up", "down"),
                property("half", "top", "bottom")));

        BlockAnalysis.run(assets, true, true);

        BlockAnalysis analysis = assets.analysis("examplemod:odd_stairs");
        assertNull(analysis == null ? null : analysis.kind());
    }

    @Test
    void fallsBackToTheShapeTheModelDescribes() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/pipe.json",
                "{\"elements\":[{\"from\":[4,0,4],\"to\":[12,16,12]}]}");
        put(assets, "examplemod", "blockstates/pipe.json",
                "{\"variants\":{\"\":{\"model\":\"examplemod:block/pipe\"}}}");
        assets.addBlock(block("pipe"));

        BlockAnalysis.run(assets, true, true);

        BlockAnalysis analysis = assets.analysis("examplemod:pipe");
        assertNotNull(analysis);
        assertNull(analysis.kind());
        assertNotNull(analysis.shape());
    }

    @Test
    void recordsNothingWhenBothHalvesAreSwitchedOff() {
        BridgedAssetManager assets = new BridgedAssetManager();
        put(assets, "examplemod", "models/block/pipe.json",
                "{\"elements\":[{\"from\":[4,0,4],\"to\":[12,16,12]}]}");
        put(assets, "examplemod", "blockstates/pipe.json",
                "{\"variants\":{\"\":{\"model\":\"examplemod:block/pipe\"}}}");
        assets.addBlock(block("pipe"));

        BlockAnalysis.run(assets, false, false);

        assertNull(assets.analysis("examplemod:pipe"));
    }

    @Test
    void aVanillaKindOnlyAcceptsPropertiesThatBlockHas() {
        BridgedStateDefinition slab = new BridgedStateDefinition(
                Arrays.asList(property("type", "top", "bottom", "double")));
        assertEquals(true, BlockKind.SLAB.accepts(slab));

        BridgedStateDefinition withStranger = new BridgedStateDefinition(
                Arrays.asList(property("type", "top", "bottom"), property("variant", "carved")));
        assertEquals(false, BlockKind.SLAB.accepts(withStranger));
    }

    @Test
    void aVanillaKindNeedsThePropertiesItsBehaviourIsBuiltOn() {
        // A model that borrowed the ladder look but has nothing to face is not a ladder.
        assertEquals(false, BlockKind.LADDER.accepts(BridgedStateDefinition.empty()));
        assertEquals(true, BlockKind.LADDER.accepts(new BridgedStateDefinition(
                Arrays.asList(property("facing", "north", "south", "west", "east")))));
    }

    @Test
    void readsTheKindOutOfAVanillaModelName() {
        assertEquals(BlockKind.STAIRS, BlockKind.byModelName("stairs"));
        assertEquals(BlockKind.SLAB, BlockKind.byModelName("slab_top"));
        assertEquals(BlockKind.FENCE, BlockKind.byModelName("fence_post"));
        // The gate has to win over the fence it contains as a word.
        assertEquals(BlockKind.FENCE_GATE, BlockKind.byModelName("fence_gate_open"));
        assertEquals(BlockKind.WALL, BlockKind.byModelName("template_wall_post"));
        assertEquals(BlockKind.PANE, BlockKind.byModelName("template_glass_pane_post"));
        assertEquals(BlockKind.TRAPDOOR, BlockKind.byModelName("template_orientable_trapdoor_open"));
        assertEquals(BlockKind.DOOR, BlockKind.byModelName("door_bottom_left"));
        assertEquals(BlockKind.LADDER, BlockKind.byModelName("ladder"));
        assertNull(BlockKind.byModelName("cube_all"));
    }

    private static BridgedBlockDefinition block(String name, BridgedProperty... properties) {
        List<BridgedProperty> list = new ArrayList<>(Arrays.asList(properties));
        return new BridgedBlockDefinition("examplemod", name, "examplemod:block/" + name,
                new BridgedStateDefinition(list), "example.jar", AssetVersion.FLATTENED);
    }

    private static BridgedProperty property(String name, String... values) {
        return Objects.requireNonNull(BridgedProperty.of(name, Arrays.asList(values)));
    }

    private static void put(BridgedAssetManager assets, String namespace, String path, String json) {
        AssetPath assetPath = Objects.requireNonNull(AssetPath.parse("assets/" + namespace + "/" + path));
        assets.putResource(assetPath, json.getBytes(StandardCharsets.UTF_8));
    }
}
