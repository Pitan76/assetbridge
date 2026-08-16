package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.feature.Feature;

/**
 * Registers the extra blocks and items a pre-1.13 archive packed behind one registry name as
 * metadata values.
 *
 * <p>Switched on by default: without it most of a 1.12 mod's content simply is not there, which
 * is the opposite of what Asset Bridge is for. It is switchable because the expansion has to
 * work out which metadata value meant what from the assets alone, and where a mod's language
 * file and blockstate disagree about how many things there are it can invent an entry the mod
 * never had.
 *
 * <p>Read by {@code AssetPipeline} while the bundle is built, which is why {@link #apply} has
 * nothing to do &mdash; the work is over by the time features run.
 */
public class MetaVariantsFeature implements Feature {
    public static final String ID = "meta";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Register the extra blocks and items that pre-1.13 mods packed behind one id as metadata values. "
                + "Turning this off leaves only the first of each such group.";
    }
}
