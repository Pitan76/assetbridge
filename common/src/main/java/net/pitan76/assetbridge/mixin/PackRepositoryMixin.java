package net.pitan76.assetbridge.mixin;

import com.google.common.collect.ImmutableSet;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.pitan76.assetbridge.pack.AssetBridgeRepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds the Asset Bridge pack source to every resource-pack repository, without depending on
 * a loader-specific resource API.
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void assetbridge$addSource(CallbackInfo ci) {
        for (RepositorySource source : sources) {
            // Data pack repository: bridged assets are client resources only.
            if (source instanceof ServerPacksSource) return;
            // The PackType constructor delegates to the other one, so guard against a double add.
            if (source == AssetBridgeRepositorySource.INSTANCE) return;
        }

        Set<RepositorySource> merged = new LinkedHashSet<>(sources);
        merged.add(AssetBridgeRepositorySource.INSTANCE);
        this.sources = ImmutableSet.copyOf(merged);
    }
}
