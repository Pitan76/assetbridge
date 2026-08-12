package net.pitan76.assetbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.*;
import net.pitan76.assetbridge.convert.AssetConverter;
import net.pitan76.assetbridge.convert.AtlasSources;
import net.pitan76.assetbridge.convert.BlockStateConverter;
import net.pitan76.assetbridge.convert.ModelConverter;
import net.pitan76.assetbridge.convert.ModelReferenceResolver;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.parse.BlockStateCoverage;
import net.pitan76.assetbridge.parse.BlockStateParser;
import net.pitan76.assetbridge.parse.BlockStatePropertyParser;
import net.pitan76.assetbridge.util.Json;
import net.pitan76.assetbridge.util.IdUtil;

import java.io.IOException;
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
public class AssetPipeline {
    private static final AssetConverter BLOCKSTATES = new BlockStateConverter();
    private static final AssetConverter MODELS = new ModelConverter();

    private AssetPipeline() {
    }

    /**
     * @param isNamespaceUsed namespaces that already belong to a loaded mod (including
     *                       {@code minecraft}). Their assets are skipped entirely, so
     *                       Asset Bridge never shadows a mod the player actually installed.
     */
    public static BridgedAssetManager build(List<AssetArchive> archives, Predicate<String> isNamespaceUsed) {
        BridgedAssetManager assets = new BridgedAssetManager();
        Set<String> seenBlocks = new HashSet<>();
        Set<String> skippedNamespaces = new HashSet<>();
        ItemCandidates itemCandidates = new ItemCandidates();

        for (AssetArchive archive : archives) {
            archive.modNames().forEach((namespace, name) -> {
                if (!isNamespaceUsed.test(namespace)) assets.addModName(namespace, name);
            });
            for (Map.Entry<AssetPath, AssetSource> entry : archive.entries().entrySet()) {
                AssetPath path = entry.getKey();

                if (isNamespaceUsed.test(path.namespace())) {
                    if (skippedNamespaces.add(path.namespace())) {
                        AssetBridge.LOGGER.info("Skipping namespace '{}' from {}: it is already provided by a loaded mod",
                                path.namespace(), archive.fileName());
                    }
                    continue;
                }
                read(assets, archive, path, entry.getValue(), seenBlocks, itemCandidates);
            }
        }

        Set<String> blockIds = new HashSet<>();
        for (BridgedBlockDefinition block : assets.blocks()) {
            generateItemModel(assets, block);
            // The block's own item owns this id, so it is not a standalone item.
            blockIds.add(block.id());
        }
        itemCandidates.resolve(blockIds).forEach(assets::addItem);

        // Last, because a model may inherit from one that another archive supplies.
        int repaired = ModelReferenceResolver.resolve(assets);
        if (repaired > 0) {
            AssetBridge.LOGGER.info("Replaced {} model(s) whose parent was not available", repaired);
        }

        // After the models are final, so a stand-in's textures are the ones declared.
        AtlasSources.declare(assets);

        // Inject render_type into block model JSON for blocks that need cutout rendering.
        // This runs last so every model is fully resolved before we patch it.
        // On pre-26 Minecraft the field is not understood and is silently ignored, so the
        // injection is harmless across all versions.
        int injected = injectRenderTypes(assets);
        if (injected > 0) {
            AssetBridge.LOGGER.info("Injected render_type=cutout into {} block model(s) for pre-26 assets", injected);
        }

        // Generate 1.21.4+ standalone item definition JSONs from item models.
        generateItemDefinitions(assets);

        AssetBridge.LOGGER.info("Prepared {} bridged blocks, {} items and {} resources from {} archive(s)",
                assets.blocks().size(), assets.items().size(), assets.resources().size(), archives.size());
        return assets;
    }

