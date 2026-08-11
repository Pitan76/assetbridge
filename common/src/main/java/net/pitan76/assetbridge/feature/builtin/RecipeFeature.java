package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.convert.RecipeConverter;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

import java.io.IOException;
import java.util.Map;

/**
 * Serves the recipes an archive shipped, so a bridged block can also be crafted.
 *
 * <p>Only recipes the running game can read are passed through; see {@link RecipeConverter}
 * for what that excludes. Even then a recipe may name an item Asset Bridge did not bridge —
 * a machine, a tool, an ore the mod would have registered — and Minecraft drops it at load
 * time with a log line. That is expected: the recipes that survive are the ones whose
 * ingredients happen to exist, which is exactly the set that could have worked anyway.
 */
public class RecipeFeature implements Feature {
    public static final String ID = "recipes";

    private static final RecipeConverter RECIPES = new RecipeConverter();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Pass through the vanilla-format recipes found in the archives. Recipes naming items that were not bridged are dropped by Minecraft at load time.";
    }

    @Override
    public boolean enabledByDefault() {
        // Off by default: a recipe whose ingredients are missing is noise in the log, and a
        // recipe that does work can quietly change what a player can craft.
        return false;
    }

    @Override
    public void apply(FeatureContext context) {
        if (!context.isEnabled(DataPackFeature.ID)) {
            AssetBridge.LOGGER.info("Not bridging recipes: '{}' is required", DataPackFeature.ID);
            return;
        }

        int bridged = 0;
        int skipped = 0;
        for (AssetArchive archive : context.archives()) {
            for (Map.Entry<AssetPath, AssetSource> entry : archive.entries().entrySet()) {
                AssetPath path = entry.getKey();
                if (path.category() != AssetPath.Category.RECIPE) continue;

                // ターゲット環境のバージョンに合わせて、レシピの保存ディレクトリ名をマッピング (recipes/ <-> recipe/)
                boolean isModernRecipeDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.ITEM_DEFINITIONS);
                AssetPath targetPath = path;
                if (isModernRecipeDir) {
                    if (path.path().startsWith("recipes/")) {
                        String newPath = "recipe/" + path.path().substring("recipes/".length());
                        targetPath = new AssetPath(path.kind(), path.namespace(), newPath);
                    }
                } else {
                    if (path.path().startsWith("recipe/")) {
                        String newPath = "recipes/" + path.path().substring("recipe/".length());
                        targetPath = new AssetPath(path.kind(), path.namespace(), newPath);
                    }
                }

                if (context.isNamespaceUsed().test(targetPath.namespace())) continue;
                // An archive read earlier claimed this recipe already.
                if (context.assets().hasResource(targetPath)) continue;

                byte[] data;
                try {
                    data = entry.getValue().readAll();
                } catch (IOException e) {
                    AssetBridge.LOGGER.error("Could not read {} from {}", path, archive.fileName(), e);
                    continue;
                }

                byte[] converted = RECIPES.convert(path, data, archive.version());
                if (converted == null) {
                    skipped++;
                    continue;
                }
                context.assets().putResource(targetPath, converted);
                bridged++;
            }
        }
        AssetBridge.LOGGER.info("Bridged {} recipe(s); skipped {} the game could not read", bridged, skipped);
    }
}
