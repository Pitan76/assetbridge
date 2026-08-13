package net.pitan76.assetbridge.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.util.Ids;

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
    private BridgedBlock(String id, BridgedStateDefinition states) {
        super(rockMaterial());
        setTranslationKey(id);
        if (BridgedItemGroup.creativeTabsSupported()) {
            setCreativeTab(BridgedItemGroup.getTab(Ids.namespaceOf(id), true));
        }
        setHardness(this, BlockConfig.getHardness(id));
        setResistance(BlockConfig.getResistance(id));

        if (!states.isEmpty()) {
            AssetBridge.LOGGER.debug(
                    "Ignoring {} bridged propert{} on {}: 1.12.2 does not support metadata-driven blockstate variants",
                    states.properties().size(), states.properties().size() == 1 ? "y" : "ies", id);
        }
    }

    public static BridgedBlock create(String id, BridgedStateDefinition states) {
        return new BridgedBlock(id, states);
    }

    /**
     * {@code Material}'s stone-like constant, fetched by reflection because its field name
     * differs across the mappings {@code common} is shared between: MCP (Forge) calls it
     * {@code ROCK}, Legacy Yarn (Legacy Fabric) calls the same constant {@code STONE}. A direct
     * {@code Material.ROCK}/{@code Material.STONE} reference would fail to resolve -- and throw
     * {@code NoSuchFieldError} the moment this constructor runs -- on whichever platform did not
     * compile this class, since {@code super(...)} cannot be guarded the way an ordinary method
     * call can.
     */
    private static Material rockMaterial() {
        for (String name : new String[]{"ROCK", "STONE"}) {
            try {
                return (Material) Material.class.getField(name).get(null);
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping's name.
            }
        }
        throw new IllegalStateException("Material has neither a ROCK nor a STONE field");
    }

    /**
     * {@code Block#setHardness(float)} under MCP (Forge); the same method is
     * {@code Block#setStrength(float)} under Legacy Yarn (Legacy Fabric). Called reflectively for
     * the same reason {@link #rockMaterial()} is: a direct call would throw
     * {@code NoSuchMethodError} on whichever platform did not compile this class.
     */
    private static void setHardness(Block block, float hardness) {
        for (String name : new String[]{"setHardness", "setStrength"}) {
            try {
                Block.class.getMethod(name, float.class).invoke(block, hardness);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping's name.
            }
        }
        throw new IllegalStateException("Block has neither a setHardness nor a setStrength method");
    }
}
