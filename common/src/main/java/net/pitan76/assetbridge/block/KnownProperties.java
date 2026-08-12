package net.pitan76.assetbridge.block;

import net.minecraft.block.properties.IProperty;
import net.pitan76.assetbridge.asset.BridgedProperty;
import org.jetbrains.annotations.Nullable;

/**
 * On later versions this recognises recovered properties with an exact vanilla counterpart,
 * so a bridged block can pick up a real {@code DirectionProperty} and orient itself on
 * placement. 1.12.2 has no equivalent: {@link BridgedBlock} does not build a metadata-driven
 * {@code IProperty}/{@code BlockStateContainer} for bridged blocks at all (see its class
 * comment), so there is nothing to ever match against here.
 */
public class KnownProperties {
    private KnownProperties() {
    }

    @Nullable
    public static IProperty<?> match(BridgedProperty property) {
        return null;
    }
}
