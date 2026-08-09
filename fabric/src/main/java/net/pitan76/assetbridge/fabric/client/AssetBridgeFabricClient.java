package net.pitan76.assetbridge.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
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
            for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
                if (BridgedBlocks.isCutout(entry.getKey())) {
                    BlockRenderLayerMap.INSTANCE.putBlock(entry.getValue(), RenderType.cutoutMipped());
                }
            }
        }
    }
}
