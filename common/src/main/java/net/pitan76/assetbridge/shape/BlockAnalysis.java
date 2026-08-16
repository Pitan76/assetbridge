package net.pitan76.assetbridge.shape;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What could be worked out about one bridged block beyond "it is a block": which vanilla kind
 * it is, or else what shape it has.
 *
 * <p>Never both. A block recognised as a vanilla kind is registered as that class, which brings
 * shapes of its own — better ones, because they cover the states the model cannot show, such as
 * which way a stair corner turns. So the kind is looked for first and the shape is only worked
 * out when there is none, which leaves the block layer with nothing left to decide.
 *
 * <p>Both are read out of the assets while the bundle is built, because that is where the
 * models are; the block layer only spends the answer.
 */
public class BlockAnalysis {
    @Nullable
    private final BlockKind kind;
    @Nullable
    private final BlockShape shape;

    private BlockAnalysis(@Nullable BlockKind kind, @Nullable BlockShape shape) {
        this.kind = kind;
        this.shape = shape;
    }

    /** The vanilla class this block should be registered as, or {@code null} for a plain one. */
    @Nullable
    public BlockKind kind() {
        return kind;
    }

    /** The shape a plain block should be given; always {@code null} when {@link #kind} is set. */
    @Nullable
    public BlockShape shape() {
        return shape;
    }

    /**
     * Analyses every block in the bundle and records the result on it.
     *
     * <p>Must run after the models are final: a model whose parent was missing has been
     * replaced by then, so the shape read here is the one the game will draw.
     *
     * @param inferKinds  whether a block may be registered as a vanilla class it looks like
     * @param buildShapes whether a block may be given the shape its model has
     */
    public static void run(BridgedAssetManager assets, boolean inferKinds, boolean buildShapes) {
        if (!inferKinds && !buildShapes) return;

        // Lives no longer than the run: every model it remembers is already in the bundle, and
        // once the blocks are built nothing asks about the geometry again.
        ModelGeometry models = new ModelGeometry(assets);
        int kinds = 0;
        int shapes = 0;

        for (BridgedBlockDefinition block : assets.blocks()) {
            BlockKind kind = inferKinds ? kindOf(models, block) : null;
            if (kind != null) {
                block.setAnalysis(new BlockAnalysis(kind, null));
                kinds++;
                continue;
            }
            if (!buildShapes) continue;

            // Only now: a block that is a vanilla kind never needs its blockstate read.
            JsonObject blockState = assets.readJson(AssetPath.blockState(block.namespace(), block.path()));
            BlockShape shape = blockState == null ? null : BlockShapes.of(models, blockState);
            if (shape == null) continue;

            block.setAnalysis(new BlockAnalysis(null, shape));
            shapes++;
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
    private static BlockKind kindOf(ModelGeometry models, BridgedBlockDefinition block) {
        List<String> chain = models.resolve(block.modelId()).chain();
        for (int i = chain.size() - 1; i >= 0; i--) {
            BlockKind kind = BlockKind.byModelName(AssetPath.modelName(chain.get(i)));
            if (kind == null) continue;
            if (kind.accepts(block.states())) return kind;

            AssetBridge.LOGGER.info("{} looks like {} but its blockstate does not fit that block's "
                    + "properties; registering it as a plain block", block.id(), kind);
            return null;
        }
        return null;
    }
}
