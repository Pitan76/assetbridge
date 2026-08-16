package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A resource location inside a pack, split into its meaningful parts.
 *
 * <p>Every place that used to build or pick apart strings like
 * {@code assets/examplemod/models/block/foo.json} goes through here, so path handling
 * lives in one class. Deliberately free of Minecraft types: the input and parsing layers
 * must stay testable without the game.
 */
public class AssetPath {
    public final PackKind kind;
    public final String namespace;
    public final String path;

    /**
     * Creates a new path. The namespace and path are normalised to lower case, as Minecraft does.
     *
     * @param kind      the pack root the resource lives under
     * @param namespace the namespace directory, e.g. {@code examplemod}
     * @param path      everything below the namespace, e.g. {@code models/block/foo.json}
     */
    public AssetPath(PackKind kind, String namespace, String path) {
        this.kind = kind;
        this.namespace = namespace.toLowerCase(Locale.ROOT);
        this.path = path.toLowerCase(Locale.ROOT);
    }

    public PackKind kind() {
        return kind;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    /** Pack roots. Mirrors Minecraft's {@code PackType} without depending on it. */
    public enum PackKind {
        CLIENT("assets"),
        SERVER("data");

        private final String directory;

        PackKind(String directory) {
            this.directory = directory;
        }

        public String directory() {
            return directory;
        }

        @Nullable
        public static PackKind byDirectory(String directory) {
            for (PackKind kind : values()) {
                if (kind.directory.equals(directory)) return kind;
            }
            return null;
        }
    }

    /** What the resource is, as far as Asset Bridge cares. */
    public enum Category {
        BLOCKSTATE,
        /**
         * A 1.21.4+ standalone item definition. Its contents mean nothing to older versions,
         * but the file name is an authoritative list of the mod's items.
         */
        ITEM_DEFINITION,
        /** A data pack recipe. Only ever found under {@link PackKind#SERVER}. */
        RECIPE,
        /** A data pack item tag. Only ever found under {@link PackKind#SERVER}. */
        TAG,
        /** A data pack loot table. Only ever found under {@link PackKind#SERVER}. */
        LOOT_TABLE,
        BLOCK_MODEL,
        ITEM_MODEL,
        MODEL,
        TEXTURE,
        TEXTURE_META,
        LANG,
        OTHER
    }

    /** Parses a full pack path, or returns {@code null} if it is not one. */
    @Nullable
    public static AssetPath parse(String fullPath) {
        String normalised = fullPath.replace('\\', '/');

        int rootEnd = normalised.indexOf('/');
        if (rootEnd < 0) return null;
        PackKind kind = PackKind.byDirectory(normalised.substring(0, rootEnd));
        if (kind == null) return null;

        int namespaceEnd = normalised.indexOf('/', rootEnd + 1);
        if (namespaceEnd < 0 || namespaceEnd == rootEnd + 1) return null;

        String namespace = normalised.substring(rootEnd + 1, namespaceEnd);
        String path = normalised.substring(namespaceEnd + 1);
        return path.isEmpty() ? null : new AssetPath(kind, namespace, path);
    }

    public AssetPath withPath(String newPath) {
        return new AssetPath(kind, namespace, newPath);
    }

    public String toFullPath() {
        return kind.directory() + "/" + namespace + "/" + path;
    }

    public Category category() {
        // The two roots share no directory layout, so a data path is never a client one.
        if (kind == PackKind.SERVER) {
            if ((path.startsWith("recipes/") || path.startsWith("recipe/")) && path.endsWith(".json")) return Category.RECIPE;
            if ((path.startsWith("tags/items/") || path.startsWith("tags/item/") || path.startsWith("tags/blocks/") || path.startsWith("tags/block/")) && path.endsWith(".json")) return Category.TAG;
            if ((path.startsWith("loot_tables/") || path.startsWith("loot_table/")) && path.endsWith(".json")) return Category.LOOT_TABLE;
            return Category.OTHER;
        }
        if (path.startsWith("blockstates/")) return Category.BLOCKSTATE;
        if (path.startsWith("items/")) return Category.ITEM_DEFINITION;
        if (path.startsWith("models/block/")) return Category.BLOCK_MODEL;
        if (path.startsWith("models/item/")) return Category.ITEM_MODEL;
        if (path.startsWith("models/")) return Category.MODEL;
        if (path.startsWith("textures/")) {
            return path.endsWith(".mcmeta") ? Category.TEXTURE_META : Category.TEXTURE;
        }
        if (path.startsWith("lang/")) return Category.LANG;
        return Category.OTHER;
    }

    /** True for the resources the MVP extracts from external archives. */
    public boolean isBridgeable() {
        return category() != Category.OTHER;
    }

    /**
     * The registry name a blockstate file describes, e.g. {@code foo} for
     * {@code blockstates/foo.json}. Only meaningful for {@link Category#BLOCKSTATE}.
     */
    @Nullable
    public String blockStateName() {
        if (category() != Category.BLOCKSTATE || !path.endsWith(".json")) return null;
        return path.substring("blockstates/".length(), path.length() - ".json".length());
    }

    /**
     * The registry name an item model file describes, e.g. {@code foo} for
     * {@code models/item/foo.json}. Only meaningful for {@link Category#ITEM_MODEL}.
     */
    @Nullable
    public String itemModelName() {
        return nameUnder(Category.ITEM_MODEL, "models/item/");
    }

    /**
     * The registry name an item definition describes, e.g. {@code foo} for
     * {@code items/foo.json}. Only meaningful for {@link Category#ITEM_DEFINITION}.
     */
    @Nullable
    public String itemDefinitionName() {
        return nameUnder(Category.ITEM_DEFINITION, "items/");
    }

    @Nullable
    private String nameUnder(Category expected, String prefix) {
        if (category() != expected || !path.endsWith(".json")) return null;
        String name = path.substring(prefix.length(), path.length() - ".json".length());
        // Nested paths are fragments shared by other files, not registry entries.
        return name.indexOf('/') < 0 ? name : null;
    }

    /**
     * This path with the pre-1.13 texture directories renamed to their flattened form,
     * or {@code this} when there is nothing to rename.
     *
     * <p>Serving a legacy texture where it was found is not enough on 1.19.3+: the block
     * atlas is assembled from {@code textures/block/} by definition, so a sprite left in
     * {@code textures/blocks/} is never stitched and every model using it renders as
     * missing. The model references are rewritten to match.
     */
    public AssetPath flattened() {
        if (kind != PackKind.CLIENT) return this;
        if (path.startsWith("textures/blocks/")) {
            return new AssetPath(kind, namespace, "textures/block/" + path.substring("textures/blocks/".length()));
        }
        if (path.startsWith("textures/items/")) {
            return new AssetPath(kind, namespace, "textures/item/" + path.substring("textures/items/".length()));
        }
        return this;
    }

    public static AssetPath blockState(String namespace, String name) {
        return new AssetPath(PackKind.CLIENT, namespace, "blockstates/" + name + ".json");
    }

    /** Where a block's drops are read from, e.g. {@code data/ns/loot_tables/blocks/foo.json}. */
    public static AssetPath lootTable(String namespace, String name) {
        return new AssetPath(PackKind.SERVER, namespace, "loot_tables/" + name + ".json");
    }

    public static AssetPath blockLootTable(String namespace, String name) {
        return lootTable(namespace, "blocks/" + name);
    }

    public static AssetPath model(String namespace, String path) {
        return new AssetPath(PackKind.CLIENT, namespace, "models/" + path + ".json");
    }

    public static AssetPath itemModel(String namespace, String name) {
        return model(namespace, "item/" + name);
    }

    public static AssetPath blockModel(String namespace, String name) {
        return model(namespace, "block/" + name);
    }

    public static AssetPath itemDefinition(String namespace, String name) {
        return new AssetPath(PackKind.CLIENT, namespace, "items/" + name + ".json");
    }

    public static AssetPath lang(String namespace, String locale) {
        return new AssetPath(PackKind.CLIENT, namespace, "lang/" + locale + ".json");
    }

    /**
     * The canonical path for a block model, e.g. {@code namespace:block/name} →
     * {@code assets/namespace/models/block/name.json}.
     *
     * <p>The {@code modelId} may be a bare {@code block/name} (implicit {@code minecraft}
     * namespace) or a fully-qualified {@code namespace:block/name}.
     */
    @Nullable
    public static AssetPath fromModelId(String modelId) {
        int colon = modelId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : modelId.substring(0, colon);
        String path = colon < 0 ? modelId : modelId.substring(colon + 1);
        if (path.isEmpty()) return null;
        return new AssetPath(PackKind.CLIENT, namespace, "models/" + path + ".json");
    }

    /**
     * The bare name a model reference ends in, e.g. {@code cube_all} for
     * {@code minecraft:block/cube_all}. That name is what says what a model is, which is why
     * it is worth having apart from the path the reference resolves to.
     */
    public static String modelName(String modelId) {
        int slash = modelId.lastIndexOf('/');
        return slash < 0 ? modelId : modelId.substring(slash + 1);
    }

    @Override
    public @NotNull String toString() {
        return toFullPath();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AssetPath)) return false;
        AssetPath other = (AssetPath) obj;
        return kind == other.kind && namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + namespace.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }
}
