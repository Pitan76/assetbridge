package net.pitan76.assetbridge.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;

import java.util.Map;

public class AssetBridgeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (Features.isEnabled(CutoutBlocksFeature.ID)) {
            //? if >=26 {
            /*// 26.1 removed the per-block render layer registry: `ItemBlockRenderTypes` is gone,
            // Fabric API dropped `BlockRenderLayerMap` with it, and the layer a block draws on is
            // now declared by its block model JSON (`"render_type"`). Vanilla also collapsed the
            // mipped variant -- ChunkSectionLayer has only SOLID, CUTOUT and TRANSLUCENT.
            //
            // There is therefore nothing to register from code. Assets that already carry
            // `render_type` render correctly on their own, but assets from pre-26 mods do not, so
            // CutoutBlocksFeature has no effect here until the served model JSON is rewritten to
            // add `render_type` -- see memo/MC-26.1.2.md.
            *///?} else {
            for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
                if (BridgedBlocks.isCutout(entry.getKey())) {
                    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                            entry.getValue(), net.minecraft.client.renderer.RenderType.cutoutMipped());
                }
            }
            //?}
        }
    }
}