    /** Routes one archive entry to whatever its category needs, if anything. */
    private static void read(BridgedAssetManager assets, AssetArchive archive, AssetPath path, AssetSource source,
                             Set<String> seenBlocks, ItemCandidates itemCandidates) {
        AssetVersion version = archive.version();
        switch (path.category()) {
            case BLOCKSTATE -> readBlockState(assets, archive, version, path, source, seenBlocks);
            case ITEM_DEFINITION ->
                    // The file itself means nothing to this Minecraft version, so it is not
                    // served; only its name is used, as the mod's own list of items.
                    itemCandidates.addDefinition(path.namespace(), path.itemDefinitionName(),
                            archive.fileName(), version);
            case ITEM_MODEL -> {
                if (convertInto(assets, MODELS, path, source, version, archive)) {
                    itemCandidates.addModel(path.namespace(), path.itemModelName(), archive.fileName(), version);
                }
            }
            case BLOCK_MODEL, MODEL -> convertInto(assets, MODELS, path, source, version, archive);
            case TEXTURE, TEXTURE_META -> {
                // Nothing to convert, so the bytes never have to enter the heap: the archive
                // serves them when the game asks. An earlier archive claiming the path still wins.
                // Pre-1.13 texture directories are flattened here so the sprite ends up where
                // the atlas looks for it; ModelConverter rewrites the references to match.
                AssetPath target = path.flattened();
                if (!assets.hasResource(target)) assets.putResource(target, source);
            }
            case LANG -> {
                AssetPath target = path.flattened();
                if (target.path().endsWith(".lang")) {
                    String newPath = target.path().substring(0, target.path().length() - ".lang".length()) + ".json";
                    AssetPath jsonTarget = target.withPath(newPath);
                    if (!assets.hasResource(jsonTarget)) {
                        byte[] raw = readBytes(source, path, archive);
                        if (raw != null) {
                            byte[] jsonBytes = convertLangToJson(raw, jsonTarget.namespace());
                            AssetBridge.LOGGER.info("Converted .lang to json: namespace={}, path={}, bytes={}", jsonTarget.namespace(), jsonTarget.path(), jsonBytes.length);
                            assets.putResource(jsonTarget, jsonBytes);
                        }
                    }
                } else {
                    if (!assets.hasResource(target)) {
                        AssetBridge.LOGGER.info("Registered existing json lang: namespace={}, path={}", target.namespace(), target.path());
                        assets.putResource(target, source);
                    }
                }
            }
            case TAG -> {
                boolean isBlockTag = path.path().startsWith("tags/blocks/") || path.path().startsWith("tags/block/");
                String prefix;
                if (isBlockTag) {
                    prefix = path.path().startsWith("tags/blocks/") ? "tags/blocks/" : "tags/block/";
                } else {
                    prefix = path.path().startsWith("tags/items/") ? "tags/items/" : "tags/item/";
                }

                String subPath = path.path().substring(prefix.length(), path.path().length() - ".json".length());
                String originalTag = path.namespace() + ":" + subPath;

                boolean toFabric = net.pitan76.assetbridge.convert.RecipeConverter.isFabric();
                String mappedTag = net.pitan76.assetbridge.convert.RecipeConverter.mapTagName(originalTag, toFabric);

                int colon = mappedTag.indexOf(':');
                String newNamespace = mappedTag.substring(0, colon);
                String newSubPath = mappedTag.substring(colon + 1);

                // ターゲット環境のバージョンに合わせて、タグの保存ディレクトリ名をマッピング
                boolean isModernTagDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
                String targetPrefix;
                if (isBlockTag) {
                    targetPrefix = isModernTagDir ? "tags/block/" : "tags/blocks/";
                } else {
                    targetPrefix = isModernTagDir ? "tags/item/" : "tags/items/";
                }
                AssetPath target = new AssetPath(AssetPath.PackKind.SERVER, newNamespace, targetPrefix + newSubPath + ".json");

                if (!assets.hasResource(target)) {
                    byte[] raw = readBytes(source, path, archive);
                    if (raw != null) {
                        byte[] converted = convertTagJson(raw, toFabric);
                        assets.putResource(target, converted);
                    }
                }
            }
            case LOOT_TABLE -> {
                boolean isModernLootDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
                AssetPath target = path;
                if (isModernLootDir) {
                    if (path.path().startsWith("loot_tables/")) {
                        String newPath = "loot_table/" + path.path().substring("loot_tables/".length());
                        target = new AssetPath(path.kind(), path.namespace(), newPath);
                    }
                } else {
                    if (path.path().startsWith("loot_table/")) {
                        String newPath = "loot_tables/" + path.path().substring("loot_table/".length());
                        target = new AssetPath(path.kind(), path.namespace(), newPath);
                    }
                }

                if (!assets.hasResource(target)) {
                    byte[] raw = readBytes(source, path, archive);
                    if (raw != null) {
                        assets.putResource(target, raw);
                    }
                }
            }
            case RECIPE -> {
                // Server-side data is a feature's business, not the core's;
                // RecipeFeature reads these straight from the archive.
            }
            case OTHER -> {
                // Filtered out at scan time; nothing to do.
            }
        }
    }

