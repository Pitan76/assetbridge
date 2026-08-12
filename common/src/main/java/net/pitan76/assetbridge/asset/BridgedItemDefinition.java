package net.pitan76.assetbridge.asset;

/**
 * An item discovered from an {@code models/item/*.json} that no bridged block claims.
 *
 * <p>Items need nothing beyond their model, so there is no equivalent of
 * {@link BridgedStateDefinition} here.
 */
public class BridgedItemDefinition {
//    String namespace, String path, String sourceArchive, AssetVersion version
    public final String namespace;
    public final String path;
    public final String sourceArchive;
    public final AssetVersion version;

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
    public String toString() {
        return id();
    }
}
