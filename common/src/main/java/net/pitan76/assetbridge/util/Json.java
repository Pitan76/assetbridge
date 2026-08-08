package net.pitan76.assetbridge.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/** Minimal Gson helpers so the parsing layer stays free of Minecraft classes. */
public final class Json {
    private static final Gson GSON = new Gson();

    private Json() {
    }

    @Nullable
    public static JsonObject parse(String text) {
        JsonElement element = GSON.fromJson(text, JsonElement.class);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static String toString(JsonElement element) {
        return GSON.toJson(element);
    }
}
