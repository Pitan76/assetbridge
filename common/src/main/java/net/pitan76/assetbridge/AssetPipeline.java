package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;
import net.pitan76.assetbridge.convert.AssetConverter;
import net.pitan76.assetbridge.convert.ModelConverter;
import net.pitan76.assetbridge.convert.PassthroughConverter;
import net.pitan76.assetbridge.parse.BlockStateParser;
import net.pitan76.assetbridge.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * archive -> parse -> internal representation -> current-version assets.
 *
 * <p>Each archive is read at its own declared asset version and converted independently,
 * so a 1.12 pack and a 1.20 pack can be loaded side by side.
 */
public final class AssetPipeline {
    private static final AssetConverter MODELS = new ModelConverter();
    private static final AssetConverter BINARY = new PassthroughConverter();

    private AssetPipeline() {
    }

    /**
     * @param namespaceInUse namespaces that already belong to a loaded mod (including
     *                       {@code minecraft}). Their assets are skipped entirely, so
     *                       Asset Bridge never shadows a mod the player actually installed.
     */
    public static AssetBundle build(List<AssetArchive> archives, Predicate<String> namespaceInUse) {
        AssetBundle bundle = new AssetBundle();
        Set<String> seenBlocks = new HashSet<>();
        Set<String> skippedNamespaces = new HashSet<>();

        for (AssetArchive archive : archives) {
            AssetVersion version = AssetVersion.fromPackFormat(archive.packFormat()).resolved();

            for (Map.Entry<AssetPath, byte[]> entry : archive.entries().entrySet()) {
                AssetPath path = entry.getKey();

                if (namespaceInUse.test(path.namespace())) {
                    if (skippedNamespaces.add(path.namespace())) {
                        AssetBridge.LOGGER.info("Skipping namespace '{}' from {}: it is already provided by a loaded mod",
                                path.namespace(), archive.fileName());
                    }
                    continue;
                }

                switch (path.category()) {
                    case BLOCKSTATE -> readBlockState(bundle, archive, version, path, entry.getValue(), seenBlocks);
                    case BLOCK_MODEL, ITEM_MODEL, MODEL ->
                            convertInto(bundle, MODELS, path, entry.getValue(), version, archive);
                    case TEXTURE, TEXTURE_META, LANG ->
                            convertInto(bundle, BINARY, path, entry.getValue(), version, archive);
                    case OTHER -> {
                        // Filtered out at scan time; nothing to do.
                    }
                }
            }
        }

        for (BridgedBlockAsset block : bundle.blocks()) {
            generateBlockState(bundle, block);
            generateItemModel(bundle, block);
        }

        AssetBridge.LOGGER.info("Prepared {} bridged blocks and {} resources from {} archive(s)",
                bundle.blocks().size(), bundle.resources().size(), archives.size());
        return bundle;
    }

    private static void readBlockState(AssetBundle bundle, AssetArchive archive, AssetVersion version,
                                       AssetPath path, byte[] data, Set<String> seenBlocks) {
        String name = path.blockStateName();
        if (name == null) return;
        String id = path.namespace() + ":" + name;

        JsonObject json = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (json == null) {
            AssetBridge.LOGGER.warn("Skipping unreadable blockstate {} in {}", id, archive.fileName());
            return;
        }
        String model = BlockStateParser.findModel(json);
        if (model == null) {
            AssetBridge.LOGGER.warn("Skipping blockstate {} in {}: no model reference found", id, archive.fileName());
            return;
        }
        if (!seenBlocks.add(id)) {
            AssetBridge.LOGGER.warn("Skipping duplicate block {} from {}", id, archive.fileName());
            return;
        }
        bundle.addBlock(new BridgedBlockAsset(path.namespace(), name, qualify(model, path.namespace()),
                archive.fileName(), version));
    }

    private static void convertInto(AssetBundle bundle, AssetConverter converter, AssetPath path, byte[] data,
                                    AssetVersion version, AssetArchive archive) {
        byte[] converted = converter.convert(path, data, version);
        if (converted == null) {
            AssetBridge.LOGGER.warn("Dropped unconvertible resource {} from {}", path, archive.fileName());
            return;
        }
        bundle.putResource(path, converted);
    }

    /** A blockstate with no properties: exactly one variant, so the block renders without state handling. */
    private static void generateBlockState(AssetBundle bundle, BridgedBlockAsset block) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", block.modelId());
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject root = new JsonObject();
        root.add("variants", variants);

        bundle.putResource(AssetPath.blockState(block.namespace(), block.path()),
                Json.toString(root).getBytes(StandardCharsets.UTF_8));
    }

    /** Only generated when the archive did not already ship an item model for the block. */
    private static void generateItemModel(AssetBundle bundle, BridgedBlockAsset block) {
        AssetPath path = AssetPath.itemModel(block.namespace(), block.path());
        if (bundle.hasResource(path)) return;

        JsonObject root = new JsonObject();
        root.addProperty("parent", block.modelId());
        bundle.putResource(path, Json.toString(root).getBytes(StandardCharsets.UTF_8));
    }

    private static String qualify(String reference, String namespace) {
        return reference.indexOf(':') < 0 ? namespace + ":" + reference : reference;
    }
}
