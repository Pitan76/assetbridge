package net.pitan76.assetbridge.asset;

/**
 * An item discovered from an {@code models/item/*.json} that no bridged block claims.
 *
 * <p>Items need nothing beyond their model, so there is no equivalent of
 * {@link BridgedStateDefinition} here.
 */
public record BridgedItemAsset(String namespace, String path, String sourceArchive, AssetVersion version) {
    public String id() {
        return namespace + ":" + path;
    }

    @Override
    public String toString() {
        return id();
    }
}
