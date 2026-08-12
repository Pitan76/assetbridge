package net.pitan76.assetbridge.mixin;

/**
 * No-op on 1.12.2.
 *
 * <p>On later versions this mixes into {@code PackRepository} to add the Asset Bridge pack
 * source to its mutable {@code Set<RepositorySource>}. 1.12.2 has neither {@code PackRepository}
 * nor {@code RepositorySource} -- that shared client/server pack abstraction was introduced in
 * 1.13. There is nothing to mix into here: bridged assets are written directly to disk instead,
 * see {@link net.pitan76.assetbridge.pack.AssetBridgeRepositorySource}. Kept as an (unannotated,
 * inert) class rather than deleted so {@code assetbridge.mixins.json} -- whose {@code "mixins"}
 * list has been emptied to match -- did not need a structural change alongside this file.
 */
public class PackRepositoryMixin {
    private PackRepositoryMixin() {
    }
}
