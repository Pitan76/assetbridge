package net.pitan76.assetbridge.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Recovers the property set of a block from its blockstate file.
 *
 * <p>The key insight behind the design: once the properties are registered correctly,
 * the blockstate JSON itself can be handed to Minecraft unchanged. Interpreting which
 * model belongs to which state is the vanilla model loader's job, so all this parser has
 * to do is work out which properties exist and which values they take.
 */
public class BlockStatePropertyParser {
    /** Multipart condition keys that combine other conditions rather than naming a property. */
    private static final Set<String> COMBINATORS = new HashSet<>(Arrays.asList("OR", "AND"));

    private BlockStatePropertyParser() {
    }

    /**
     * @return the recovered properties, or {@code null} if any of them cannot be represented
     *         in Minecraft. In that case the blockstate must not be passed through: the model
     *         loader would fail on the property it does not know.
     */
    @Nullable
    public static BridgedStateDefinition parse(JsonObject blockState) {
        Map<String, Set<String>> collected = new LinkedHashMap<>();

        if (blockState.has("variants") && blockState.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : blockState.getAsJsonObject("variants").entrySet()) {
                if (!collectFromVariantKey(entry.getKey(), collected)) return null;
            }
        }
        if (blockState.has("multipart") && blockState.get("multipart").isJsonArray()) {
            for (JsonElement part : blockState.getAsJsonArray("multipart")) {
                if (!part.isJsonObject()) continue;
                JsonElement when = part.getAsJsonObject().get("when");
                if (when != null && when.isJsonObject() && !collectFromCondition(when.getAsJsonObject(), collected)) {
                    return null;
                }
            }
        }

        List<BridgedProperty> properties = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : collected.entrySet()) {
            BridgedProperty property = BridgedProperty.of(entry.getKey(), new ArrayList<>(entry.getValue()));
            if (property == null) return null;
            properties.add(property);
        }
        return new BridgedStateDefinition(properties);
    }

    /** Variant keys look like {@code facing=north,half=bottom}; the empty key means "any state". */
    private static boolean collectFromVariantKey(String key, Map<String, Set<String>> collected) {
        Map<String, String> conditions = VariantKey.parse(key);
        if (conditions == null) return false;

        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            add(collected, condition.getKey(), condition.getValue());
        }
        return true;
    }

    /**
     * Multipart conditions are {@code {"facing": "north|south"}}, optionally nested inside
     * an {@code OR} / {@code AND} array.
     */
    private static boolean collectFromCondition(JsonObject condition, Map<String, Set<String>> collected) {
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            if (COMBINATORS.contains(entry.getKey())) {
                if (!entry.getValue().isJsonArray()) return false;
                JsonArray nested = entry.getValue().getAsJsonArray();
                for (JsonElement element : nested) {
                    if (!element.isJsonObject()) return false;
                    if (!collectFromCondition(element.getAsJsonObject(), collected)) return false;
                }
                continue;
            }
            if (!entry.getValue().isJsonPrimitive()) return false;
            // '|' separates alternative values for the same property.
            for (String value : entry.getValue().getAsString().split("\\|")) {
                add(collected, entry.getKey(), value.trim());
            }
        }
        return true;
    }

    private static void add(Map<String, Set<String>> collected, String name, String value) {
        collected.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(value);
    }
}
