package net.pitan76.assetbridge.asset;

/**
 * An item discovered from an {@code models/item/*.json} that no bridged block claims.
 *
 * <p>Items need nothing beyond their model, so there is no equivalent of
 * {@link BridgedStateDefinition} here.
 */
public class BridgedItemDefinition {
    private final String namespace;
    private final String path;
    private final String sourceArchive;
    private final AssetVersion version;

    public BridgedItemDefinition(String namespace, String path, String sourceArchive, AssetVersion version) {
        this.namespace = namespace;
        this.path = path;
        this.sourceArchive = sourceArchive;
        this.version = version;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
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
        if (!(o instanceof BridgedItemDefinition)) return false;
        BridgedItemDefinition other = (BridgedItemDefinition) o;
        return namespace.equals(other.namespace) && path.equals(other.path)
                && sourceArchive.equals(other.sourceArchive) && version == other.version;
    }

    @Override
    public int hashCode() {
        int result = namespace.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + sourceArchive.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return id();
    }
}
