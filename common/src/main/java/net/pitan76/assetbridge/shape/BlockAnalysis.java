package net.pitan76.assetbridge.shape;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * What could be worked out about one bridged block beyond "it is a block": which vanilla kind
 * it is, if any, and what shape it has.
 *
 * <p>Both are read out of the assets while the bundle is built, because that is where the
 * models are; the block layer only spends the answer. Either half can be absent, and a block
 * with neither is registered exactly as it was before this existed.
 */
public class BlockAnalysis {
    @Nullable
    private final BlockKind kind;
    @Nullable
    private final BlockShape shape;

    public BlockAnalysis(@Nullable BlockKind kind, @Nullable BlockShape shape) {
        this.kind = kind;
        this.shape = shape;
    }

    @Nullable
    public BlockKind kind() {
        return kind;
    }

    @Nullable
    public BlockShape shape() {
        return shape;
    }

    public boolean isEmpty() {
        return kind == null && shape == null;
    }

    /**
     * Analyses every block in the bundle and stores the result on it.
     *
     * <p>Must run after the models are final: a model whose parent was missing has been
     * replaced by then, so the shape read here is the one the game will draw.
     *
     * @param inferKinds  whether a block may be registered as a vanilla class it looks like
     * @param buildShapes whether a block may be given the shape its model has
     */
    public static void run(BridgedAssetManager assets, boolean inferKinds, boolean buildShapes) {
        if (!inferKinds && !buildShapes) return;

        int kinds = 0;
        int shapes = 0;
        for (BridgedBlockDefinition block : assets.blocks()) {
            JsonObject blockState = read(assets, AssetPath.blockState(block.namespace(), block.path()));

            BlockKind kind = inferKinds ? kindOf(assets, block) : null;
            // A vanilla class brings its own shape, and a better one: it knows about the
            // states the model cannot show, such as which way a stair corner turns.
            BlockShape shape = buildShapes && kind == null && blockState != null
                    ? BlockShapes.of(assets, blockState)
                    : null;

            if (kind == null && shape == null) continue;
            if (kind != null) kinds++;
            if (shape != null) shapes++;

            assets.putAnalysis(block.id(), new BlockAnalysis(kind, shape));
        }
        if (kinds > 0) {
            AssetBridge.LOGGER.info("Registering {} block(s) as the vanilla kind their models describe", kinds);
        }
        if (shapes > 0) {
            AssetBridge.LOGGER.info("Derived a shape from the model of {} block(s)", shapes);
        }
    }

    /**
     * The vanilla kind a block's model says it is, or {@code null} when nothing says so or the
     * blockstate could not be registered as that kind.
     *
     * <p>The chain is read from its far end first, so the vanilla model a mod inherited from
     * decides before the mod's own naming does: a model called {@code stone_stairs_slab} that
     * inherits from {@code minecraft:block/stairs} is stairs.
     */
    @Nullable
    private static BlockKind kindOf(BridgedAssetManager assets, BridgedBlockDefinition block) {
        List<String> chain = ModelGeometry.parentChain(assets, block.modelId());
        for (int i = chain.size() - 1; i >= 0; i--) {
            String reference = chain.get(i);
            int slash = reference.lastIndexOf('/');
            String name = slash < 0 ? reference : reference.substring(slash + 1);

            BlockKind kind = BlockKind.byModelName(name);
            if (kind == null) continue;
            if (kind.accepts(block.states())) return kind;

            AssetBridge.LOGGER.info("{} looks like {} but its blockstate does not fit that block's "
                    + "properties; registering it as a plain block", block.id(), kind);
            return null;
        }
        return null;
    }

    @Nullable
    private static JsonObject read(BridgedAssetManager assets, AssetPath path) {
        try {
            byte[] data = assets.readResource(path);
            return data == null ? null : Json.parse(new String(data, StandardCharsets.UTF_8));
        } catch (IOException e) {
            AssetBridge.LOGGER.warn("Could not read {} while analysing a block", path, e);
            return null;
        }
    }
}
