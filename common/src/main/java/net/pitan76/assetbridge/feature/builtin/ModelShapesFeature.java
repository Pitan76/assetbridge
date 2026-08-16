package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.feature.Feature;

/**
 * Gives a bridged block the shape the model it is drawn with has, instead of the full cube
 * every block would otherwise be.
 *
 * <p>A block whose model is a full cube is left alone, so this only ever narrows something that
 * looked wrong: a pipe the player could not walk past, a lamp post that filled its block.
 *
 * <p>Read by {@code AssetPipeline} while the bundle is built, which is why {@link #apply} has
 * nothing to do.
 */
public class ModelShapesFeature implements Feature {
    public static final String ID = "model_shapes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Give each bridged block the collision and outline shape of its model, instead of a full cube. "
                + "Blocks whose model is a full cube are unaffected.";
    }
}
