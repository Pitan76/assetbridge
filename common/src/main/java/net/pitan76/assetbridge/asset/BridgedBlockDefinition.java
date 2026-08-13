package net.pitan76.assetbridge.asset;

/**
 * Internal, version-neutral description of one block discovered in an external archive.
 *
 * @param modelId    a representative model in {@code namespace:block/name} form, used for the
 *                   item model and as the fallback when the blockstate cannot be passed through
 * @param states     the properties the block must be registered with so that its original
 *                   blockstate file resolves; empty when the block is property-free
 */
public class BridgedBlockDefinition {
    private final String namespace;
    private final String path;
    private final String modelId;
    private final BridgedStateDefinition states;
    private final String sourceArchive;
    private final AssetVersion version;
    private final boolean supportsFacing;

    public BridgedBlockDefinition(String namespace, String path, String modelId,
                                   BridgedStateDefinition states, String sourceArchive, AssetVersion version) {
        this(namespace, path, modelId, states, sourceArchive, version, false);
    }

    public BridgedBlockDefinition(String namespace, String path, String modelId, BridgedStateDefinition states,
                                   String sourceArchive, AssetVersion version, boolean supportsFacing) {
        this.namespace = namespace;
        this.path = path;
        this.modelId = modelId;
        this.states = states;
        this.sourceArchive = sourceArchive;
        this.version = version;
        this.supportsFacing = supportsFacing;
    }

    /**
     * Whether this block was registered with a placement-facing property (1.12.2 only; see
     * {@code BridgedBlock}). False when the representative model already carries its own fixed
     * {@code x}/{@code y} rotation (e.g. a log lying on its side): composing that with a second,
     * placement-driven rotation is not a simple addition, so those blocks keep their single fixed
     * orientation instead of risking an incorrectly rotated model.
     */
    public boolean supportsFacing() {
        return supportsFacing;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BridgedBlockDefinition)) return false;
        BridgedBlockDefinition other = (BridgedBlockDefinition) o;
        return namespace.equals(other.namespace) && path.equals(other.path) && modelId.equals(other.modelId)
                && states.equals(other.states) && sourceArchive.equals(other.sourceArchive)
                && version == other.version && supportsFacing == other.supportsFacing;
    }

    @Override
    public int hashCode() {
        int result = namespace.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + modelId.hashCode();
        result = 31 * result + states.hashCode();
        result = 31 * result + sourceArchive.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + Boolean.hashCode(supportsFacing);
        return result;
    }

    @Override
    public String toString() {
        return id() + " -> " + modelId;
    }
}
