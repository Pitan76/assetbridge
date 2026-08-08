package net.pitan76.assetbridge.util;

import com.google.gson.Gson;
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
}
