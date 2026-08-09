package net.pitan76.assetbridge.feature;

import net.pitan76.assetbridge.AssetBridge;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Reads which features are switched on from {@code config/assetbridge.properties}.
 *
 * <p>Plain properties on purpose: the file has to be editable by a player who is diagnosing
 * a crash, and it must not pull a config library into a mod that is meant to stay small.
 * Missing keys are written back with their default, so a newly added feature documents
 * itself the first time the game starts.
 */
public class FeatureConfig {
    private static final String FILE_NAME = AssetBridge.MOD_ID + ".properties";
    private static final String KEY_PREFIX = "feature.";

    private FeatureConfig() {
    }

    public static Path file(Path gameDir) {
        return gameDir.resolve("config").resolve(FILE_NAME);
    }

    /** The ids of the features the player left switched on. Never fails: defaults win. */
    public static Set<String> read(Path gameDir, List<Feature> features) {
        Path path = file(gameDir);
        Properties properties = new Properties();

        if (Files.isRegularFile(path)) {
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException e) {
                AssetBridge.LOGGER.warn("Could not read {}; every feature keeps its default", path, e);
            }
        }

        Set<String> enabled = new LinkedHashSet<>();
        java.util.Map<String, String> valuesMap = new java.util.HashMap<>();
        boolean complete = true;
        for (Feature feature : features) {
            String value = properties.getProperty(KEY_PREFIX + feature.id());
            if (value == null) {
                complete = false;
                String defaultValue = String.valueOf(feature.enabledByDefault());
                valuesMap.put(feature.id(), defaultValue);
                if (feature.enabledByDefault()) enabled.add(feature.id());
            } else {
                String trimmed = value.trim();
                valuesMap.put(feature.id(), trimmed);
                if (trimmed.equalsIgnoreCase("true") || (!trimmed.equalsIgnoreCase("false") && isCustomValidValue(feature.id(), trimmed))) {
                    enabled.add(feature.id());
                }
            }
        }

        Features.setConfigValues(valuesMap);

        if (!complete) write(path, features, valuesMap);
        return enabled;
    }

    private static boolean isCustomValidValue(String featureId, String value) {
        if (featureId.equals("cutout_blocks")) {
            return value.contains(":") || value.contains(",");
        }
        return false;
    }

    private static void write(Path path, List<Feature> features, java.util.Map<String, String> valuesMap) {
        StringBuilder text = new StringBuilder("# ").append(AssetBridge.MOD_NAME)
                .append(" features. Set a line to false to switch that part off.\n");
        for (Feature feature : features) {
            text.append('\n')
                    .append("# ").append(feature.description()).append('\n')
                    .append(KEY_PREFIX).append(feature.id()).append('=')
                    .append(valuesMap.getOrDefault(feature.id(), String.valueOf(feature.enabledByDefault()))).append('\n');
        }

        try {
            Files.createDirectories(path.getParent());
            try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                out.write(text.toString());
            }
        } catch (IOException e) {
            AssetBridge.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
