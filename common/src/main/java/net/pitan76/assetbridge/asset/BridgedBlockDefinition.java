package net.pitan76.assetbridge.asset;

/**
 * Internal, version-neutral description of one block discovered in an external archive.
 */
public class BridgedBlockDefinition {
    public final String namespace;
    public final String path;
    public final String modelId;
    public final BridgedStateDefinition states;
    public final String sourceArchive;
    public final AssetVersion version;

    /**
     * @param modelId    a representative model in {@code namespace:block/name} form, used for the
     *                   item model and as the fallback when the blockstate cannot be passed through
     * @param states     the properties the block must be registered with so that its original
     *                   blockstate file resolves; empty when the block is property-free
     */
    public BridgedBlockDefinition(String namespace, String path, String modelId,
                                  BridgedStateDefinition states, String sourceArchive, AssetVersion version) {
        this.namespace = namespace;
        this.path = path;
        this.modelId = modelId;
        this.states = states;
        this.sourceArchive = sourceArchive;
        this.version = version;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public String modelId() {
        return modelId;
    }

    public BridgedStateDefinition states() {
        return states;
    }

    public String sourceArchive() {
        return sourceArchive;
    }

    public AssetVersion version() {
        return version;
    }

    public String id() {
        return namespace + ":" + path;
    }

    @Override
    public String toString() {
        return id() + " -> " + modelId;
    }
}
