package net.pitan76.assetbridge.asset;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.util.IdUtil;
import net.pitan76.assetbridge.util.Json;
import net.pitan76.assetbridge.shape.BlockAnalysis;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    public byte[] readResource(AssetPath path) throws IOException {
        AssetSource source = resources.get(path);
        return source == null ? null : source.readAll();
    }

    /**
     * Reads a resource as JSON. Everything that inspects the bundle rather than serving it
     * wants exactly this, so how a missing, unreadable or malformed file is answered is
     * decided once here: with {@code null}, and a line in the log for the file that was there
     * but could not be read.
     */
    @Nullable
    public JsonObject readJson(AssetPath path) {
        try {
            byte[] data = readResource(path);
            return data == null ? null : Json.parse(new String(data, StandardCharsets.UTF_8));
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Could not read {}", path, e);
            return null;
        }
    }

    public List<BridgedBlockDefinition> blocks() {
        return blocks;
    }

    public List<BridgedItemDefinition> items() {
        return items;
    }

    public java.util.Set<String> namespaces() {
        java.util.Set<String> ns = new LinkedHashSet<>();
        for (BridgedBlockDefinition block : blocks) {
            ResourceLocation id = IdUtil.tryParse(block.id());
            if (id != null) ns.add(id.getNamespace());
        }
        for (BridgedItemDefinition item : items) {
            ResourceLocation id = IdUtil.tryParse(item.id());
            if (id != null) ns.add(id.getNamespace());
        }
        return ns;
    }

    public boolean isEmpty() {
        return resources.isEmpty() && blocks.isEmpty() && items.isEmpty();
    }

    @Nullable
    public BlockAnalysis analysis(String id) {
        for (BridgedBlockDefinition block : blocks) {
            if (block.id().equals(id)) return block.analysis();
        }
        return null;
    }
}
