package net.pitan76.assetbridge.pack;

import com.google.gson.JsonObject;
// Only the >=26 branch of getMetadataSection uses this, but the class exists in every supported
// version, so importing it unconditionally is cheaper than a preprocessor block.
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.RuntimePack;
import net.pitan76.assetbridge.util.IdUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/**
 * Serves the bridged assets to Minecraft as an in-memory pack.
 *
 * <p>
 * One instance covers one pack root: a resource pack for
 * {@link AssetPath.PackKind#CLIENT}
 * and a data pack for {@link AssetPath.PackKind#SERVER}. They are separate
 * packs because
 * Minecraft keeps a separate repository, and a separate {@code pack_format},
 * for each.
 */
public class AssetBridgePackResources implements PackResources {
    public static final String PACK_ID = AssetBridge.MOD_ID + "_external";
    public static final String DATA_PACK_ID = AssetBridge.MOD_ID + "_external_data";

    private final BridgedAssetManager assets;
    private final AssetPath.PackKind kind;
    private final String mcmeta;

    public AssetBridgePackResources(BridgedAssetManager assets, AssetPath.PackKind kind) {
        this.assets = assets;
        this.kind = kind;
        int format = kind == AssetPath.PackKind.SERVER
                ? RuntimePack.dataPackFormat()
                : RuntimePack.resourcePackFormat();
        int formatMinor = kind == AssetPath.PackKind.SERVER
                ? RuntimePack.dataPackFormatMinor()
                : RuntimePack.resourcePackFormatMinor();
        //? if >=26 {
        /*// 26.1 split pack_format into major (pack_format) and minor (pack_format_minor).
        // PackFormat's Codec requires both fields; a missing pack_format_minor causes the
        // metadata section to fail parsing and Pack.readMetaAndCreate to return null.
        this.mcmeta = "{\"pack\":{\"pack_format\":" + format
                + ",\"pack_format_minor\":" + formatMinor
                + ",\"min_format\":" + format
                + ",\"max_format\":" + format
                + ",\"description\":\"Assets bridged from mods/assetbridge/\"}}";
        *///?} else {
        this.mcmeta = "{\"pack\":{\"pack_format\":" + format
                + ",\"description\":\"Assets bridged from mods/assetbridge/\"}}";
        //?}
    }

    /**
     * Looks up a resource, falling back to the pre-flattening directory name.
     * Version-independent: every override below is expressed in terms of this.
     */
    @Nullable
    private AssetSource sourceOf(PackType type, ResourceLocation location) {
        if (!serves(type))
            return null;
        AssetPath path = pathOf(type, location);
        AssetSource source = assets.resources().get(path);
        if (source != null)
            return source;
        AssetPath alt = getAlternativePath(path);
        return alt == null ? null : assets.resources().get(alt);
    }

