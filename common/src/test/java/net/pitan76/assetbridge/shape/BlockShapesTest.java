package net.pitan76.assetbridge.shape;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.parse.VariantKey;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockShapesTest {
    @Test
    void readsTheBoxesAModelIsDrawnOutOf() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/pipe",
                "{\"elements\":[{\"from\":[4,0,4],\"to\":[12,16,12]}]}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets), blockState("{\"variants\":{\"\":{\"model\":\"examplemod:block/pipe\"}}}"));

        assertNotNull(shape);
        assertEquals(Collections.singletonList(new ShapeBox(4, 0, 4, 12, 16, 12)),
                shape.boxesFor(Collections.<String, String>emptyMap()));
    }

    @Test
    void inheritsTheGeometryOfAParentThatHasSome() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/base",
                "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,4,16]}]}");
        putModel(assets, "examplemod", "block/derived",
                "{\"parent\":\"examplemod:block/base\",\"textures\":{\"all\":\"examplemod:block/derived\"}}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets),
                blockState("{\"variants\":{\"\":{\"model\":\"examplemod:block/derived\"}}}"));

        assertNotNull(shape);
        assertEquals(Collections.singletonList(new ShapeBox(0, 0, 0, 16, 4, 16)),
                shape.boxesFor(Collections.<String, String>emptyMap()));
    }

    @Test
    void leavesAFullCubeAlone() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/stone",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"examplemod:block/stone\"}}");

        assertNull(BlockShapes.of(new ModelGeometry(assets), blockState("{\"variants\":{\"\":{\"model\":\"examplemod:block/stone\"}}}")));
    }

    @Test
    void turnsTheBoxesTheWayTheVariantTurnsTheModel() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/rung",
                "{\"elements\":[{\"from\":[0,0,13],\"to\":[16,16,16]}]}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets), blockState("{\"variants\":{"
                + "\"facing=north\":{\"model\":\"examplemod:block/rung\"},"
                + "\"facing=east\":{\"model\":\"examplemod:block/rung\",\"y\":90}}}"));

        assertNotNull(shape);
        assertEquals(Collections.singletonList(new ShapeBox(0, 0, 13, 16, 16, 16)),
                shape.boxesFor(values("facing", "north")));
        assertEquals(Collections.singletonList(new ShapeBox(0, 0, 0, 3, 16, 16)),
                shape.boxesFor(values("facing", "east")));
    }

    /** A state the file covers with a full cube must not pick up another variant's shape. */
    @Test
    void aStateThatReallyIsAFullCubeKeepsIt() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/open",
                "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,2,16]}]}");
        putModel(assets, "examplemod", "block/closed",
                "{\"parent\":\"minecraft:block/cube_all\"}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets), blockState("{\"variants\":{"
                + "\"open=false\":{\"model\":\"examplemod:block/closed\"},"
                + "\"open=true\":{\"model\":\"examplemod:block/open\"}}}"));

        assertNotNull(shape);
        assertEquals(Collections.singletonList(new ShapeBox(0, 0, 0, 16, 16, 16)),
                shape.boxesFor(values("open", "false")));
        assertEquals(Collections.singletonList(new ShapeBox(0, 0, 0, 16, 2, 16)),
                shape.boxesFor(values("open", "true")));
    }

    @Test
    void aMultipartBlockIsShapedByThePartsThatAlwaysApply() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/post",
                "{\"elements\":[{\"from\":[6,0,6],\"to\":[10,16,10]}]}");
        putModel(assets, "examplemod", "block/side",
                "{\"elements\":[{\"from\":[7,6,0],\"to\":[9,15,6]}]}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets), blockState("{\"multipart\":["
                + "{\"apply\":{\"model\":\"examplemod:block/post\"}},"
                + "{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":\"examplemod:block/side\"}}]}"));

        assertNotNull(shape);
        // Only the post: an arm reaching towards a neighbour is not something to be stopped by.
        assertEquals(Collections.singletonList(new ShapeBox(6, 0, 6, 10, 16, 10)),
                shape.boxesFor(values("north", "true")));
    }

    @Test
    void aRotatedElementIsEnclosedByABox() {
        BridgedAssetManager assets = new BridgedAssetManager();
        putModel(assets, "examplemod", "block/blade",
                "{\"elements\":[{\"from\":[0,0,7],\"to\":[16,16,9],"
                + "\"rotation\":{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":45}}]}");

        BlockShape shape = BlockShapes.of(new ModelGeometry(assets),
                blockState("{\"variants\":{\"\":{\"model\":\"examplemod:block/blade\"}}}"));

        assertNotNull(shape);
        List<ShapeBox> boxes = shape.boxesFor(Collections.<String, String>emptyMap());
        assertEquals(1, boxes.size());
        ShapeBox box = boxes.get(0);
        // A blade turned 45 degrees is not a box any more, so what is used is the box it fits in.
        assertEquals(8 - 9 * Math.sqrt(0.5), box.minX, 0.001);
        assertEquals(8 + 9 * Math.sqrt(0.5), box.maxX, 0.001);
        assertEquals(8 - 9 * Math.sqrt(0.5), box.minZ, 0.001);
        assertEquals(8 + 9 * Math.sqrt(0.5), box.maxZ, 0.001);
        // Turning around the vertical axis leaves the height alone.
        assertEquals(0.0, box.minY);
        assertEquals(16.0, box.maxY);
    }

    @Test
    void readsAVariantKeyIntoTheConditionsItSpellsOut() {
        assertEquals(values("facing", "north"), VariantKey.parse("facing=north"));
        assertEquals(Collections.emptyMap(), VariantKey.parse(""));
        assertNull(VariantKey.parse("normal"));
        assertNull(VariantKey.parse("facing"));
    }

    private static Map<String, String> values(String name, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(name, value);
        return values;
    }

    private static JsonObject blockState(String json) {
        JsonObject parsed = Json.parse(json);
        assertNotNull(parsed);
        return parsed;
    }

    private static void putModel(BridgedAssetManager assets, String namespace, String path, String json) {
        assets.putResource(AssetPath.model(namespace, path), json.getBytes(StandardCharsets.UTF_8));
    }
}
