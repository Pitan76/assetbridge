package net.pitan76.assetbridge.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

/** Minimal Gson helpers so the parsing layer stays free of Minecraft classes. */
public class Json {
    private static final Gson GSON = new Gson();

    private Json() {
    }

    /** Returns {@code null} for anything that is not a JSON object, including malformed input. */
    @Nullable
    public static JsonObject parse(String text) {
        JsonElement element;
        try {
            element = GSON.fromJson(text, JsonElement.class);
        } catch (JsonParseException e) {
            return null;
        }
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static String toString(JsonElement element) {
        return GSON.toJson(element);
    }

    @SuppressWarnings("unchecked")
    public static <T extends JsonElement> T copy(T element) {
        if (element == null) return null;
        return (T) GSON.fromJson(GSON.toJson(element), element.getClass());
    }

    /** @return the value of {@code key} if it is a primitive, else {@code null}. */
    @Nullable
    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** @return the value of {@code key} if it is an object, else {@code null}. */
    @Nullable
    public static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    /** @return the value of {@code key} if it is an array, else {@code null}. */
    @Nullable
    public static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
}