    private InputStream openMcmeta() {
        return new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8));
    }

    /** The name shown in the pack list. */
    private String displayName() {
        return kind == AssetPath.PackKind.SERVER ? "Asset Bridge Data" : "Asset Bridge";
    }

    // ---------------------------------------------------------------------------
    // The PackResources surface. 1.20 rewrote it: lookups return an IoSupplier and
    // report absence with null instead of throwing, listing became a callback, and
    // getName() became packId(). Only these wrappers differ; the logic above does
    // not.
    // ---------------------------------------------------------------------------

    //? if >=1.20 {
    /*@Override
    @Nullable
    public net.minecraft.server.packs.resources.IoSupplier<InputStream> getRootResource(String... elements) {
        return elements.length == 1 && "pack.mcmeta".equals(elements[0]) ? this::openMcmeta : null;
    }

    @Override
    @Nullable
    public net.minecraft.server.packs.resources.IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (location.getPath().contains("lang")) {
            AssetBridge.LOGGER.info("getResource (lang): location={}", location);
        }
        AssetSource source = sourceOf(type, location);
        if (source == null) {
            if (location.getPath().contains("lang")) {
                AssetBridge.LOGGER.info("getResource (lang) NOT FOUND: location={}", location);
            }
            return null;
        }
        if (location.getPath().contains("lang")) {
            AssetBridge.LOGGER.info("getResource (lang) FOUND: location={}", location);
        }
        return source::open;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (path.contains("lang")) {
            AssetBridge.LOGGER.info("listResources (lang): namespace={}, path={}", namespace, path);
        }
        for (ResourceLocation id : collectResources(type, namespace, path, Integer.MAX_VALUE)) {
            AssetSource source = sourceOf(type, id);
            if (source != null) {
                if (path.contains("lang")) {
                    AssetBridge.LOGGER.info("listResources found: {}", id);
                }
                output.accept(id, source::open);
            }
        }
    }

    *///?} else {
    @Override
    public InputStream getRootResource(String fileName) throws IOException {
        if ("pack.mcmeta".equals(fileName))
            return openMcmeta();
        throw new FileNotFoundException(fileName);
    }

    @Override
    public @NotNull InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        AssetSource source = sourceOf(type, location);
        if (source == null)
            throw new FileNotFoundException(location.toString());
        return source.open();
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        return sourceOf(type, location) != null;
    }
    //?}

    // How the pack names itself: getName() up to 1.19, packId() in 1.20,
    // and a PackLocationInfo record from 1.21. A separate chain, because Java block
    // comments do not nest and the block above is already commented out per
    // version.
    //? if >=1.21 {
    /*@Override
    public net.minecraft.server.packs.PackLocationInfo location() {
        return new net.minecraft.server.packs.PackLocationInfo(
                packName(),
                net.minecraft.network.chat.Component.literal(displayName()),
                net.minecraft.server.packs.repository.PackSource.BUILT_IN,
                java.util.Optional.empty());
    }
    *///?} elif >=1.20 {
    /*@Override
    public String packId() {
        return displayName();
    }
    *///?} else {
    @Override
    public @NotNull String getName() {
        return displayName();
    }
    //?}

    @Nullable
    private AssetPath getAlternativePath(AssetPath original) {
        if (original.kind() != AssetPath.PackKind.CLIENT) return null;

        String path = original.path();
        if (path.startsWith("textures/block/")) {
            return new AssetPath(original.kind(), original.namespace(),
                    "textures/blocks/" + path.substring("textures/block/".length()));
        }
        if (path.startsWith("textures/item/")) {
            return new AssetPath(original.kind(), original.namespace(),
                    "textures/items/" + path.substring("textures/item/".length()));
        }
        return null;
    }

    /**
     * Collects every bridged resource under {@code path}, version-independently.
     *
     * <p>
     * The {@code getResources} override below is the only part that differs between
     * Minecraft versions, so the traversal lives here and the overrides stay thin.
     *
     * @param maxDepth directory levels below {@code path} to descend; pass
     *                 {@link Integer#MAX_VALUE} on versions that dropped the limit
     */
    private List<ResourceLocation> collectResources(PackType type, String namespace, String path, int maxDepth) {
        if (!serves(type)) return Collections.emptyList();

        String prefix = path.endsWith("/") ? path : path + "/";
        List<ResourceLocation> found = new ArrayList<>();

        for (AssetPath key : assets.resources().keySet()) {
            if (key.kind() != kind || !key.namespace().equals(namespace)) continue;
            if (!key.path().startsWith(prefix)) continue;

            String relative = key.path().substring(prefix.length());
            if (countSlashes(relative) >= maxDepth) continue;

            try {
                found.add(IdUtil.of(namespace, key.path()));
            } catch (Exception e) {
                // Ignore files with invalid characters in path
            }
        }
        return found;
    }

    //? if >=1.20 {
    /*// Replaced by listResources above.
    *///?} elif >=1.19 {
    /*@Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String path,
                                                     Predicate<ResourceLocation> filter) {
        // 1.19 dropped the depth limit and moved the filter onto the full identifier.
        List<ResourceLocation> found = new ArrayList<>();
        for (ResourceLocation id : collectResources(type, namespace, path, Integer.MAX_VALUE)) {
            if (filter.test(id)) found.add(id);
        }
        return found;
    }
    *///?} else {
    @Override
    public @NotNull Collection<ResourceLocation> getResources(PackType type, String namespace, String path,
                                                              int maxDepth, Predicate<String> filter) {
        List<ResourceLocation> found = new ArrayList<>();
        for (ResourceLocation id : collectResources(type, namespace, path, maxDepth)) {
            if (filter.test(fileNameOf(id.getPath()))) found.add(id);
        }
        return found;
    }
    //?}

    @Override
    public @NotNull Set<String> getNamespaces(PackType type) {
        if (!serves(type)) return new HashSet<>();

        Set<String> namespaces = new LinkedHashSet<>();
        for (AssetPath key : assets.resources().keySet()) {
            if (key.kind() == kind)
                namespaces.add(key.namespace());
        }
        return namespaces;
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        JsonObject root = GsonHelper.parse(new StringReader(mcmeta));
        //? if >=26 {
        /*// 26.1 turned the serializer into a record carrying a Codec, so the section name and the
        // deserialisation both come from different members than before.
        String section = serializer.name();
        if (!root.has(section)) return null;
        return serializer.codec()
                .parse(JsonOps.INSTANCE, GsonHelper.getAsJsonObject(root, section))
                .resultOrPartial(err -> AssetBridge.LOGGER.error("Failed to parse metadata section '{}' (JSON: {}): {}", section, root, err))
                .orElse(null);
        *///?} else {
        String section = serializer.getMetadataSectionName();
        if (!root.has(section)) return null;
        return serializer.fromJson(GsonHelper.getAsJsonObject(root, section));
        //?}
    }

    /**
     * This pack only answers for the root it was built for; the other one is a
     * separate pack.
     */
    private boolean serves(PackType type) {
        return kindOf(type) == kind;
    }

    @Override
    public void close() {
        // A new instance is created on every resource reload, while the archives behind
        // the
        // bundle live as long as the game does, so there is nothing to release here.
    }

    /** The pack's registry id, distinct from the name shown to the player. */
    private String packName() {
        return kind == AssetPath.PackKind.SERVER ? DATA_PACK_ID : PACK_ID;
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
            if (value.charAt(i) == '/')
                count++;
        }
        return count;
    }

    private static String fileNameOf(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? relative : relative.substring(slash + 1);
    }
}
