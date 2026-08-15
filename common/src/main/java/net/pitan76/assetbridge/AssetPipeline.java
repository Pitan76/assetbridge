package net.pitan76.assetbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.*;
import net.pitan76.assetbridge.convert.AssetConverter;
import net.pitan76.assetbridge.convert.AtlasSources;
import net.pitan76.assetbridge.convert.BlockStateConverter;
import net.pitan76.assetbridge.convert.ForgeBlockStateExpander;
import net.pitan76.assetbridge.convert.ModelConverter;
import net.pitan76.assetbridge.convert.ModelReferenceResolver;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.feature.builtin.MetaVariantsFeature;
import net.pitan76.assetbridge.parse.BlockStateCoverage;
import net.pitan76.assetbridge.parse.BlockStateParser;
import net.pitan76.assetbridge.parse.BlockStatePropertyParser;
import net.pitan76.assetbridge.parse.LegacyLangKey;
import net.pitan76.assetbridge.parse.MetaVariants;
import net.pitan76.assetbridge.util.Json;
import net.pitan76.assetbridge.util.IdUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
        Map<String, JsonObject> blockStateJson = new LinkedHashMap<>();
        MetaVariants metaVariants = new MetaVariants();

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
                read(assets, archive, path, entry.getValue(), seenBlocks, itemCandidates, blockStateJson, metaVariants);
            }
        }

        boolean expandMeta = Features.isEnabled(MetaVariantsFeature.ID);

        // Ids a metadata sub-entry took over, so the same model is not also registered under
        // its own name. Filled by both expansions, spent when the item candidates resolve.
        Set<String> claimed = new HashSet<>();

        // Before the items are resolved, because a sub-block that cannot be given a state of
        // its own falls back to being one more item candidate.
        if (expandMeta) {
            expandBlockMetaVariants(assets, metaVariants, blockStateJson, itemCandidates, claimed);
        }

        Set<String> blockIds = new HashSet<>();
        for (BridgedBlockDefinition block : assets.blocks()) {
            blockIds.add(block.id());
        }
        claimed.addAll(blockIds);

        // Planned before the candidates resolve so the models it takes over can be excluded,
        // but added after, so an item the archive named itself keeps the order it was found in.
        List<BridgedItemDefinition> metaItems = expandMeta
                ? planItemMetaVariants(assets, metaVariants, claimed)
                : Collections.<BridgedItemDefinition>emptyList();
        itemCandidates.resolve(claimed).forEach(assets::addItem);
        metaItems.forEach(assets::addItem);

        // After both expansions, so the sub-entries get their item model too.
        for (BridgedBlockDefinition block : assets.blocks()) {
            generateItemModel(assets, block);
        }

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
                             Set<String> seenBlocks, ItemCandidates itemCandidates,
                             Map<String, JsonObject> blockStateJson, MetaVariants metaVariants) {
        AssetVersion version = archive.version();

        switch (path.category()) {
            case BLOCKSTATE:
                readBlockState(assets, archive, version, path, source, seenBlocks, blockStateJson);
                break;
            case ITEM_DEFINITION:
                // The file itself means nothing to this Minecraft version, so it is not
                // served; only its name is used, as the mod's own list of items.
                itemCandidates.addDefinition(path.namespace(), path.itemDefinitionName(),
                        archive.fileName(), version);
                break;
            case ITEM_MODEL:
                if (convertInto(assets, MODELS, path, source, version, archive)) {
                    itemCandidates.addModel(path.namespace(), path.itemModelName(), archive.fileName(), version);
                }
                break;
            case BLOCK_MODEL:
            case MODEL:
                convertInto(assets, MODELS, path, source, version, archive);
                break;
            case TEXTURE:
            case TEXTURE_META:
                // Nothing to convert, so the bytes never have to enter the heap: the archive
                // serves them when the game asks. An earlier archive claiming the path still wins.
                // Pre-1.13 texture directories are flattened here so the sprite ends up where
                // the atlas looks for it; ModelConverter rewrites the references to match.
                {
                    AssetPath target = path.flattened();
                    if (!assets.hasResource(target)) assets.putResource(target, source);
                }
                break;
            case LANG:
                {
                    AssetPath target = path.flattened();
                    if (target.path().endsWith(".lang")) {
                        String newPath = target.path().substring(0, target.path().length() - ".lang".length()) + ".json";
                        AssetPath jsonTarget = target.withPath(newPath);
                        byte[] raw = readBytes(source, path, archive);
                        if (raw != null) {
                            // Read even when another archive already claimed this locale: the
                            // metadata sub-entries are collected from every language file there
                            // is, so a locale we do not serve still tells us what exists.
                            metaVariants.scan(jsonTarget.namespace(), new String(raw, StandardCharsets.UTF_8),
                                    archive.fileName(), version);
                            if (!assets.hasResource(jsonTarget)) {
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
                break;
            case TAG:
                {
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
                break;
            case LOOT_TABLE:
                {
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
                        if (raw != null) assets.putResource(target, raw);
                    }
                }
                break;
            case RECIPE:
                // Server-side data is a feature's business, not the core's;
                // RecipeFeature reads these straight from the archive.
            case OTHER:
                // Filtered out at scan time; nothing to do.
            default:
                break;
        }
    }

    private static void readBlockState(BridgedAssetManager assets, AssetArchive archive, AssetVersion version,
                                       AssetPath path, AssetSource source, Set<String> seenBlocks,
                                       Map<String, JsonObject> blockStateJson) {
        String name = path.blockStateName();
        if (name == null) return;
        String id = path.namespace() + ":" + name;

        byte[] data = readBytes(source, path, archive);
        if (data == null) return;

        // Forge's own blockstate format hides the models one level deeper than vanilla looks,
        // so without this the whole file reads as "no model" and the block is dropped.
        ForgeBlockStateExpander.Result forge = null;
        JsonObject raw = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (ForgeBlockStateExpander.isForgeFormat(raw)) {
            forge = ForgeBlockStateExpander.expand(raw, path.namespace(), name);
            data = Json.toString(forge.blockState()).getBytes(StandardCharsets.UTF_8);
        }

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

        // Only now that the block is definitely ours: a duplicate must not leave its
        // generated models behind for the archive that actually won the id.
        if (forge != null) {
            storeForgeExtras(assets, forge, path.namespace(), name, version);
            AssetBridge.LOGGER.info("Expanded Forge blockstate {} in {} into {} variant(s) and {} generated model(s)",
                    id, archive.fileName(), forge.variantCount(), forge.generatedModels().size());
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

        // Kept so the metadata expansion can pick a single sub-variant out of it later, once
        // the language files that say how many sub-blocks there are have all been read.
        blockStateJson.put(id, json);

        assets.addBlock(new BridgedBlockDefinition(path.namespace(), name, qualify(model, path.namespace()),
                states, archive.fileName(), version));
    }

    /**
     * Registers the assets a Forge blockstate needed synthesising to be expressible in vanilla:
     * the derived models standing in for its per-variant retexturing, and the item model its
     * {@code inventory} variant described.
     */
    private static void storeForgeExtras(BridgedAssetManager assets, ForgeBlockStateExpander.Result forge,
                                         String namespace, String blockName, AssetVersion version) {
        for (Map.Entry<String, JsonObject> generated : forge.generatedModels().entrySet()) {
            putModel(assets, AssetPath.blockModel(namespace, generated.getKey()), generated.getValue(), version);
        }
        if (forge.inventoryModel() != null) {
            putModel(assets, AssetPath.itemModel(namespace, blockName), forge.inventoryModel(), version);
        }
    }

    /** Writes a model Asset Bridge built itself, through the same conversion an archive's own gets. */
    private static void putModel(BridgedAssetManager assets, AssetPath path, JsonObject model, AssetVersion version) {
        if (assets.hasResource(path)) return;

        byte[] data = Json.toString(model).getBytes(StandardCharsets.UTF_8);
        byte[] converted = MODELS.convert(path, data, version);
        assets.putResource(path, converted == null ? data : converted);
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

    /**
     * Registers the extra blocks a pre-1.13 archive packed behind one block id as metadata.
     *
     * <p>Metadata 0 is the block that was already registered from the blockstate file. For each
     * further value the language file described, a sub-block is registered under
     * {@code <name>_meta<n>} &mdash; the same name {@link LegacyLangKey} rewrites the translation
     * to, so it comes out named rather than as a raw key.
     *
     * <p>Which state a metadata value stood for is decided by the mod's Java code and is not in
     * the assets, so it is only recoverable when the blockstate leaves exactly one reading: a
     * single property whose values line up one-to-one with the metadata values found. Then
     * metadata <i>n</i> is that property's <i>n</i>-th value, and the sub-block is registered
     * property-free with that variant as its only state. When the shapes do not line up there is
     * nothing to place, so the sub-entry becomes an item instead &mdash; the id still exists, is
     * named, and shows the block's model.
     */
    private static void expandBlockMetaVariants(BridgedAssetManager assets, MetaVariants metaVariants,
                                                Map<String, JsonObject> blockStateJson, ItemCandidates itemCandidates,
                                                Set<String> claimed) {
        int blocks = 0;
        int items = 0;
        // Snapshotted: the sub-blocks are appended to the same list we are walking.
        for (BridgedBlockDefinition block : new ArrayList<>(assets.blocks())) {
            List<Integer> metas = metaVariants.blockMetas(block.id());
            if (metas.isEmpty()) continue;

            List<JsonElement> variants = singlePropertyVariants(blockStateJson.get(block.id()), metas.size());
            for (int index = 0; index < metas.size(); index++) {
                int meta = metas.get(index);
                if (meta <= 0) continue;

                String name = MetaVariants.nameFor(block.path(), meta);
                AssetPath statePath = AssetPath.blockState(block.namespace(), name);
                // An archive that spells the sub-entry out itself always wins.
                if (assets.hasResource(statePath)) continue;

                JsonElement variant = variants == null ? null : variants.get(index);
                String model = BlockStateParser.modelOf(variant);
                if (model == null) {
                    // No state to place, so it survives as an item. Its own item model if the
                    // archive has one, the block's otherwise.
                    String dedicated = dedicatedItemModel(assets, block.namespace(), block.path(), meta);
                    if (dedicated != null && dedicated.equals(name)) continue;

                    String parent = dedicated != null
                            ? block.namespace() + ":item/" + dedicated
                            : block.namespace() + ":item/" + block.path();
                    if (dedicated != null) claimed.add(block.namespace() + ":" + dedicated);

                    itemCandidates.addModel(block.namespace(), name, block.sourceArchive(), block.version());
                    putAliasItemModel(assets, block.namespace(), name, parent, block.version());
                    items++;
                    continue;
                }

                assets.putResource(statePath, Json.toString(BlockStateConverter.singleVariant(variant))
                        .getBytes(StandardCharsets.UTF_8));
                assets.addBlock(new BridgedBlockDefinition(block.namespace(), name, model,
                        BridgedStateDefinition.empty(), block.sourceArchive(), block.version()));
                blocks++;
            }
        }
        if (blocks > 0 || items > 0) {
            AssetBridge.LOGGER.info("Expanded pre-1.13 metadata into {} sub-block(s) and {} sub-item(s)", blocks, items);
        }
    }

    /**
     * Plans the extra items a pre-1.13 archive packed behind one item name as metadata values.
     *
     * <p>Items have no blockstate, so there is no equivalent of the property mapping the blocks
     * get: which model a metadata value used was bound in the mod's Java code, to a name of the
     * author's choosing. Two of those choices are recoverable, because they are spelled out in
     * the file names and can be confirmed rather than guessed:
     * {@code models/item/<base>_meta<n>.json} and {@code models/item/<base>_<n>.json}. Where one
     * of those exists the sub-entry gets its real model, and that model is claimed so it is not
     * <em>also</em> registered as a standalone item under its own name.
     *
     * <p>Any other naming &mdash; {@code models/item/dust_copper.json} for metadata 0 of
     * {@code dust}, say &mdash; cannot be tied back to a metadata value from the assets. Those
     * models are already registered as items in their own right with the right texture, so
     * nothing is lost but the display name, and inventing a mapping to recover it would risk
     * putting the wrong name on the wrong item.
     *
     * @param claimed ids that must not resolve as standalone items; added to here
     * @return the items to register, which the caller adds after the candidates resolve
     */
    private static List<BridgedItemDefinition> planItemMetaVariants(BridgedAssetManager assets,
                                                                    MetaVariants metaVariants, Set<String> claimed) {
        List<BridgedItemDefinition> planned = new ArrayList<>();
        for (MetaVariants.Entry entry : metaVariants.itemEntries()) {
            String namespace = entry.namespace();
            String base = entry.base();
            boolean hasBaseModel = assets.hasResource(AssetPath.itemModel(namespace, base));

            for (int meta : entry.metas()) {
                String name = MetaVariants.nameFor(base, meta);
                String dedicated = dedicatedItemModel(assets, namespace, base, meta);
                // The model is already named exactly what the sub-entry would be; it is the
                // sub-entry, and gets registered from its own model like any other item.
                if (dedicated != null && dedicated.equals(name)) continue;

                String parent;
                if (dedicated != null) {
                    parent = namespace + ":item/" + dedicated;
                    claimed.add(namespace + ":" + dedicated);
                } else if (hasBaseModel) {
                    parent = namespace + ":item/" + base;
                } else {
                    // Nothing in the archive to show for this value.
                    continue;
                }
                // Metadata 0 is the base name, which its own item model already registers.
                if (meta == 0 && hasBaseModel) continue;
                if (!claimed.add(namespace + ":" + name)) continue;

                putAliasItemModel(assets, namespace, name, parent, entry.version());
                planned.add(new BridgedItemDefinition(namespace, name, entry.sourceArchive(), entry.version()));
            }
        }
        if (!planned.isEmpty()) {
            AssetBridge.LOGGER.info("Expanded pre-1.13 metadata into {} sub-item(s)", planned.size());
        }
        return planned;
    }

    /**
     * An item model named after a metadata value of {@code base}, if the archive shipped one.
     *
     * @return the name under {@code models/item/}, or {@code null} when there is none
     */
    @Nullable
    private static String dedicatedItemModel(BridgedAssetManager assets, String namespace, String base, int meta) {
        String[] candidates = {base + "_meta" + meta, base + "_" + meta};
        for (String candidate : candidates) {
            if (assets.hasResource(AssetPath.itemModel(namespace, candidate))) return candidate;
        }
        return null;
    }

    /**
     * Reads a blockstate as a list of variants indexed by metadata value.
     *
     * @return one variant per metadata value in ascending order, or {@code null} when the file
     *         cannot be read that way: anything but a flat set of single-property keys, or a
     *         count that does not match, means the mapping would be a guess
     */
    @Nullable
    private static List<JsonElement> singlePropertyVariants(@Nullable JsonObject blockState, int expected) {
        if (blockState == null) return null;
        JsonObject variants = Json.object(blockState, "variants");
        if (variants == null || variants.size() != expected) return null;

        String property = null;
        List<JsonElement> ordered = new ArrayList<>(expected);
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            String key = entry.getKey();
            int equals = key.indexOf('=');
            if (equals <= 0 || key.indexOf(',') >= 0) return null;

            String name = key.substring(0, equals);
            if (property == null) {
                property = name;
            } else if (!property.equals(name)) {
                return null;
            }
            if (BlockStateParser.modelOf(entry.getValue()) == null) return null;
            ordered.add(entry.getValue());
        }
        return ordered;
    }

    /** An item model for a sub-entry that has none of its own: it looks like the entry it came from. */
    private static void putAliasItemModel(BridgedAssetManager assets, String namespace, String name,
                                          String parent, AssetVersion version) {
        AssetPath path = AssetPath.itemModel(namespace, name);
        if (assets.hasResource(path)) return;

        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        putModel(assets, path, model, version);
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
        if (Features.isDisabled(CutoutBlocksFeature.ID)) return 0;

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

    /**
     * Rewrites a pre-1.13 translation key to its modern form.
     *
     * <pre>
     * tile.example.name    -&gt; block.&lt;modid&gt;.example
     * tile.example.0.name  -&gt; block.&lt;modid&gt;.example        (metadata 0 is the entry itself)
     * tile.example.1.name  -&gt; block.&lt;modid&gt;.example_meta1  (matches the id the sub-block gets)
     * item.example.name    -&gt; item.&lt;modid&gt;.example
     * </pre>
     *
     * <p>Anything that is not a legacy name key is left exactly as it was: a modern key is
     * already right, and a legacy non-name key such as {@code tile.example.tooltip} has no
     * modern counterpart to rewrite it to.
     *
     * <p>With the metadata expansion switched off there is no {@code example_meta1} to name, so
     * every sub-entry names {@code example} instead. The last one in the file wins, which beats
     * pointing at a registry entry that does not exist.
     */
    private static String convertLangKey(String key, String modid) {
        LegacyLangKey parsed = LegacyLangKey.parse(key);
        if (parsed == null) return key;
        return Features.isEnabled(MetaVariantsFeature.ID) ? parsed.modernKey(modid) : parsed.baseKey(modid);
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
