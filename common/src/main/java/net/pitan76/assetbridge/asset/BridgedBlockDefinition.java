package net.pitan76.assetbridge.asset;

/**
 * Internal, version-neutral description of one block discovered in an external archive.
 *
 * @param modelId    a representative model in {@code namespace:block/name} form, used for the
 *                   item model and as the fallback when the blockstate cannot be passed through
 * @param states     the properties the block must be registered with so that its original
 *                   blockstate file resolves; empty when the block is property-free
 */
public record BridgedBlockDefinition(String namespace, String path, String modelId,
                                BridgedStateDefinition states, String sourceArchive, AssetVersion version) {
    public String id() {
        return namespace + ":" + path;
    }

    @Override
    public String toString() {
        return id() + " -> " + modelId;
    }
}
