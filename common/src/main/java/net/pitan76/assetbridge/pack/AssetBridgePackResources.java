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
import net.pitan76.assetbridge.asset.AssetSource;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Serves the bridged assets to Minecraft as an in-memory pack.
 *
 * <p>One instance covers one pack root: a resource pack for {@link AssetPath.PackKind#CLIENT}
 * and a data pack for {@link AssetPath.PackKind#SERVER}. They are separate packs because
 * Minecraft keeps a separate repository, and a separate {@code pack_format}, for each.
 */
public class AssetBridgePackResources implements PackResources {
    public static final String PACK_ID = AssetBridge.MOD_ID + "_external";
    public static final String DATA_PACK_ID = AssetBridge.MOD_ID + "_external_data";

    private final AssetBundle bundle;
    private final AssetPath.PackKind kind;
    private final String mcmeta;

    public AssetBridgePackResources(AssetBundle bundle, AssetPath.PackKind kind) {
        this.bundle = bundle;
        this.kind = kind;
        int format = kind == AssetPath.PackKind.SERVER
                ? AssetVersion.CURRENT_DATA_PACK_FORMAT
                : AssetVersion.CURRENT_PACK_FORMAT;
        this.mcmeta = "{\"pack\":{\"pack_format\":" + format
                + ",\"description\":\"Assets bridged from mods/assetbridge/\"}}";
    }

    @Override
    public InputStream getRootResource(String fileName) throws IOException {
        if ("pack.mcmeta".equals(fileName)) {
            return new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8));
        }
        throw new FileNotFoundException(fileName);
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        AssetSource source = serves(type) ? bundle.resources().get(pathOf(type, location)) : null;
        if (source == null) throw new FileNotFoundException(location.toString());
        return source.open();
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        return serves(type) && bundle.resources().containsKey(pathOf(type, location));
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String path,
                                                     int maxDepth, Predicate<String> filter) {
        if (!serves(type)) return List.of();

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
        if (!serves(type)) return Set.of();

        Set<String> namespaces = new LinkedHashSet<>();
        for (AssetPath key : bundle.resources().keySet()) {
            if (key.kind() == kind) namespaces.add(key.namespace());
        }
        return namespaces;
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        JsonObject root = GsonHelper.parse(new StringReader(mcmeta));
        String section = serializer.getMetadataSectionName();
        if (!root.has(section)) return null;
        return serializer.fromJson(GsonHelper.getAsJsonObject(root, section));
    }

    @Override
    public String getName() {
        return kind == AssetPath.PackKind.SERVER ? "Asset Bridge Data" : "Asset Bridge";
    }

    /** This pack only answers for the root it was built for; the other one is a separate pack. */
    private boolean serves(PackType type) {
        return kindOf(type) == kind;
    }

    @Override
    public void close() {
        // A new instance is created on every resource reload, while the archives behind the
        // bundle live as long as the game does, so there is nothing to release here.
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