    private static void readBlockState(BridgedAssetManager assets, AssetArchive archive, AssetVersion version,
                                       AssetPath path, AssetSource source, Set<String> seenBlocks) {
        String name = path.blockStateName();
        if (name == null) return;
        String id = path.namespace() + ":" + name;

        byte[] data = readBytes(source, path, archive);
        if (data == null) return;

        byte[] converted = BLOCKSTATES.convert(path, data, version);
        JsonObject json = converted == null ? null : Json.parse(new String(converted, StandardCharsets.UTF_8));
        if (json == null) {
            AssetBridge.LOGGER.warn("Skipping unreadable blockstate {} in {}", id, archive.fileName());
            return;
        }
        JsonElement variant = BlockStateParser.findVariant(json);
        String model = BlockStateParser.findModel(json);
        if (variant == null || model == null) {
            AssetBridge.LOGGER.warn("Skipping blockstate {} in {}: no model reference found", id, archive.fileName());
            return;
        }
        if (!seenBlocks.add(id)) {
            AssetBridge.LOGGER.warn("Skipping duplicate block {} from {}", id, archive.fileName());
            return;
        }

        BridgedStateDefinition states = BlockStatePropertyParser.parse(json);
        if (states != null) {
            // Every property can be registered, so the original blockstate resolves as-is —
            // except for states the file never described, which would render as the missing
            // model. Those are pointed at the block's representative variant instead.
            JsonObject completed = BlockStateCoverage.complete(json, states, variant);
            if (completed == null) {
                assets.putResource(path, converted);
            } else {
                AssetBridge.LOGGER.info("Filled {} uncovered state(s) of {} in {} with a fallback model",
                        BlockStateCoverage.missingCount(json, completed), id, archive.fileName());
                assets.putResource(path, Json.toString(completed).getBytes(StandardCharsets.UTF_8));
            }
        } else {
            // Passing it through would make the model loader fail on a property we cannot
            // register, so fall back to a single property-free variant.
            AssetBridge.LOGGER.warn("{} in {} uses properties Asset Bridge cannot register; "
                    + "falling back to a single model", id, archive.fileName());
            states = BridgedStateDefinition.empty();
            assets.putResource(path, Json.toString(BlockStateConverter.singleVariant(variant))
                    .getBytes(StandardCharsets.UTF_8));
        }

        assets.addBlock(new BridgedBlockDefinition(path.namespace(), name, qualify(model, path.namespace()),
                states, archive.fileName(), version));
    }

    private static boolean convertInto(BridgedAssetManager assets, AssetConverter converter, AssetPath path, AssetSource source,
                                       AssetVersion version, AssetArchive archive) {
        byte[] data = readBytes(source, path, archive);
        if (data == null) return false;

        byte[] converted = converter.convert(path, data, version);
        if (converted == null) {
            AssetBridge.LOGGER.warn("Dropped unconvertible resource {} from {}", path, archive.fileName());
            return false;
        }
        // An earlier archive claimed this path already; keep the first one.
        if (assets.hasResource(path)) return false;
        assets.putResource(path, converted);
        return true;
    }

    /** Only generated when the archive did not already ship an item model for the block. */
    private static void generateItemModel(BridgedAssetManager assets, BridgedBlockDefinition block) {
        AssetPath path = AssetPath.itemModel(block.namespace(), block.path());
        if (assets.hasResource(path)) return;

        JsonObject root = new JsonObject();
        root.addProperty("parent", block.modelId());
        assets.putResource(path, Json.toString(root).getBytes(StandardCharsets.UTF_8));
    }

