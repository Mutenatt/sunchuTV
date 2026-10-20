package tv.sunchu.launcher;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/** Outfit (títulos) y Plus Jakarta Sans (texto), fuentes variables empaquetadas en assets. */
final class Fonts {

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private Fonts() {}

    static Typeface display(Context c, int weight) {
        return get(c, "fonts/Outfit.ttf", weight);
    }

    static Typeface body(Context c, int weight) {
        return get(c, "fonts/PlusJakartaSans.ttf", weight);
    }

    private static Typeface get(Context c, String path, int weight) {
        String key = path + "@" + weight;
        Typeface t = CACHE.get(key);
        if (t == null) {
            t = new Typeface.Builder(c.getAssets(), path)
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (t == null) t = Typeface.DEFAULT;
            CACHE.put(key, t);
        }
        return t;
    }
}
