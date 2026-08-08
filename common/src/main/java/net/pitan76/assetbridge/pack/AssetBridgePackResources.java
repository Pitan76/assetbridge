package net.pitan76.assetbridge.pack;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Serves the converted assets to Minecraft as an in-memory resource pack. */
public class AssetBridgePackResources implements PackResources {
    public static final String PACK_ID = AssetBridge.MOD_ID + "_external";

    private static final String PACK_MCMETA = "{\"pack\":{\"pack_format\":" + AssetVersion.CURRENT_PACK_FORMAT
            + ",\"description\":\"Assets bridged from mods/assetbridge/\"}}";

    private final AssetBundle bundle;

    public AssetBridgePackResources(AssetBundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public InputStream getRootResource(String fileName) throws IOException {
        if ("pack.mcmeta".equals(fileName)) {
            return new ByteArrayInputStream(PACK_MCMETA.getBytes(StandardCharsets.UTF_8));
        }
        throw new FileNotFoundException(fileName);
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        byte[] data = bundle.resources().get(pathOf(type, location));
        if (data == null) throw new FileNotFoundException(location.toString());
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        return bundle.resources().containsKey(pathOf(type, location));
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String path,
                                                     int maxDepth, Predicate<String> filter) {
        AssetPath.PackKind kind = kindOf(type);
        String prefix = path.endsWith("/") ? path : path + "/";
        List<ResourceLocation> found = new ArrayList<>();

        for (AssetPath key : bundle.resources().keySet()) {
            if (key.kind() != kind || !key.namespace().equals(namespace)) continue;
            if (!key.path().startsWith(prefix)) continue;

            String relative = key.path().substring(prefix.length());
            // maxDepth counts directory levels below `path`.
            if (countSlashes(relative) >= maxDepth) continue;
            if (!filter.test(fileNameOf(relative))) continue;

            found.add(new ResourceLocation(namespace, key.path()));
        }
        return found;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        AssetPath.PackKind kind = kindOf(type);
        Set<String> namespaces = new HashSet<>();
        for (AssetPath key : bundle.resources().keySet()) {
            if (key.kind() == kind) namespaces.add(key.namespace());
        }
        return namespaces;
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        JsonObject root = GsonHelper.parse(new StringReader(PACK_MCMETA));
        String section = serializer.getMetadataSectionName();
        if (!root.has(section)) return null;
        return serializer.fromJson(GsonHelper.getAsJsonObject(root, section));
    }

    @Override
    public String getName() {
        return "Asset Bridge";
    }

    @Override
    public void close() {
        // Everything is held in memory; nothing to release.
    }

    private static AssetPath pathOf(PackType type, ResourceLocation location) {
        return new AssetPath(kindOf(type), location.getNamespace(), location.getPath());
    }

    private static AssetPath.PackKind kindOf(PackType type) {
        return type == PackType.SERVER_DATA ? AssetPath.PackKind.SERVER : AssetPath.PackKind.CLIENT;
    }

    private static int countSlashes(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '/') count++;
        }
        return count;
    }

    private static String fileNameOf(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? relative : relative.substring(slash + 1);
    }
}
