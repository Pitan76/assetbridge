package net.pitan76.assetbridge.parse;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The key of one entry in a blockstate's {@code variants}, e.g. {@code facing=north,half=top}.
 *
 * <p>A key constrains only the properties it names, so {@code facing=north} stands for every
 * state facing north whatever the rest holds, and the empty key stands for all of them. Every
 * place that reads a blockstate needs that same reading — which properties a file uses, which
 * states it covers, which variant a state gets its shape from — so it is written once here.
 */
public class VariantKey {
    private VariantKey() {
    }

    /**
     * @return the properties the key requires, empty for the key that matches everything, or
     *         {@code null} when the key is not a list of assignments at all. Callers must treat
     *         {@code null} as "cannot be interpreted" rather than as "no conditions": a key
     *         Asset Bridge cannot read is one it must not claim to have understood.
     */
    @Nullable
    public static Map<String, String> parse(String key) {
        if (key.isEmpty()) return Collections.emptyMap();

        Map<String, String> conditions = new LinkedHashMap<>();
        for (String pair : key.split(",")) {
            if (pair.trim().isEmpty()) continue;

            int equals = pair.indexOf('=');
            // A key without '=' is not a property assignment. Pre-1.13 packs used "normal"
            // here, which the converter rewrites; anything else we cannot interpret.
            if (equals < 0) return null;
            conditions.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return conditions;
    }

    /** Whether a state, as property name to value name, satisfies everything {@code key} requires. */
    public static boolean matches(String key, Map<String, String> state) {
        Map<String, String> conditions = parse(key);
        return conditions != null && matches(conditions, state);
    }

    /**
     * A property the state does not have at all never matches, which drops a variant left over
     * from a file that could not be registered in full rather than letting it claim every state.
     */
    public static boolean matches(Map<String, String> conditions, Map<String, String> state) {
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            if (!condition.getValue().equals(state.get(condition.getKey()))) return false;
        }
        return true;
    }
}
