package tv.sunchu.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** "Mi lista": títulos guardados con el botón + del hero. Se guarda en el TV. */
final class MyList {

    private final SharedPreferences prefs;

    MyList(Context c) {
        prefs = c.getSharedPreferences("mylist", Context.MODE_PRIVATE);
    }

    List<Tmdb.Media> all() {
        List<Tmdb.Media> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Tmdb.Media.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
            // Datos corruptos: se tratan como lista vacía.
        }
        return out;
    }

    boolean contains(Tmdb.Media m) {
        for (Tmdb.Media x : all()) if (x.key().equals(m.key())) return true;
        return false;
    }

    /** Agrega o quita. Devuelve true si quedó en la lista. */
    boolean toggle(Tmdb.Media m) {
        List<Tmdb.Media> items = all();
        boolean removed = items.removeIf(x -> x.key().equals(m.key()));
        if (!removed) items.add(0, m);
        JSONArray arr = new JSONArray();
        try {
            for (Tmdb.Media x : items) arr.put(x.toJson());
        } catch (JSONException ignored) {
            return contains(m);
        }
        prefs.edit().putString("items", arr.toString()).apply();
        return !removed;
    }
}
