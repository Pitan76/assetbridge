package net.pitan76.assetbridge.block;

import net.minecraft.util.text.translation.I18n;
import net.minecraft.util.text.translation.LanguageMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.12.2 actually has two, entirely separate translation tables, and this injects into both:
 * <ul>
 *   <li>{@code net.minecraft.util.text.translation.I18n}/{@link LanguageMap} -- what
 *       {@code Block#getLocalizedName()}/{@code Item#getItemStackDisplayName()} read (block/item
 *       names).</li>
 *   <li>{@code net.minecraft.client.resources.I18n}, which instead reads from a
 *       {@code net.minecraft.client.resources.Locale} held in its private {@code i18nLocale}
 *       field -- what {@code CreativeTabs}' own label lookup uses ({@code GuiContainerCreative}
 *       calls this I18n, not the other one). A key injected only into the first table renders
 *       correctly as a block/item name but leaves the creative tab showing its raw,
 *       untranslated key.
 * </ul>
 */
public class LanguageHelper {
    @SuppressWarnings("unchecked")
    public static void injectTranslation(String key, String value) {
        injectIntoLanguageMap(key, value);
        injectIntoClientLocale(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoLanguageMap(String key, String value) {
        try {
            LanguageMap map = activeLanguageMap();
            if (map == null) return;

            // Look for any Map field on LanguageMap, same as before: resilient to the field
            // actually being named something else on a given build.
            for (Field field : LanguageMap.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Map<Object, Object> entries = (Map<Object, Object>) field.get(map);
                try {
                    entries.put(key, value);
                } catch (UnsupportedOperationException e) {
                    Map<Object, Object> mutable = new HashMap<>(entries);
                    mutable.put(key, value);
                    field.set(map, mutable);
                }
                return;
            }
        } catch (Throwable e) {
            // Fallback: do not crash the game.
        }
    }

    private static LanguageMap activeLanguageMap() throws ReflectiveOperationException {
        Field field = I18n.class.getDeclaredField("localizedName");
        field.setAccessible(true);
        return (LanguageMap) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoClientLocale(String key, String value) {
        try {
            Class<?> i18nClass = Class.forName("net.minecraft.client.resources.I18n");
            Field localeField = i18nClass.getDeclaredField("i18nLocale");
            localeField.setAccessible(true);
            Object locale = localeField.get(null);
            if (locale == null) return;

            Field propertiesField = locale.getClass().getDeclaredField("properties");
            propertiesField.setAccessible(true);
            Map<Object, Object> properties = (Map<Object, Object>) propertiesField.get(locale);
            try {
                properties.put(key, value);
            } catch (UnsupportedOperationException e) {
                Map<Object, Object> mutable = new HashMap<>(properties);
                mutable.put(key, value);
                propertiesField.set(locale, mutable);
            }
        } catch (Throwable e) {
            // Fallback: do not crash the game.
        }
    }
}
