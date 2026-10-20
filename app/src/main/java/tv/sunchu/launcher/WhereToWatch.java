package tv.sunchu.launcher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** URL de búsqueda del título en el sitio configurado en where.url (botón del globo). */
final class WhereToWatch {

    private WhereToWatch() {}

    /**
     * Ej.: https://framextv.cc/search?q=Spider-Man%3A+Brand+New+Day
     * Busca por el título original; si no lo tiene (Mi lista vieja), usa el título que se muestra.
     */
    static String searchUrl(Tmdb.Media m) {
        String query = (m.originalTitle != null && !m.originalTitle.isEmpty()) ? m.originalTitle : m.title;
        String base = BuildConfig.WHERE_URL.replaceAll("/+$", "");
        try {
            // URLEncoder: espacio -> "+", ":" -> "%3A", "-" queda igual (el formato del ejemplo).
            return base + "/search?q=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 siempre existe
        }
    }
}
