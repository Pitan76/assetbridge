package net.pitan76.assetbridge;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
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

    public static AssetBundle build(List<AssetArchive> archives) {
        AssetBundle bundle = new AssetBundle();
        Set<String> seenBlocks = new HashSet<>();

        for (AssetArchive archive : archives) {
            AssetVersion version = AssetVersion.fromPackFormat(archive.packFormat()).resolved();

            for (Map.Entry<String, byte[]> entry : archive.entries().entrySet()) {
                String path = entry.getKey();
                String namespace = namespaceOf(path);
                if (namespace == null) continue;
                String rest = path.substring("assets/".length() + namespace.length() + 1);

                if (rest.startsWith("blockstates/") && rest.endsWith(".json")) {
                    readBlockState(bundle, archive, version, namespace, rest, entry.getValue(), seenBlocks);
                } else if (rest.startsWith("models/") && rest.endsWith(".json")) {
                    convertInto(bundle, MODELS, path, entry.getValue(), version, archive);
                } else if (rest.startsWith("textures/") || rest.startsWith("lang/")) {
                    convertInto(bundle, BINARY, path, entry.getValue(), version, archive);
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
                                       String namespace, String rest, byte[] data, Set<String> seenBlocks) {
        String name = rest.substring("blockstates/".length(), rest.length() - ".json".length());
        String id = namespace + ":" + name;

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
        bundle.addBlock(new BridgedBlockAsset(namespace, name, qualify(model, namespace), archive.fileName(), version));
    }

    private static void convertInto(AssetBundle bundle, AssetConverter converter, String path, byte[] data,
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

        bundle.putResource("assets/" + block.namespace() + "/blockstates/" + block.path() + ".json",
                Json.toString(root).getBytes(StandardCharsets.UTF_8));
    }

    /** Only generated when the archive did not already ship an item model for the block. */
    private static void generateItemModel(AssetBundle bundle, BridgedBlockAsset block) {
        String path = "assets/" + block.namespace() + "/models/item/" + block.path() + ".json";
        if (bundle.hasResource(path)) return;

        JsonObject root = new JsonObject();
        root.addProperty("parent", block.modelId());
        bundle.putResource(path, Json.toString(root).getBytes(StandardCharsets.UTF_8));
    }

    private static String qualify(String reference, String namespace) {
        return reference.indexOf(':') < 0 ? namespace + ":" + reference : reference;
    }

    private static String namespaceOf(String path) {
        if (!path.startsWith("assets/")) return null;
        int end = path.indexOf('/', "assets/".length());
        return end < 0 ? null : path.substring("assets/".length(), end);
    }
}
