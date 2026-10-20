package tv.sunchu.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cliente TMDB: listas, plataformas en Argentina y tráiler. Usa el Read Access Token (Bearer). */
final class Tmdb {

    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/";

    static final class Media {
        int id;
        String type;      // "movie" o "tv"
        String title;
        String originalTitle; // título en su idioma original, ej. "Spider-Man: Brand New Day"
        String overview;
        String backdrop;  // ruta TMDB, ej. "/abc.jpg"
        String year;
        double vote;
        String originalLanguage; // ISO 639-1, ej. "en", "ja", "ko"

        String key() {
            return type + ":" + id;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id).put("type", type).put("title", title)
                    .put("otitle", originalTitle == null ? "" : originalTitle)
                    .put("overview", overview).put("backdrop", backdrop)
                    .put("year", year).put("vote", vote)
                    .put("lang", originalLanguage == null ? "" : originalLanguage);
        }

        static Media fromJson(JSONObject o) {
            Media m = new Media();
            m.id = o.optInt("id");
            m.type = o.optString("type", "movie");
            m.title = o.optString("title", "");
            m.originalTitle = o.optString("otitle", ""); // Mi lista guardada antes de este cambio: queda vacío
            m.overview = o.optString("overview", "");
            m.backdrop = o.optString("backdrop", null);
            m.year = o.optString("year", "");
            m.vote = o.optDouble("vote", 0);
            m.originalLanguage = o.optString("lang", "");
            return m;
        }
    }

    private Tmdb() {}

    static boolean hasToken() {
        return !BuildConfig.TMDB_TOKEN.isEmpty();
    }

    static String img(String size, String path) {
        return path == null ? null : IMG + size + path;
    }

    /** path: por ejemplo "/trending/all/week" o "/movie/popular". */
    static List<Media> list(String path) throws Exception {
        // En /discover, "region" filtra por fecha de estreno local y recorta resultados: no se usa.
        String url = API + path + (path.contains("?") ? "&" : "?")
                + "language=" + BuildConfig.TMDB_LANG
                + (path.startsWith("/discover") ? "" : "&region=" + BuildConfig.TMDB_REGION);
        JSONArray results = new JSONObject(Net.get(url, BuildConfig.TMDB_TOKEN)).optJSONArray("results");
        List<Media> out = new ArrayList<>();
        if (results == null) return out;

        String defaultType = path.contains("/tv") ? "tv" : "movie";
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String type = r.optString("media_type", defaultType);
            if (!type.equals("movie") && !type.equals("tv")) continue;

            Media m = new Media();
            m.id = r.getInt("id");
            m.type = type;
            m.title = type.equals("tv") ? r.optString("name", "") : r.optString("title", "");
            m.originalTitle = type.equals("tv") ? r.optString("original_name", "") : r.optString("original_title", "");
            m.overview = r.optString("overview", "");
            m.backdrop = optPath(r, "backdrop_path");
            String date = r.optString(type.equals("tv") ? "first_air_date" : "release_date", "");
            m.year = date.length() >= 4 ? date.substring(0, 4) : "";
            m.vote = r.optDouble("vote_average", 0);
            m.originalLanguage = r.optString("original_language", "");
            if (m.backdrop == null || m.title.isEmpty()) continue;
            out.add(m);
        }
        return out;
    }

    /** Nombres de plataformas donde se puede ver en la región configurada (suscripción o gratis). */
    static List<String> providers(Media m) throws Exception {
        JSONObject root = new JSONObject(Net.get(
                API + "/" + m.type + "/" + m.id + "/watch/providers", BuildConfig.TMDB_TOKEN));
        JSONObject results = root.optJSONObject("results");
        JSONObject region = results == null ? null : results.optJSONObject(BuildConfig.TMDB_REGION);
        if (region == null) return Collections.emptyList();

        List<String> names = new ArrayList<>();
        for (String kind : new String[]{"flatrate", "free", "ads"}) {
            JSONArray arr = region.optJSONArray(kind);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getJSONObject(i).optString("provider_name", "");
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    /**
     * Clave de YouTube del tráiler en el idioma original del título.
     * Prioridad: tráiler original, teaser original, tráiler en inglés, cualquier video de YouTube.
     */
    static String trailerKey(Media m) throws Exception {
        String orig = (m.originalLanguage == null || m.originalLanguage.isEmpty()) ? "en" : m.originalLanguage;
        String url = API + "/" + m.type + "/" + m.id + "/videos?include_video_language="
                + orig + ",en,null";
        JSONArray videos = new JSONObject(Net.get(url, BuildConfig.TMDB_TOKEN)).optJSONArray("results");
        if (videos == null) return null;

        String[] best = new String[4];
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.getJSONObject(i);
            String key = v.optString("key", "");
            if (!"YouTube".equals(v.optString("site")) || key.isEmpty()) continue;
            String lang = v.optString("iso_639_1", "");
            String type = v.optString("type", "");
            boolean original = lang.equals(orig);
            int rank;
            if (original && type.equals("Trailer")) rank = 0;
            else if (original && type.equals("Teaser")) rank = 1;
            else if (lang.equals("en") && type.equals("Trailer")) rank = 2;
            else rank = 3;
            if (best[rank] == null) best[rank] = key;
        }
        for (String key : best) if (key != null) return key;
        return null;
    }

    private static Map<String, String> providerIdsByPackage;

    /**
     * IDs de plataforma de TMDB para la región, agrupados por la app del TV que los reproduce.
     * Ej. "com.netflix.ninja" -> "8|1796" (Netflix y Netflix con anuncios).
     */
    static synchronized Map<String, String> providerIdsByPackage() throws Exception {
        if (providerIdsByPackage != null) return providerIdsByPackage;
        Map<String, Set<String>> acc = new HashMap<>();
        for (String kind : new String[]{"movie", "tv"}) {
            JSONArray arr = new JSONObject(Net.get(API + "/watch/providers/" + kind
                    + "?watch_region=" + BuildConfig.TMDB_REGION, BuildConfig.TMDB_TOKEN)).optJSONArray("results");
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String pkg = Apps.packageForProvider(p.optString("provider_name", ""));
                if (pkg == null) continue;
                Set<String> ids = acc.get(pkg);
                if (ids == null) {
                    ids = new LinkedHashSet<>();
                    acc.put(pkg, ids);
                }
                ids.add(String.valueOf(p.optInt("provider_id")));
            }
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : acc.entrySet()) out.put(e.getKey(), String.join("|", e.getValue()));
        providerIdsByPackage = out;
        return out;
    }

    /** Hasta 10 títulos populares disponibles en la plataforma de esa app: películas y series intercaladas. */
    static List<Media> platformTop(String pkg) throws Exception {
        String ids = providerIdsByPackage().get(pkg);
        if (ids == null) return Collections.emptyList();
        String query = "?sort_by=popularity.desc"
                + "&watch_region=" + BuildConfig.TMDB_REGION
                + "&with_watch_providers=" + URLEncoder.encode(ids, "UTF-8")
                + "&with_watch_monetization_types=" + URLEncoder.encode("flatrate|free|ads", "UTF-8");
        List<Media> movies = list("/discover/movie" + query);
        List<Media> series = list("/discover/tv" + query);
        List<Media> out = new ArrayList<>();
        for (int i = 0; out.size() < 10 && (i < movies.size() || i < series.size()); i++) {
            if (i < series.size() && out.size() < 10) out.add(series.get(i));
            if (i < movies.size() && out.size() < 10) out.add(movies.get(i));
        }
        return out;
    }

    private static String optPath(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String s = o.optString(key, "");
        return s.isEmpty() ? null : s;
    }
}