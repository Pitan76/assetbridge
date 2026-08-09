package net.pitan76.assetbridge.block;

import net.minecraft.locale.Language;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class LanguageHelper {
    @SuppressWarnings("unchecked")
    public static void injectTranslation(String key, String value) {
        try {
            Language language = Language.getInstance();
            // Look for any Map field in the runtime Language implementation class
            Class<?> clazz = language.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Map<Object, Object> map = (Map<Object, Object>) field.get(language);
                        try {
                            map.put(key, value);
                        } catch (UnsupportedOperationException e) {
                            Map<Object, Object> mutableMap = new HashMap<>(map);
                            mutableMap.put(key, value);
                            field.set(language, mutableMap);
                        }
                        return;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable e) {
            // Fallback: do not crash the game.
        }
    }
}
