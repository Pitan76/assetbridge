package net.pitan76.assetbridge.parse;

import org.jetbrains.annotations.Nullable;

/**
 * A pre-1.13 translation key, split into the parts that survive the move to the modern format.
 *
 * <p>Legacy keys look like {@code tile.example.name} for a block and {@code item.example.name}
 * for an item. Where a mod packed several sub-blocks or sub-items behind one registry entry
 * &mdash; the metadata values that 1.13 flattening removed &mdash; the key carries the metadata
 * value as well: {@code tile.example.0.name}, {@code tile.example.1.name}, and so on.
 *
 * <p>Metadata 0 is the entry itself, so it maps to the plain name. Everything above it has no
 * counterpart in a modern registry and gets a {@code _meta<n>} suffix, which is also the id the
 * pipeline registers the extra block or item under &mdash; the two have to agree or the name
 * never shows up in game.
 */
public class LegacyLangKey {
    /** {@code true} for a {@code tile.} key, {@code false} for an {@code item.} one. */
    public final boolean block;
    public final String base;
    /** The metadata value, or {@code -1} when the key named no metadata at all. */
    public final int meta;

    private LegacyLangKey(boolean block, String base, int meta) {
        this.block = block;
        this.base = base;
        this.meta = meta;
    }

    public boolean isBlock() {
        return block;
    }

    public String base() {
        return base;
    }

    public int meta() {
        return meta;
    }

    /**
     * @return the key, or {@code null} when it is not a legacy name key. Keys such as
     *         {@code tile.example.tooltip} are deliberately rejected: they are prose the mod
     *         attached to the block, not its name, and rewriting them would invent a key
     *         nothing looks up while destroying the one that was there.
     */
    @Nullable
    public static LegacyLangKey parse(String key) {
        boolean block;
        String rest;
        if (key.startsWith("tile.")) {
            block = true;
            rest = key.substring("tile.".length());
        } else if (key.startsWith("item.")) {
            block = false;
            rest = key.substring("item.".length());
        } else {
            return null;
        }

        if (!rest.endsWith(".name")) return null;
        rest = rest.substring(0, rest.length() - ".name".length());

        int meta = -1;
        int dot = rest.lastIndexOf('.');
        if (dot > 0) {
            int parsed = asMeta(rest.substring(dot + 1));
            if (parsed >= 0) {
                meta = parsed;
                rest = rest.substring(0, dot);
            }
        }
        return rest.isEmpty() ? null : new LegacyLangKey(block, rest, meta);
    }

    /** The registry name this key describes, e.g. {@code example} or {@code example_meta1}. */
    public String registryName() {
        return meta > 0 ? base + "_meta" + meta : base;
    }

    /** The modern translation key, e.g. {@code block.examplemod.example_meta1}. */
    public String modernKey(String namespace) {
        return prefix() + namespace + "." + registryName();
    }

    /**
     * The modern translation key with the metadata value ignored, so every sub-entry names the
     * one registry entry that exists. For when the metadata expansion is switched off: the
     * suffixed keys would then match nothing, and a block showing a raw key is worse than a
     * block wearing the last of its sub-entries' names.
     */
    public String baseKey(String namespace) {
        return prefix() + namespace + "." + base;
    }

    private String prefix() {
        return block ? "block." : "item.";
    }

    /** @return the parsed value, or {@code -1} when the text is not a plain decimal number */
    private static int asMeta(String text) {
        if (text.isEmpty() || text.length() > 5) return -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return -1;
        }
        return Integer.parseInt(text);
    }
}
