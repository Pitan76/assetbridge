package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

/**
 * A resource location inside a pack, split into its meaningful parts.
 *
 * <p>Every place that used to build or pick apart strings like
 * {@code assets/examplemod/models/block/foo.json} goes through here, so path handling
 * lives in one class. Deliberately free of Minecraft types: the input and parsing layers
 * must stay testable without the game.
 *
 * @param kind      the pack root the resource lives under
 * @param namespace the namespace directory, e.g. {@code examplemod}
 * @param path      everything below the namespace, e.g. {@code models/block/foo.json}
 */
public record AssetPath(PackKind kind, String namespace, String path) {
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

    public String toFullPath() {
        return kind.directory() + "/" + namespace + "/" + path;
    }

    public Category category() {
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
        return switch (category()) {
            case OTHER -> false;
            default -> true;
        };
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

    public static AssetPath blockState(String namespace, String name) {
        return new AssetPath(PackKind.CLIENT, namespace, "blockstates/" + name + ".json");
    }

    public static AssetPath itemModel(String namespace, String name) {
        return new AssetPath(PackKind.CLIENT, namespace, "models/item/" + name + ".json");
    }

    @Override
    public String toString() {
        return toFullPath();
    }
}
