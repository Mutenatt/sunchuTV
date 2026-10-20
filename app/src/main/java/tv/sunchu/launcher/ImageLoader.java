package tv.sunchu.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

/** Carga de imágenes con caché en memoria. Evita mezclar imágenes cuando una vista se reutiliza. */
final class ImageLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ImageLoader() {}

    static void load(String url, ImageView view) {
        load(url, view, null);
    }

    static void load(String url, ImageView view, Runnable onLoaded) {
        view.setTag(url);
        if (url == null) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (onLoaded != null) onLoaded.run();
            return;
        }
        Net.async(() -> fetch(url), (bitmap, error) -> {
            if (bitmap == null) return;
            CACHE.put(url, bitmap);
            if (url.equals(view.getTag())) {
                view.setImageBitmap(bitmap);
                if (onLoaded != null) onLoaded.run();
            }
        });
    }

    /** Descarga en segundo plano sin mostrar, para que el próximo fondo ya esté en caché. */
    static void prefetch(String url) {
        if (url == null || CACHE.get(url) != null) return;
        Net.async(() -> fetch(url), (bitmap, error) -> {
            if (bitmap != null) CACHE.put(url, bitmap);
        });
    }

    private static Bitmap fetch(String url) throws IOException {
        HttpURLConnection c = Net.open(url, null);
        try (InputStream in = c.getInputStream()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565; // mitad de memoria; alcanza para fotos
            return BitmapFactory.decodeStream(in, null, o);
        } finally {
            c.disconnect();
        }
    }
}