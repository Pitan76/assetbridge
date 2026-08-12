package net.pitan76.assetbridge.archive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which Minecraft generation an archive's assets were authored for.
 *
 * <p>{@code pack.mcmeta} is the obvious source but most mod JARs do not ship one, so the
 * loader metadata is consulted next: it names the Minecraft version the mod depends on.
 * Only metadata is read; no mod code is loaded or executed.
 */
public class VersionDetector {
    /** Metadata files worth reading, in the order they are trusted. */
    public static final List<String> METADATA_FILES = List.of(
            "pack.mcmeta",
            "META-INF/neoforge.mods.toml",
            "META-INF/mods.toml",
            "fabric.mod.json"
    );

    /** A release version such as {@code 1.21} or {@code 1.21.1}. */
    private static final Pattern VERSION = Pattern.compile("1\\.(\\d{1,2})(?:\\.(\\d{1,2}))?");

    /** {@code modId="minecraft"} followed by its {@code versionRange} in a mods.toml block. */
    private static final Pattern TOML_MINECRAFT_RANGE = Pattern.compile(
            "modId\\s*=\\s*\"minecraft\"[\\s\\S]{0,400}?versionRange\\s*=\\s*\"([^\"]*)\"");

    public static class Detection {
        public final AssetVersion version;
        public final String source;

        /**
         * @param version the generation to convert the archive's assets from
         * @param source  where it came from, for logging
         */
        public Detection(AssetVersion version, String source) {
            this.version = version;
            this.source = source;
        }

        public AssetVersion version() {
            return version;
        }

        public String source() {
            return source;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Detection)) return false;
            Detection other = (Detection) obj;
            return version == other.version && source.equals(other.source);
        }

        @Override
        public int hashCode() {
            int result = version.hashCode();
            result = 31 * result + source.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Detection{" +
                    "version=" + version +
                    ", source='" + source + '\'' +
                    '}';
        }
    }

    /**
     * Directories that only exist in one generation. Collected from the raw entry names while
     * the archive is walked, before anything is filtered out.
     *
     * <p>These beat any declared version: {@code pack.mcmeta} is hand-written and frequently
     * carries a stale or copy-pasted {@code pack_format}, whereas a directory either is or is
     * not there.
     */
    public static class Structure {
        private boolean legacyTextureDirs;
        private boolean atlases;
        private boolean itemDefinitions;

        /** @param entryName a raw archive path such as {@code assets/mymod/atlases/blocks.json} */
        public void observe(String entryName) {
            AssetPath path = AssetPath.parse(entryName);
            if (path == null) return;

            String rest = path.path();
            // 'textures/blocks' and 'textures/items' were renamed to the singular in 1.13.
            if (rest.startsWith("textures/blocks/") || rest.startsWith("textures/items/")) {
                legacyTextureDirs = true;
            }
            // Sprite atlas definitions arrived in 1.19.3.
            if (rest.startsWith("atlases/")) atlases = true;
            // Standalone item definitions arrived in 1.21.4.
            if (rest.startsWith("items/")) itemDefinitions = true;
        }

        @Nullable
        Detection detect() {
            if (itemDefinitions) return new Detection(AssetVersion.ITEM_DEFINITIONS, "assets/*/items/ (1.21.4+)");
            if (atlases) return new Detection(AssetVersion.ATLASES, "assets/*/atlases/ (1.19.3+)");
            if (legacyTextureDirs) {
                return new Detection(AssetVersion.LEGACY, "assets/*/textures/blocks/ (pre-1.13)");
            }
            return null;
        }
    }

    private VersionDetector() {
    }

    /**
     * Sources are consulted most-trustworthy first: the directory layout, then the loader
     * metadata (the loader enforces that dependency range, so it has to be right), then
     * {@code pack.mcmeta}, then the file name.
     *
     * @param metadata the contents of {@link #METADATA_FILES} that the archive actually had
     */
    public static Detection detect(String fileName, Map<String, String> metadata, Structure structure) {
        Detection structural = structure.detect();
        if (structural != null) return structural;

        for (String file : Arrays.asList("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            AssetVersion fromToml = fromModsToml(metadata.get(file));
            if (fromToml != null) return new Detection(fromToml, file);
        }

        AssetVersion fromFabric = fromFabricModJson(metadata.get("fabric.mod.json"));
        if (fromFabric != null) return new Detection(fromFabric, "fabric.mod.json");

        AssetVersion fromPack = fromPackMeta(metadata.get("pack.mcmeta"));
        if (fromPack != null) return new Detection(fromPack, "pack.mcmeta");

        AssetVersion fromName = firstVersionIn(fileName);
        if (fromName != null) return new Detection(fromName, "file name");

        return new Detection(AssetVersion.UNKNOWN.resolved(), "no version information, assuming current");
    }

    @Nullable
    private static AssetVersion fromPackMeta(@Nullable String text) {
        if (text == null) return null;

        JsonObject root = Json.parse(text);
        if (root == null || !root.has("pack") || !root.get("pack").isJsonObject()) return null;
        JsonObject pack = root.getAsJsonObject("pack");
        if (!pack.has("pack_format")) return null;

        try {
            AssetVersion version = AssetVersion.fromPackFormat(pack.get("pack_format").getAsInt());
            return version == AssetVersion.UNKNOWN ? null : version;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static AssetVersion fromModsToml(@Nullable String text) {
        if (text == null) return null;

        Matcher matcher = TOML_MINECRAFT_RANGE.matcher(text);
        return matcher.find() ? firstVersionIn(matcher.group(1)) : null;
    }

    @Nullable
    private static AssetVersion fromFabricModJson(@Nullable String text) {
        if (text == null) return null;

        JsonObject root = Json.parse(text);
        if (root == null || !root.has("depends") || !root.get("depends").isJsonObject()) return null;

        JsonElement minecraft = root.getAsJsonObject("depends").get("minecraft");
        if (minecraft == null) return null;

        // The value is either a version predicate or an array of them.
        if (minecraft.isJsonArray()) {
            for (JsonElement element : minecraft.getAsJsonArray()) {
                AssetVersion version = element.isJsonPrimitive() ? firstVersionIn(element.getAsString()) : null;
                if (version != null) return version;
            }
            return null;
        }
        return minecraft.isJsonPrimitive() ? firstVersionIn(minecraft.getAsString()) : null;
    }

    /**
     * Finds the first plausible Minecraft version in free text. Mod version numbers live in
     * the same strings, so anything below 1.6 is ignored: {@code mymod-1.2.0-1.20.1.jar}
     * must resolve to 1.20.1, not 1.2.0.
     */
    @Nullable
    private static AssetVersion firstVersionIn(String text) {
        Matcher matcher = VERSION.matcher(text);
        while (matcher.find()) {
            AssetVersion version = AssetVersion.fromMinecraftVersion(matcher.group());
            if (version != null) return version;
        }
        return null;
    }
}
