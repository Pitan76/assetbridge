package net.pitan76.assetbridge.block;

import net.minecraft.util.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class BlockConfig {
    private static String defaultHardness = "1.5";
    private static String defaultResistance = "6.0";
    private static String noOcclusionBlocks = "true";
    private static String cutoutBlocks = "true";

    private static final Map<String, String> customHardness = new HashMap<>();
    private static final Map<String, String> customResistance = new HashMap<>();

    public static void read(Properties properties) {
        defaultHardness = properties.getProperty("block_hardness", "1.5").trim();
        defaultResistance = properties.getProperty("block_resistance", "6.0").trim();
        noOcclusionBlocks = properties.getProperty("no_occlusion_blocks", "true").trim();
        cutoutBlocks = properties.getProperty("feature.cutout_blocks", "true").trim();

        customHardness.clear();
        customResistance.clear();

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("block_hardness.")) {
                String blockId = key.substring("block_hardness.".length());
                customHardness.put(blockId, properties.getProperty(key).trim());
            } else if (key.startsWith("block_resistance.")) {
                String blockId = key.substring("block_resistance.".length());
                customResistance.put(blockId, properties.getProperty(key).trim());
            }
        }
    }

    public static void appendConfig(StringBuilder text, Properties properties) {
        text.append('\n')
                .append("# Default hardness (destroy time) for bridged blocks.\n")
                .append("block_hardness=").append(properties.getProperty("block_hardness", "1.5")).append('\n')
                .append("\n# Default explosion resistance for bridged blocks.\n")
                .append("block_resistance=").append(properties.getProperty("block_resistance", "6.0")).append('\n')
                .append("\n# List of blocks that should disable occlusion (render culling). Can be true (use default keyword rules), false (disable all), or comma-separated block IDs.\n")
                .append("no_occlusion_blocks=").append(properties.getProperty("no_occlusion_blocks", "true")).append('\n');

        // 個別設定がある場合はそれも書き出す
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("block_hardness.") || key.startsWith("block_resistance.")) {
                text.append(key).append('=').append(properties.getProperty(key)).append('\n');
            }
        }
    }

    public static float getHardness(ResourceLocation id) {
        String val = customHardness.get(id.toString());
        if (val == null) {
            val = defaultHardness;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return 1.5F;
        }
    }

    public static float getResistance(ResourceLocation id) {
        String val = customResistance.get(id.toString());
        if (val == null) {
            val = defaultResistance;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return 6.0F;
        }
    }

    public static boolean isCutout(ResourceLocation id) {
        if (cutoutBlocks.equalsIgnoreCase("false")) return false;
        if (cutoutBlocks.equalsIgnoreCase("true")) {
            String path = id.getPath().toLowerCase();
            return path.contains("leaves")
                    || path.contains("glass")
                    || path.contains("sapling")
                    || path.contains("crop")
                    || path.contains("flower")
                    || path.contains("plant")
                    || path.contains("cutout")
                    || path.contains("portal");
        }
        String[] ids = cutoutBlocks.split(",");
        String idStr = id.toString();
        for (String targetId : ids) {
            if (targetId.trim().equals(idStr)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldDisableOcclusion(ResourceLocation id) {
        if (noOcclusionBlocks.equalsIgnoreCase("false")) {
            return false;
        }

        if (noOcclusionBlocks.equalsIgnoreCase("true")) {
            String path = id.getPath().toLowerCase();
            return path.contains("stairs")
                    || path.contains("slab")
                    || path.contains("wall")
                    || path.contains("fence")
                    || path.contains("pane")
                    || path.contains("door")
                    || path.contains("gate")
                    || path.contains("button")
                    || path.contains("pressure_plate")
                    || path.contains("chest")
                    || path.contains("hopper")
                    || path.contains("anvil")
                    || path.contains("cauldron")
                    || path.contains("spawner")
                    || path.contains("lantern")
                    || path.contains("torch")
                    || path.contains("campfire")
                    || isCutout(id);
        }

        // Commas separated list of block IDs
        String[] ids = noOcclusionBlocks.split(",");
        String idStr = id.toString();
        for (String targetId : ids) {
            if (targetId.trim().equals(idStr)) {
                return true;
            }
        }
        return false;
    }
}
