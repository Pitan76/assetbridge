package net.pitan76.assetbridge.asset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Asset Bridge extracted from the external archives, already converted to
 * the format the running Minecraft version expects.
 */
public final class AssetBundle {
    private final Map<String, byte[]> resources = new HashMap<>();
    private final List<BridgedBlockAsset> blocks = new ArrayList<>();

    /** @param path full pack path, e.g. {@code assets/examplemod/models/block/foo.json} */
    public void putResource(String path, byte[] data) {
        resources.put(path, data);
    }

    public void addBlock(BridgedBlockAsset block) {
        blocks.add(block);
    }

    public boolean hasResource(String path) {
        return resources.containsKey(path);
    }

    public Map<String, byte[]> resources() {
        return resources;
    }

    public List<BridgedBlockAsset> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return resources.isEmpty() && blocks.isEmpty();
    }
}
