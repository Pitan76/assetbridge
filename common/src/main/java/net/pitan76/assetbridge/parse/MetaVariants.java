package net.pitan76.assetbridge.parse;

import net.pitan76.assetbridge.asset.AssetVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The metadata sub-entries a pre-1.13 archive hides behind a single registry name.
 *
 * <p>Before flattening, a block or item was addressed by a numeric id, and there were only so
 * many to go round &mdash; 4096 for blocks. Packing several distinct things behind one id and
 * telling them apart by a metadata value was how a mod added a lot of content without spending
 * a lot of ids. So a metadata value is not a state of one thing; it is a thing.
 *
 * <p>Which value is which lives in the mod's Java code, which Asset Bridge never runs. The one
 * place the sub-entries are visible from the assets alone is the language file: a mod shipping
 * {@code tile.example.0.name} through {@code tile.example.3.name} is telling us that
 * {@code example} is four blocks.
 *
 * <p>That is what this collects. Every locale in the archive is folded together, because a
 * translation may be incomplete and the union is the closest thing to the real set.
 */
public class MetaVariants {
    private final Map<String, Entry> blocks = new LinkedHashMap<>();
    private final Map<String, Entry> items = new LinkedHashMap<>();

    /** Reads every key out of a {@code .lang} file body. */
    public void scan(String namespace, String langContent, String sourceArchive, AssetVersion version) {
        for (String line : langContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
            int equals = trimmed.indexOf('=');
            if (equals > 0) record(namespace, trimmed.substring(0, equals).trim(), sourceArchive, version);
        }
    }

    public void record(String namespace, String key, String sourceArchive, AssetVersion version) {
        LegacyLangKey parsed = LegacyLangKey.parse(key);
        if (parsed == null || parsed.meta() < 0) return;

        Map<String, Entry> into = parsed.isBlock() ? blocks : items;
        Entry entry = into.get(namespace + ":" + parsed.base());
        if (entry == null) {
            entry = new Entry(namespace, parsed.base(), sourceArchive, version);
            into.put(entry.id(), entry);
        }
        entry.metas.add(parsed.meta());
    }

    /**
     * @return the metadata values of {@code id} in ascending order, or an empty list when the
     *         archive described only one thing under that name. A single value is not a
     *         sub-entry set &mdash; plenty of mods write {@code tile.example.0.name} for a block
     *         that has no metadata at all &mdash; so it is reported as nothing to expand.
     */
    public List<Integer> blockMetas(String id) {
        Entry entry = blocks.get(id);
        return entry == null || !entry.isSubEntrySet() ? Collections.<Integer>emptyList() : entry.metas();
    }

    /** Every item name that stands for more than one thing, in the order they were found. */
    public Collection<Entry> itemEntries() {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : items.values()) {
            if (entry.isSubEntrySet()) found.add(entry);
        }
        return found;
    }

    /** The registry name metadata {@code meta} of {@code base} is bridged under. */
    public static String nameFor(String base, int meta) {
        return meta > 0 ? base + "_meta" + meta : base;
    }

    /** One registry name and the metadata values found under it. */
    public static class Entry {
        private final String namespace;
        private final String base;
        private final String sourceArchive;
        private final AssetVersion version;
        private final SortedSet<Integer> metas = new TreeSet<>();

        Entry(String namespace, String base, String sourceArchive, AssetVersion version) {
            this.namespace = namespace;
            this.base = base;
            this.sourceArchive = sourceArchive;
            this.version = version;
        }

        public String namespace() {
            return namespace;
        }

        public String base() {
            return base;
        }

        /** The archive whose language file described the sub-entries. */
        public String sourceArchive() {
            return sourceArchive;
        }

        public AssetVersion version() {
            return version;
        }

        public List<Integer> metas() {
            return new ArrayList<>(metas);
        }

        public String id() {
            return namespace + ":" + base;
        }

        boolean isSubEntrySet() {
            return metas.size() >= 2;
        }
    }
}
