package net.pitan76.assetbridge.archive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the human-readable mod names out of an archive's loader metadata.
 *
 * <p>The loader itself cannot answer this: the archives Asset Bridge reads are precisely the
 * mods that are <em>not</em> installed, so they appear in no mod list. The name has to come
 * from the archive, and the same metadata files {@link VersionDetector} already reads carry it.
 *
 * <p>Only metadata is parsed; no mod code is loaded or executed.
 */
public class ModMetadata {
    /** {@code modId = "..."} in a mods.toml {@code [[mods]]} block. */
    private static final Pattern TOML_MOD_ID = Pattern.compile("modId\\s*=\\s*\"([^\"]*)\"");
    /** {@code displayName = "..."} in the same block. */
    private static final Pattern TOML_DISPLAY_NAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]*)\"");

    private ModMetadata() {
    }

    /**
     * @param metadata the contents of {@link VersionDetector#METADATA_FILES} the archive had
     * @return namespace to display name, for every mod the archive declares a name for
     */
    public static Map<String, String> displayNames(Map<String, String> metadata) {
        Map<String, String> names = new LinkedHashMap<>();
        // Fabric first: an archive carrying both is a multi-loader jar, and either is as good.
        putFabricName(names, metadata.get("fabric.mod.json"));
        for (String file : Arrays.asList("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            putTomlNames(names, metadata.get(file));
        }
        return names;
    }

    private static void putFabricName(Map<String, String> names, @Nullable String text) {
        if (text == null) return;

        JsonObject root = Json.parse(text);
        if (root == null) return;
        put(names, string(root, "id"), string(root, "name"));
    }

    /**
     * A mods.toml may declare several mods. Splitting on the table header keeps each
     * {@code modId} with the {@code displayName} of its own block rather than the next one's.
     */
    private static void putTomlNames(Map<String, String> names, @Nullable String text) {
        if (text == null) return;

        for (String block : text.split("\\[\\[\\s*mods\\s*]]")) {
            Matcher id = TOML_MOD_ID.matcher(block);
            Matcher name = TOML_DISPLAY_NAME.matcher(block);
            if (id.find() && name.find()) put(names, id.group(1), name.group(1));
        }
    }

    @Nullable
    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static void put(Map<String, String> names, @Nullable String namespace, @Nullable String displayName) {
        if (namespace == null || namespace.isBlank()) return;
        if (displayName == null || displayName.isBlank()) return;
        names.putIfAbsent(namespace.toLowerCase(java.util.Locale.ROOT), displayName.trim());
    }
}
