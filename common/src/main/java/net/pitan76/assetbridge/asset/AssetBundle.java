package net.pitan76.assetbridge.asset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Asset Bridge extracted from the external archives, already converted to
 * the format the running Minecraft version expects.
 */
public class AssetBundle {
    private final Map<AssetPath, byte[]> resources = new HashMap<>();
    private final List<BridgedBlockAsset> blocks = new ArrayList<>();
    private final List<BridgedItemAsset> items = new ArrayList<>();

    public void putResource(AssetPath path, byte[] data) {
        resources.put(path, data);
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

    public Map<AssetPath, byte[]> resources() {
        return resources;
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
