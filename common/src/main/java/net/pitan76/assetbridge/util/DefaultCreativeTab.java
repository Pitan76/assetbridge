package net.pitan76.assetbridge.util;

/**
 * Assigns the vanilla default creative tab (Building Blocks or Miscellaneous) to a
 * {@code Block}/{@code Item} entirely by reflection, for platforms where
 * {@link net.pitan76.assetbridge.block.BridgedItemGroup}'s own {@code CreativeTabs}-typed API
 * cannot be used (see {@link net.pitan76.assetbridge.block.BridgedItemGroup#creativeTabsSupported()}).
 *
 * <p>MCP (Forge) names the tab class {@code net.minecraft.creativetab.CreativeTabs}; Legacy Yarn
 * (Legacy Fabric) names the very same game class {@code net.minecraft.item.itemgroup.ItemGroup}.
 * Both mappings keep the constant field names identical ({@code BUILDING_BLOCKS}/{@code MISC});
 * only the class name and the setter method name ({@code setCreativeTab} vs {@code setItemGroup})
 * differ. Resolving both class name and method name reflectively -- rather than referencing
 * either statically -- means this same code works whichever platform it runs on.
 */
public final class DefaultCreativeTab {
    private static final String[] TAB_CLASSES = {
            "net.minecraft.creativetab.CreativeTabs",
            "net.minecraft.item.itemgroup.ItemGroup",
    };
    private static final String[] SETTER_METHODS = {"setCreativeTab", "setItemGroup"};

    private DefaultCreativeTab() {
    }

    public static void assignDefault(Object itemOrBlock, boolean isBlock) {
        String fieldName = isBlock ? "BUILDING_BLOCKS" : "MISC";
        for (String className : TAB_CLASSES) {
            try {
                Class<?> tabClass = Class.forName(className);
                Object tab = tabClass.getField(fieldName).get(null);
                for (String methodName : SETTER_METHODS) {
                    try {
                        itemOrBlock.getClass().getMethod(methodName, tabClass).invoke(itemOrBlock, tab);
                        return;
                    } catch (ReflectiveOperationException ignored) {
                        // Try the next mapping's method name.
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping's class name.
            }
        }
    }
}