    /** Resources that have to be converted are read once, here, and never again. */
    private static byte[] readBytes(AssetSource source, AssetPath path, AssetArchive archive) {
        try {
            return source.readAll();
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Could not read {} from {}", path, archive.fileName(), e);
            return null;
        }
    }

    private static String qualify(String reference, String namespace) {
        return reference.indexOf(':') < 0 ? namespace + ":" + reference : reference;
    }

    /**
     * Injects {@code "render_type": "minecraft:cutout"} into the representative model JSON
     * of every bridged block whose id matches the cutout heuristic, provided the
     * {@link CutoutBlocksFeature} is enabled.
     *
     * <p>On Minecraft 26.1+ the per-block render layer API was removed; the game reads the
     * render type from the block model JSON instead. On older versions the field is unknown
     * and silently ignored, so patching all versions is safe.
     *
     * <p>Must run after all models are fully resolved so that the representative model the
     * blockstate refers to is guaranteed to be present in {@code assets}.
     *
     * @return the number of models that were patched
     */
    private static int injectRenderTypes(BridgedAssetManager assets) {
        if (!Features.isEnabled(CutoutBlocksFeature.ID)) return 0;

        int count = 0;
        for (BridgedBlockDefinition block : assets.blocks()) {
            if (!isCutoutById(block.id())) continue;

            AssetPath modelPath = AssetPath.fromModelId(block.modelId());
            if (modelPath == null) continue;

            byte[] existing;
            try {
                existing = assets.readResource(modelPath);
            } catch (IOException e) {
                AssetBridge.LOGGER.warn("Could not read model {} for render_type injection", modelPath, e);
                continue;
            }
            if (existing == null) continue;

            JsonObject model = Json.parse(new String(existing, StandardCharsets.UTF_8));
            if (model == null) continue;
            // Respect an explicit declaration in the original asset.
            if (model.has("render_type")) continue;

            model.addProperty("render_type", "minecraft:cutout");
            assets.putResource(modelPath, Json.toString(model).getBytes(StandardCharsets.UTF_8));
            count++;
        }
        return count;
    }

    /**
     * Mirrors the name-based heuristic in {@code BridgedBlocks.isCutout()}, but operates
     * on a plain {@code namespace:path} string so that no Minecraft types are needed here.
     * The pipeline runs before block instances exist, but the decision only depends on the
     * block's id, which is already known.
     */
    private static boolean isCutoutById(String id) {
        String configVal = Features.getConfigValue(CutoutBlocksFeature.ID);
        if (configVal == null) return false;
        String trimmed = configVal.trim();
        if (trimmed.equalsIgnoreCase("false")) return false;
        int colon = id.indexOf(':');
        String path = (colon < 0 ? id : id.substring(colon + 1)).toLowerCase(java.util.Locale.ROOT);
        if (trimmed.equalsIgnoreCase("true")) {
            return path.contains("leaves")
                    || path.contains("glass")
                    || path.contains("sapling")
                    || path.contains("crop")
                    || path.contains("flower")
                    || path.contains("plant")
                    || path.contains("cutout")
                    || path.contains("portal");
        }
        // Comma-separated explicit id list.
        for (String targetId : trimmed.split(",")) {
            if (targetId.trim().equals(id)) return true;
        }
        return false;
    }

    /**
     * Generates a 1.21.4+ item definition JSON for every registered item, pointing to its
     * corresponding item model JSON.
     *
     * <p>On 1.21.4+ (ITEM_DEFINITIONS), item models are no longer loaded implicitly. Minecraft
     * instead requires an item definition file under {@code assets/namespace/items/item_name.json}
     * describing the model configuration for each item registry entry.
     */
    private static void generateItemDefinitions(BridgedAssetManager assets) {
        if (!RuntimePack.generation().isAtLeast(AssetVersion.ITEM_DEFINITIONS)) return;

        int generated = 0;
        for (BridgedItemDefinition item : assets.items()) {
            generated += generateSingleItemDefinition(assets, item.id());
        }
        for (BridgedBlockDefinition block : assets.blocks()) {
            generated += generateSingleItemDefinition(assets, block.id());
        }
        if (generated > 0) {
            AssetBridge.LOGGER.info("Generated {} item definition(s) for 1.21.4+ (ITEM_DEFINITIONS)", generated);
        }
    }

