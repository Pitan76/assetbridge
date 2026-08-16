package net.pitan76.assetbridge.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Fills in the states an external blockstate file forgot to describe.
 *
 * <p>The registered properties span the full cartesian product of their values, but the file
 * only lists the combinations the original mod could actually reach. A state nobody described
 * still exists on our block — the player can get to it with a placement or a command — and
 * vanilla renders it as the missing model. This pass finds those states and points them at a
 * model that does exist.
 *
 * <p>Purely a JSON-to-JSON operation, so it stays in the parse layer and needs no Minecraft.
 */
public class BlockStateCoverage {
    /**
     * Above this many combinations the file is unusual enough that listing every state would
     * bloat the blockstate more than a missing model costs. Vanilla's widest blocks (redstone
     * wire, 1296 states) stay well inside it.
     */
    private static final int MAX_COMBINATIONS = 4096;

    /** Multipart condition keys that combine other conditions rather than naming a property. */
    private static final Set<String> COMBINATORS = new HashSet<>(Arrays.asList("OR", "AND"));

    private BlockStateCoverage() {
    }

    /**
     * @param fallback the variant value ({@code {"model": ...}} or a weighted array) to apply to
     *                 states the file does not describe
     * @return the completed blockstate, or {@code null} when every state was already covered
     *         (or when there are too many to check)
     */
    @Nullable
    public static JsonObject complete(JsonObject blockState, BridgedStateDefinition states, JsonElement fallback) {
        if (states.isEmpty()) return null;

        List<Map<String, String>> combinations = combinations(states);
        if (combinations == null) return null;

        JsonArray parts = Json.array(blockState, "multipart");
        JsonObject variants = parts != null ? null : Json.object(blockState, "variants");
        if (parts == null && variants == null) return null;

        List<Map<String, String>> missing = new ArrayList<>();
        for (Map<String, String> state : combinations) {
            boolean covered = parts != null ? matchesAnyPart(parts, state) : matchesAnyVariant(variants, state);
            if (!covered) missing.add(state);
        }
        if (missing.isEmpty()) return null;

        JsonObject completed = Json.copy(blockState);
        if (parts != null) {
            JsonArray completedParts = completed.getAsJsonArray("multipart");
            for (Map<String, String> state : missing) {
                JsonObject part = new JsonObject();
                part.add("when", condition(state));
                part.add("apply", Json.copy(fallback));
                completedParts.add(part);
            }
        } else {
            JsonObject completedVariants = completed.getAsJsonObject("variants");
            for (Map<String, String> state : missing) {
                completedVariants.add(variantKey(state), Json.copy(fallback));
            }
        }
        return completed;
    }

    /** @return how many states {@link #complete} would have to add, for logging. */
    public static int missingCount(JsonObject blockState, JsonObject completed) {
        return size(completed) - size(blockState);
    }

    private static int size(JsonObject blockState) {
        JsonArray parts = Json.array(blockState, "multipart");
        if (parts != null) return parts.size();
        JsonObject variants = Json.object(blockState, "variants");
        return variants != null ? variants.size() : 0;
    }

    /** @return every combination of property values, or {@code null} if there are too many. */
    @Nullable
    private static List<Map<String, String>> combinations(BridgedStateDefinition states) {
        long total = 1;
        for (BridgedProperty property : states.properties()) {
            total *= property.values().size();
            if (total > MAX_COMBINATIONS) return null;
        }

        List<Map<String, String>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());
        for (BridgedProperty property : states.properties()) {
            List<Map<String, String>> expanded = new ArrayList<>();
            for (Map<String, String> partial : result) {
                for (String value : property.values()) {
                    Map<String, String> next = new LinkedHashMap<>(partial);
                    next.put(property.name(), value);
                    expanded.add(next);
                }
            }
            result = expanded;
        }
        return result;
    }

    /**
     * A variant key constrains only the properties it names, so {@code facing=north} covers
     * every state facing north whatever the rest holds. The empty key covers everything.
     */
    private static boolean matchesAnyVariant(JsonObject variants, Map<String, String> state) {
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            if (matchesVariantKey(entry.getKey(), state)) return true;
        }
        return false;
    }

    private static boolean matchesVariantKey(String key, Map<String, String> state) {
        return VariantKey.matches(key, state);
    }

    /**
     * A multipart state is covered as soon as one part applies; an unconditional part covers
     * everything, which is why most multipart files need no completion at all.
     */
    private static boolean matchesAnyPart(JsonArray parts, Map<String, String> state) {
        for (JsonElement part : parts) {
            if (!part.isJsonObject()) continue;
            JsonElement when = part.getAsJsonObject().get("when");
            if (when == null) return true;
            if (when.isJsonObject() && matchesCondition(when.getAsJsonObject(), state)) return true;
        }
        return false;
    }

    private static boolean matchesCondition(JsonObject condition, Map<String, String> state) {
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            if (COMBINATORS.contains(entry.getKey())) {
                if (!entry.getValue().isJsonArray()) return false;
                boolean or = entry.getKey().equals("OR");
                boolean matched = !or;
                for (JsonElement nested : entry.getValue().getAsJsonArray()) {
                    boolean value = nested.isJsonObject() && matchesCondition(nested.getAsJsonObject(), state);
                    matched = or ? matched || value : matched && value;
                }
                if (!matched) return false;
                continue;
            }
            if (!entry.getValue().isJsonPrimitive()) return false;
            // '|' separates alternative values for the same property.
            boolean matched = false;
            for (String value : entry.getValue().getAsString().split("\\|")) {
                if (value.trim().equals(state.get(entry.getKey()))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static String variantKey(Map<String, String> state) {
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            if (key.length() > 0) key.append(',');
            key.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return key.toString();
    }

    private static JsonObject condition(Map<String, String> state) {
        JsonObject condition = new JsonObject();
        state.forEach(condition::addProperty);
        return condition;
    }
}
