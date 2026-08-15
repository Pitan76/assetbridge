package net.pitan76.assetbridge.parse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.ArrayList;

/**
 * The metadata sub-entries a pre-1.13 archive hides behind a single registry name.
 *
 * <p>Before flattening, one block or item id could stand for up to sixteen distinct things,
 * told apart by a metadata value. Which value means what lives in the mod's Java code, which
 * Asset Bridge never runs, so the only place the sub-entries are visible from the assets alone
 * is the language file: a mod that ships {@code tile.example.0.name} through
 * {@code tile.example.3.name} is telling us that {@code example} is four blocks.
 *
 * <p>That is what this collects. Every locale in the archive is folded together, because a
 * translation may be incomplete and the union is the closest thing to the real set.
 */
public class MetaVariants {
    private final Map<String, SortedSet<Integer>> blocks = new LinkedHashMap<>();
    private final Map<String, SortedSet<Integer>> items = new LinkedHashMap<>();

    /** Reads every key out of a {@code .lang} file body. */
    public void scan(String namespace, String langContent) {
        for (String line : langContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
            int equals = trimmed.indexOf('=');
            if (equals > 0) record(namespace, trimmed.substring(0, equals).trim());
        }
    }

    public void record(String namespace, String key) {
        LegacyLangKey parsed = LegacyLangKey.parse(key);
        if (parsed == null || parsed.meta() < 0) return;

        Map<String, SortedSet<Integer>> into = parsed.isBlock() ? blocks : items;
        into.computeIfAbsent(namespace + ":" + parsed.base(), id -> new TreeSet<>()).add(parsed.meta());
    }

    /**
     * @return the metadata values of {@code id} in ascending order, or an empty list when the
     *         archive described only one thing under that name. A single value is not a
     *         sub-entry set &mdash; plenty of mods write {@code tile.example.0.name} for a block
     *         that has no metadata at all &mdash; so it is reported as nothing to expand.
     */
    public List<Integer> blockMetas(String id) {
        return metas(blocks, id);
    }

    public List<Integer> itemMetas(String id) {
        return metas(items, id);
    }

    private static List<Integer> metas(Map<String, SortedSet<Integer>> from, String id) {
        SortedSet<Integer> found = from.get(id);
        if (found == null || found.size() < 2) return Collections.emptyList();
        return new ArrayList<>(found);
    }

    /** The registry name metadata {@code meta} of {@code base} is bridged under. */
    public static String nameFor(String base, int meta) {
        return meta > 0 ? base + "_meta" + meta : base;
    }
}
