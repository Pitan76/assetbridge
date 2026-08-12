package net.pitan76.assetbridge.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureConfigTest {
    @TempDir
    Path gameDir;

    private static final Feature ON = feature("on", true);
    private static final Feature OFF = feature("off", false);

    @Test
    void usesTheDefaultsAndWritesThemOutWhenThereIsNoConfig() throws IOException {
        assertEquals(Set.of("on"), FeatureConfig.read(gameDir, Arrays.asList(ON, OFF)));

        String written = Files.readString(FeatureConfig.file(gameDir), StandardCharsets.UTF_8);
        assertTrue(written.contains("feature.on=true"), written);
        assertTrue(written.contains("feature.off=false"), written);
        // The description is what tells a player what they are switching off.
        assertTrue(written.contains("# on feature"), written);
    }

    @Test
    void honoursWhatThePlayerWrote() {
        write("feature.on=false\nfeature.off=true\n");

        assertEquals(Set.of("off"), FeatureConfig.read(gameDir, Arrays.asList(ON, OFF)));
    }

    @Test
    void addsAFeatureTheConfigDoesNotMentionYet() throws IOException {
        write("feature.on=false\n");

        // A feature added in a later version keeps its default instead of counting as off.
        assertEquals(Set.of("off"), FeatureConfig.read(gameDir, Arrays.asList(ON, feature("off", true))));

        String written = Files.readString(FeatureConfig.file(gameDir), StandardCharsets.UTF_8);
        assertTrue(written.contains("feature.on=false"), written);
        assertTrue(written.contains("feature.off=true"), written);
    }

    @Test
    void treatsAnUnreadableValueAsOff() {
        write("feature.on=yes please\n");

        assertEquals(Set.of(), FeatureConfig.read(gameDir, Arrays.asList(ON)));
    }

    private void write(String contents) {
        try {
            Path file = FeatureConfig.file(gameDir);
            Files.createDirectories(file.getParent());
            Files.writeString(file, contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Feature feature(String id, boolean enabledByDefault) {
        return new Feature() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String description() {
                return id + " feature";
            }

            @Override
            public boolean enabledByDefault() {
                return enabledByDefault;
            }

            @Override
            public void apply(FeatureContext context) {
            }
        };
    }
}
