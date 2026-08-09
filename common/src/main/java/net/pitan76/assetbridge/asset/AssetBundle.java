package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Asset Bridge extracted from the external archives, already converted to
 * the format the running Minecraft version expects.
 */
public class AssetBundle {
    private final Map<AssetPath, AssetSource> resources = new HashMap<>();
    private final List<BridgedBlockAsset> blocks = new ArrayList<>();
    private final List<BridgedItemAsset> items = new ArrayList<>();

    /** For resources Asset Bridge produced itself; they exist nowhere on disk. */
    public void putResource(AssetPath path, byte[] data) {
        putResource(path, AssetSource.ofBytes(data));
    }

    /** For resources still backed by their archive, read only when the game asks. */
    public void putResource(AssetPath path, AssetSource source) {
        resources.put(path, source);
    }

    public void addBlock(BridgedBlockAsset block) {
        blocks.add(block);
    }

    public void addItem(BridgedItemAsset item) {
        items.add(item);
    }

    public boolean hasResource(AssetPath path) {
        return resources.containsKey(path);
    }

    public Map<AssetPath, AssetSource> resources() {
        return resources;
    }

    /** Reads a resource in full. Convenience for callers that cannot stream, e.g. tests. */
    @Nullable
    public byte[] readResource(AssetPath path) throws IOException {
        AssetSource source = resources.get(path);
        return source == null ? null : source.readAll();
    }

    public List<BridgedBlockAsset> blocks() {
        return blocks;
    }

    public List<BridgedItemAsset> items() {
        return items;
    }

    public boolean isEmpty() {
        return resources.isEmpty() && blocks.isEmpty() && items.isEmpty();
    }
}
