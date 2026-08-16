package net.pitan76.assetbridge.asset;

import net.pitan76.assetbridge.shape.BlockAnalysis;
import org.jetbrains.annotations.Nullable;

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
     * What the block's models turned out to say about it. Filled in at the end of the
     * pipeline, once every model is final, and left null for a block whose models said
     * nothing beyond "it is a block". Not part of the block's identity, so it stays out of
     * {@link #equals}: two definitions describing the same block are the same block whether
     * or not they have been analysed yet.
     */
    @Nullable
    private BlockAnalysis analysis;

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

    public void setAnalysis(@Nullable BlockAnalysis analysis) {
        this.analysis = analysis;
    }

    /** What the models said about this block, or {@code null} when they said nothing useful. */
    @Nullable
    public BlockAnalysis analysis() {
        return analysis;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BridgedBlockDefinition)) return false;
        BridgedBlockDefinition other = (BridgedBlockDefinition) obj;
        return namespace.equals(other.namespace)
                && path.equals(other.path)
                && modelId.equals(other.modelId)
                && states.equals(other.states)
                && sourceArchive.equals(other.sourceArchive)
                && version.equals(other.version);
    }

    @Override
    public int hashCode() {
        int result = namespace.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + modelId.hashCode();
        result = 31 * result + states.hashCode();
        result = 31 * result + sourceArchive.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return id() + " -> " + modelId;
    }
}
