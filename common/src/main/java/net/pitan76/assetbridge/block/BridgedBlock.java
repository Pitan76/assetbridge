package net.pitan76.assetbridge.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;

/**
 * A block that carries the model/texture recovered from an external blockstate file.
 *
 * <p>On every other supported version this also reproduces the block's variants by building a
 * real {@code Property}/{@code StateDefinition} from {@link BridgedStateDefinition} and picking
 * an orientation on placement. 1.12.2 predates the flattened blockstate system entirely: a
 * block there has at most 16 metadata variants (four bits), described through
 * {@code IProperty}/{@code BlockStateContainer}, and an externally-recovered property set can
 * easily need more states than that fits (and has no placement-orientation concept to hook
 * into in the first place without one). Rather than approximate that, a bridged block on
 * 1.12.2 is registered as a single, property-free block: it always renders its default variant,
 * and any bridged properties are ignored (and logged) for blockstate purposes. The original
 * blockstate file's default model still resolves, and its texture and item icon are unaffected
 * -- only per-state visual variation and orientation-on-placement are lost on this version.
 */
public class BridgedBlock extends Block {
    private BridgedBlock(ResourceLocation id, BridgedStateDefinition states) {
        super(Material.ROCK);
        setTranslationKey(id.toString());
        setCreativeTab(BridgedItemGroup.getTab(id.getNamespace(), true));
        setHardness(BlockConfig.getHardness(id));
        setResistance(BlockConfig.getResistance(id));

        if (!states.isEmpty()) {
            AssetBridge.LOGGER.debug(
                    "Ignoring {} bridged propert{} on {}: 1.12.2 does not support metadata-driven blockstate variants",
                    states.properties().size(), states.properties().size() == 1 ? "y" : "ies", id);
        }
    }

    public static BridgedBlock create(ResourceLocation id, BridgedStateDefinition states) {
        return new BridgedBlock(id, states);
    }
}
