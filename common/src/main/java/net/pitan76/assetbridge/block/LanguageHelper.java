package net.pitan76.assetbridge.block;

import net.minecraft.util.text.translation.I18n;
import net.minecraft.util.text.translation.LanguageMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * On 1.12.2 the translation table lives in {@code net.minecraft.util.text.translation}, not
 * {@code net.minecraft.locale}: {@link I18n#translateToLocal} reads from the
 * {@link LanguageMap} held in {@code I18n}'s private {@code localizedName} field.
 */
public class LanguageHelper {
    @SuppressWarnings("unchecked")
    public static void injectTranslation(String key, String value) {
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
}
