package tv.sunchu.launcher;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP mínimo sin librerías: GET en un pool de hilos y resultado en el hilo principal. */
final class Net {

    interface Callback<T> {
        void onResult(T value, Exception error);
    }

    static final ExecutorService IO = Executors.newFixedThreadPool(4);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Net() {}

    static <T> void async(Callable<T> work, Callback<T> callback) {
        IO.execute(() -> {
            T value = null;
            Exception error = null;
            try {
                value = work.call();
            } catch (Exception e) {
                error = e;
            }
            final T v = value;
            final Exception err = error;
            MAIN.post(() -> callback.onResult(v, err));
        });
    }

    static HttpURLConnection open(String url, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new HttpError(code);
        }
        return c;
    }

    static String get(String url, String bearer) throws IOException {
        HttpURLConnection c = open(url, bearer);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    static final class HttpError extends IOException {
        private static final long serialVersionUID = 1L;
        final int code;

        HttpError(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    /** Mensaje corto y accionable para mostrar en pantalla. */
    static String explain(Exception e) {
        if (e instanceof HttpError) {
            int code = ((HttpError) e).code;
            if (code == 401) return "token de TMDB inválido (401). Revisá tmdb.token";
            if (code == 429) return "demasiadas consultas (429). Probá en un minuto";
            return "el servidor respondió " + code;
        }
        if (e instanceof java.net.UnknownHostException) return "sin conexión a internet";
        if (e instanceof java.net.SocketTimeoutException) return "la conexión tardó demasiado";
        return e == null ? "error desconocido" : e.getClass().getSimpleName();
    }
}
