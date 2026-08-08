package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;
import net.pitan76.assetbridge.asset.BridgedItemAsset;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.convert.AssetConverter;
import net.pitan76.assetbridge.convert.BlockStateConverter;
import net.pitan76.assetbridge.convert.ModelConverter;
import net.pitan76.assetbridge.convert.PassthroughConverter;
import net.pitan76.assetbridge.parse.BlockStateParser;
import net.pitan76.assetbridge.parse.BlockStatePropertyParser;
import net.pitan76.assetbridge.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class AssetPipeline {
    private static final AssetConverter BLOCKSTATES = new BlockStateConverter();
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
        // Item candidates cannot be resolved until every blockstate has been read, because a
        // block item takes precedence over a standalone item of the same id. First one wins.
        Map<String, BridgedItemAsset> itemCandidates = new LinkedHashMap<>();

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
                    case ITEM_MODEL -> {
                        if (convertInto(bundle, MODELS, path, entry.getValue(), version, archive)) {
                            readItem(itemCandidates, archive, version, path);
                        }
                    }
                    case BLOCK_MODEL, MODEL ->
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
            generateItemModel(bundle, block);
            // The block's own item owns this id, so it is not a standalone item.
            itemCandidates.remove(block.id());
        }
        itemCandidates.values().forEach(bundle::addItem);

        AssetBridge.LOGGER.info("Prepared {} bridged blocks, {} items and {} resources from {} archive(s)",
                bundle.blocks().size(), bundle.items().size(), bundle.resources().size(), archives.size());
        return bundle;
    }

    private static void readBlockState(AssetBundle bundle, AssetArchive archive, AssetVersion version,
                                       AssetPath path, byte[] data, Set<String> seenBlocks) {
        String name = path.blockStateName();
        if (name == null) return;
        String id = path.namespace() + ":" + name;

        byte[] converted = BLOCKSTATES.convert(path, data, version);
        JsonObject json = converted == null ? null : Json.parse(new String(converted, StandardCharsets.UTF_8));
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

        BridgedStateDefinition states = BlockStatePropertyParser.parse(json);
        if (states != null) {
            // Every property can be registered, so the original blockstate resolves as-is.
            bundle.putResource(path, converted);
        } else {
            // Passing it through would make the model loader fail on a property we cannot
            // register, so fall back to a single property-free variant.
            AssetBridge.LOGGER.warn("{} in {} uses properties Asset Bridge cannot register; "
                    + "falling back to a single model", id, archive.fileName());
            states = BridgedStateDefinition.empty();
            bundle.putResource(path, Json.toString(BlockStateConverter.singleVariant(model))
                    .getBytes(StandardCharsets.UTF_8));
        }

        bundle.addBlock(new BridgedBlockAsset(path.namespace(), name, qualify(model, path.namespace()),
                states, archive.fileName(), version));
    }

    /** An item model is only worth an item registration if the model itself survived. */
    private static void readItem(Map<String, BridgedItemAsset> candidates, AssetArchive archive,
                                 AssetVersion version, AssetPath path) {
        String name = path.itemModelName();
        if (name == null) return;

        candidates.putIfAbsent(path.namespace() + ":" + name,
                new BridgedItemAsset(path.namespace(), name, archive.fileName(), version));
    }

    private static boolean convertInto(AssetBundle bundle, AssetConverter converter, AssetPath path, byte[] data,
                                       AssetVersion version, AssetArchive archive) {
        byte[] converted = converter.convert(path, data, version);
        if (converted == null) {
            AssetBridge.LOGGER.warn("Dropped unconvertible resource {} from {}", path, archive.fileName());
            return false;
        }
        // An earlier archive claimed this path already; keep the first one.
        if (bundle.hasResource(path)) return false;
        bundle.putResource(path, converted);
        return true;
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
