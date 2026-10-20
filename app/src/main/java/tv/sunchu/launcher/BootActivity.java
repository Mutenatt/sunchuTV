package tv.sunchu.launcher;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.VideoView;

/** Animación de inicio. Se muestra una vez por proceso y cede el paso a HomeActivity. */
public class BootActivity extends Activity {

    static boolean shown = false;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boot);

        VideoView video = findViewById(R.id.bootVideo);
        video.setOnCompletionListener(mp -> finishBoot());
        video.setOnErrorListener((mp, what, extra) -> {
            finishBoot();
            return true;
        });
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.boot));
        video.start();
    }

    /** Cualquier tecla saltea la animación. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            finishBoot();
        }
        return true;
    }

    private void finishBoot() {
        if (finished) return;
        finished = true;
        shown = true;
        finish();
        overridePendingTransition(0, 0);
    }
}
