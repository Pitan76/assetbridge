package net.pitan76.assetbridge.convert;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla texture names as they were before the 1.13 flattening, mapped to what they are now.
 *
 * <p>A pre-1.13 mod borrows vanilla sprites freely &mdash; a model that wants a wooden side
 * writes {@code blocks/planks_oak} rather than shipping its own copy. Relocating the reference
 * to {@code block/planks_oak} is not enough, because 1.13 also <em>renamed</em> the file to
 * {@code oak_planks}. Left alone the sprite is never found and the face renders magenta, which
 * is the single most common way a bridged 1.12 block comes out wrong.
 *
 * <p>The bulk of the renames are mechanical: the qualifier moved from the front to the back.
 * Those are expressed as rules rather than as a table of a thousand lines, and each rule only
 * fires when the qualifier is a wood or colour that actually existed, so a name that merely
 * looks similar &mdash; {@code glass_pane_top}, say &mdash; is left alone. The handful that were
 * renamed irregularly are listed outright.
 */
public class LegacyVanillaTextures {
    /** 1.12 wood names. {@code big_oak} is what 1.12 called dark oak in its texture names. */
    private static final Map<String, String> WOODS = new LinkedHashMap<>();

    static {
        for (String wood : Arrays.asList("oak", "spruce", "birch", "jungle", "acacia", "dark_oak")) {
            WOODS.put(wood, wood);
        }
        WOODS.put("big_oak", "dark_oak");
    }

    /** 1.12 colour names. {@code silver} became {@code light_gray}. */
    private static final Map<String, String> COLOURS = new LinkedHashMap<>();

    static {
        for (String colour : Arrays.asList("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            COLOURS.put(colour, colour);
        }
        COLOURS.put("silver", "light_gray");
    }

    /**
     * {@code <prefix>_<qualifier>} became {@code <qualifier>_<suffix>}. Ordered longest prefix
     * first, so {@code hardened_clay_stained_black} is not read as {@code hardened_clay}.
     */
    private static final Map<String, String> WOOD_RULES = new LinkedHashMap<>();
    private static final Map<String, String> COLOUR_RULES = new LinkedHashMap<>();

    static {
        WOOD_RULES.put("planks", "planks");
        WOOD_RULES.put("log", "log");
        WOOD_RULES.put("leaves", "leaves");
        WOOD_RULES.put("sapling", "sapling");

        COLOUR_RULES.put("hardened_clay_stained", "terracotta");
        COLOUR_RULES.put("wool_colored", "wool");
        COLOUR_RULES.put("glass_pane_top", "stained_glass_pane_top");
        COLOUR_RULES.put("glass", "stained_glass");
        COLOUR_RULES.put("concrete_powder", "concrete_powder");
        COLOUR_RULES.put("concrete", "concrete");
    }

    /**
     * Doors put the wood in the middle rather than at the front, so they do not fit the rule
     * above: {@code door_oak_lower} became {@code oak_door_bottom}.
     */
    private static final Map<String, String> DOOR_HALVES = new LinkedHashMap<>();

    static {
        DOOR_HALVES.put("lower", "bottom");
        DOOR_HALVES.put("upper", "top");
    }

    /** A trailing part that names a face rather than the block, e.g. {@code log_oak_top}. */
    private static final Set<String> FACE_SUFFIXES = new HashSet<>(Arrays.asList("top", "side", "bottom", "front", "end"));

    /** Renamed without a pattern to follow. */
    private static final Map<String, String> IRREGULAR = new HashMap<>();

    static {
        IRREGULAR.put("piston_top_normal", "piston_top");
        IRREGULAR.put("anvil_base", "anvil");
        IRREGULAR.put("hardened_clay", "terracotta");
        IRREGULAR.put("stonebrick", "stone_bricks");
        IRREGULAR.put("cobblestone_mossy", "mossy_cobblestone");
        IRREGULAR.put("stone_slab_top", "smooth_stone");
        IRREGULAR.put("grass_side", "grass_block_side");
        IRREGULAR.put("grass_top", "grass_block_top");
        IRREGULAR.put("mushroom_brown", "brown_mushroom");
        IRREGULAR.put("mushroom_red", "red_mushroom");
        IRREGULAR.put("web", "cobweb");
        IRREGULAR.put("brick", "bricks");
        IRREGULAR.put("quartz_block_lines", "quartz_pillar");
        IRREGULAR.put("quartz_block_lines_top", "quartz_pillar_top");
    }

    private LegacyVanillaTextures() {
    }

    /**
     * @param path a vanilla texture path with its directory already flattened, e.g.
     *             {@code block/planks_oak}
     * @return what 1.13+ calls it, or {@code null} when the name needs no change. A name that
     *         was removed rather than renamed also returns {@code null}: there is nothing
     *         truthful to point it at, and substituting a lookalike would misrepresent the mod.
     */
    @Nullable
    public static String rename(String path) {
        if (!path.startsWith("block/")) return null;
        String name = path.substring("block/".length());

        String irregular = IRREGULAR.get(name);
        if (irregular != null) return "block/" + irregular;

        String renamed = applyRules(name, WOOD_RULES, WOODS);
        if (renamed == null) renamed = applyRules(name, COLOUR_RULES, COLOURS);
        if (renamed == null) renamed = renameDoor(name);
        return renamed == null ? null : "block/" + renamed;
    }

    @Nullable
    private static String renameDoor(String name) {
        if (!name.startsWith("door_")) return null;

        int lastUnderscore = name.lastIndexOf('_');
        String half = DOOR_HALVES.get(name.substring(lastUnderscore + 1));
        if (half == null) return null;

        String wood = WOODS.get(name.substring("door_".length(), lastUnderscore));
        return wood == null ? null : wood + "_door_" + half;
    }

    @Nullable
    private static String applyRules(String name, Map<String, String> rules, Map<String, String> qualifiers) {
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            String prefix = rule.getKey() + "_";
            if (!name.startsWith(prefix)) continue;

            String rest = name.substring(prefix.length());
            // The face, where the name carried one: log_oak_top is oak_log_top, not oak_top_log.
            String face = "";
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore > 0 && FACE_SUFFIXES.contains(rest.substring(lastUnderscore + 1))) {
                face = rest.substring(lastUnderscore);
                rest = rest.substring(0, lastUnderscore);
            }

            String qualifier = qualifiers.get(rest);
            if (qualifier != null) return qualifier + "_" + rule.getValue() + face;
        }
        return null;
    }
}
