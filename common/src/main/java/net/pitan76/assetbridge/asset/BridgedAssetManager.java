package net.pitan76.assetbridge.asset;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Asset Bridge extracted from the external archives, already converted to
 * the format the running Minecraft version expects.
 */
public class BridgedAssetManager {
    // Insertion ordered: what a pack lists, and in which order, must not depend on a hash.
    private final Map<AssetPath, AssetSource> resources = new LinkedHashMap<>();
    private final List<BridgedBlockDefinition> blocks = new ArrayList<>();
    private final List<BridgedItemDefinition> items = new ArrayList<>();
    /** Namespace to the display name its archive declared, when it declared one. */
    private final Map<String, String> modNames = new LinkedHashMap<>();

    /** For resources Asset Bridge produced itself; they exist nowhere on disk. */
    public void putResource(AssetPath path, byte[] data) {
        putResource(path, AssetSource.ofBytes(data));
    }

    /** For resources still backed by their archive, read only when the game asks. */
    public void putResource(AssetPath path, AssetSource source) {
        resources.put(path, source);
    }

    public void addBlock(BridgedBlockDefinition block) {
        blocks.add(block);
    }

    public void addItem(BridgedItemDefinition item) {
        items.add(item);
    }

    /** The first archive to name a namespace wins, matching how resources are claimed. */
    public void addModName(String namespace, String displayName) {
        modNames.putIfAbsent(namespace, displayName);
    }

    /** The name the mod calls itself, or {@code null} if no archive declared one. */
    @Nullable
    public String modName(String namespace) {
        return modNames.get(namespace);
    }

    public Map<String, String> modNames() {
        return modNames;
    }

    public boolean hasResource(AssetPath path) {
        return resources.containsKey(path);
    }

    /** Whether anything at all would be served in that pack root. */
    public boolean hasResources(AssetPath.PackKind kind) {
        return resources.keySet().stream().anyMatch(path -> path.kind() == kind);
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

    public List<BridgedBlockDefinition> blocks() {
        return blocks;
    }

    public List<BridgedItemDefinition> items() {
        return items;
    }

    public java.util.Set<String> namespaces() {
        java.util.Set<String> ns = new java.util.LinkedHashSet<>();
        for (BridgedBlockDefinition block : blocks) {
            net.minecraft.util.ResourceLocation id = net.minecraft.util.ResourceLocation.tryParse(block.id());
            if (id != null) ns.add(id.getNamespace());
        }
        for (BridgedItemDefinition item : items) {
            net.minecraft.util.ResourceLocation id = net.minecraft.util.ResourceLocation.tryParse(item.id());
            if (id != null) ns.add(id.getNamespace());
        }
        return ns;
    }

    public boolean isEmpty() {
        return resources.isEmpty() && blocks.isEmpty() && items.isEmpty();
    }
}
