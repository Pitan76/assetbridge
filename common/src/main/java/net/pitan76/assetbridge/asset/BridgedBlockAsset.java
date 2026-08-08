package net.pitan76.assetbridge.asset;

/**
 * Internal, version-neutral description of one block discovered in an external archive.
 * Deliberately far simpler than a vanilla blockstate: the MVP renders a single model
 * and registers a property-less block.
 */
public final class BridgedBlockAsset {
    private final String namespace;
    private final String path;
    private final String modelId;
    private final String sourceArchive;
    private final AssetVersion version;

    public BridgedBlockAsset(String namespace, String path, String modelId, String sourceArchive, AssetVersion version) {
        this.namespace = namespace;
        this.path = path;
        this.modelId = modelId;
        this.sourceArchive = sourceArchive;
        this.version = version;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    /** Model reference in {@code namespace:block/name} form. */
    public String modelId() {
        return modelId;
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