    private static int generateSingleItemDefinition(BridgedAssetManager assets, String itemId) {
        ResourceLocation id = IdUtil.tryParse(itemId);
        if (id == null) return 0;

        String namespace = id.getNamespace();
        String name = id.getPath();

        AssetPath defPath = AssetPath.itemDefinition(namespace, name);
        if (assets.hasResource(defPath)) return 0;

        JsonObject root = new JsonObject();
        JsonObject modelObj = new JsonObject();
        modelObj.addProperty("type", "minecraft:model");
        modelObj.addProperty("model", namespace + ":item/" + name);
        root.add("model", modelObj);

        assets.putResource(defPath, Json.toString(root).getBytes(StandardCharsets.UTF_8));
        return 1;
    }

    private static String convertLangKey(String key, String modid) {
        /*
        #ja_JP
        tile.example.name=Example Block -> block.<modid>.example=Example Block
        tile.exam.0.name=Example 0 Block -> block.<modid>.exam=Example 0 Block
        tile.exam.1.name=Example 1 Block // TODO: meta値に対応していないため、とりあえず保留
        item.example.name=Example Item
        item.exam.0.name=Example 0 Item
        item.exam.1.name=Example 1 Item
         */

        if (key.startsWith("tile.")) {
            key = key.substring("tile.".length());
            int dotIndex = key.indexOf('.');
            if (dotIndex != -1) {
                String blockName = key.substring(0, dotIndex);
                String suffix = key.substring(dotIndex + 1);
                if (suffix.equals("name")) {
                    key = "block." + modid + "." + blockName;
                } else if (suffix.equals("0.name")) {
                    // TODO: meta値に対応していないため、とりあえず0の場合はそのまま変換する
                    key = "block." + modid + "." + blockName;
                } else {
                    // TODO: meta値に対応していないため、とりあえず保留
                    key = "block." + modid + "." + blockName + suffix;
                }
            }
        } else if (key.startsWith("item.")) {
            key = key.substring("item.".length());
            int dotIndex = key.indexOf('.');
            if (dotIndex != -1) {
                String itemName = key.substring(0, dotIndex);
                String suffix = key.substring(dotIndex + 1);
                if (suffix.equals("name")) {
                    key = "item." + modid + "." + itemName;
                } else {
                    key = "item." + modid + "." + itemName + suffix;
                }
            }
        }

        return key;
    }

    private static byte[] convertLangToJson(byte[] raw, String modid) {
        String content = new String(raw, StandardCharsets.UTF_8);
        JsonObject json = new JsonObject();
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();

                String convertedKey = convertLangKey(key, modid);

                json.addProperty(convertedKey, value);
            }
        }
        return Json.toString(json).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] convertTagJson(byte[] raw, boolean toFabric) {
        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            JsonObject json = Json.parse(text);
            if (json == null || !json.has("values")) return raw;
            JsonElement valuesEl = json.get("values");
            if (valuesEl.isJsonArray()) {
                com.google.gson.JsonArray values = valuesEl.getAsJsonArray();
                com.google.gson.JsonArray newValues = new com.google.gson.JsonArray();
                for (JsonElement el : values) {
                    if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                        String val = el.getAsString();
                        boolean isTagRef = val.startsWith("#");
                        String cleanVal = isTagRef ? val.substring(1) : val;
                        String mapped = net.pitan76.assetbridge.convert.RecipeConverter.mapTagName(cleanVal, toFabric);
                        newValues.add(isTagRef ? "#" + mapped : mapped);
                    } else {
                        newValues.add(el);
                    }
                }
                json.add("values", newValues);
            }
            return Json.toString(json).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}
