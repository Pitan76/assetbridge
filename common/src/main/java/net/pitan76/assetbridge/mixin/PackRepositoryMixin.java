package net.pitan76.assetbridge.mixin;

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
        // A repository that already reads the vanilla data packs is the data pack one;
        // anything else is a resource pack repository.
        boolean serverSide = false;
        for (RepositorySource source : sources) {
            if (source instanceof ServerPacksSource) serverSide = true;
            // The PackType constructor delegates to the other one, so guard against a double add.
            if (source instanceof AssetBridgeRepositorySource) return;
        }

        // Stays mutable: other mods add their own sources to this set after the constructor
        // returns. Fabric's resource loader does exactly that, and replacing the field with
        // an immutable copy made it throw when the world creation screen opened.
        Set<RepositorySource> merged = new LinkedHashSet<>(sources);
        merged.add(serverSide ? AssetBridgeRepositorySource.DATA : AssetBridgeRepositorySource.RESOURCES);
        this.sources = merged;
    }
}
