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
    private static final String KEY_SPLIT_TAB = "creative_tab.split_by_namespace";

    private static boolean splitTabByNamespace = true;

    private FeatureConfig() {
    }

    public static Path file(Path gameDir) {
        return gameDir.resolve("config").resolve(FILE_NAME);
    }

    public static boolean isSplitTabByNamespace() {
        return splitTabByNamespace;
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

        String splitVal = properties.getProperty(KEY_SPLIT_TAB);
        if (splitVal == null) {
            splitTabByNamespace = true;
        } else {
            splitTabByNamespace = Boolean.parseBoolean(splitVal.trim());
        }

        Set<String> enabled = new LinkedHashSet<>();
        boolean complete = splitVal != null;
        for (Feature feature : features) {
            String value = properties.getProperty(KEY_PREFIX + feature.id());
            if (value == null) {
                complete = false;
                if (feature.enabledByDefault()) enabled.add(feature.id());
            } else if (Boolean.parseBoolean(value.trim())) {
                enabled.add(feature.id());
            }
        }

        if (!complete) write(path, features, enabled);
        return enabled;
    }

    private static void write(Path path, List<Feature> features, Set<String> enabled) {
        StringBuilder text = new StringBuilder("# ").append(AssetBridge.MOD_NAME)
                .append(" features. Set a line to false to switch that part off.\n");
        text.append('\n')
                .append("# Split creative tabs by namespace (mod) instead of blocks and items.\n")
                .append("# If true, tabs are created per namespace (e.g. \"Asset Bridge: namespace\").\n")
                .append("# If false, two tabs \"Asset Bridge Blocks\" and \"Asset Bridge Items\" are created.\n")
                .append(KEY_SPLIT_TAB).append('=').append(splitTabByNamespace).append('\n');

        for (Feature feature : features) {
            text.append('\n')
                    .append("# ").append(feature.description()).append('\n')
                    .append(KEY_PREFIX).append(feature.id()).append('=')
                    .append(enabled.contains(feature.id())).append('\n');
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
