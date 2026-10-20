package tv.sunchu.launcher;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Protector de pantalla: imágenes en tendencia de TMDB con reloj grande y clima. */
public class SunchuDream extends DreamService {

    private static final long SLIDE_MS = 12_000;
    private static final Locale ES = new Locale("es", "AR");

    private final Handler h = new Handler(Looper.getMainLooper());
    private ImageView front, back;
    private TextView clock, date, caption, weather;
    private List<Tmdb.Media> items = new ArrayList<>();
    private int index = 0;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // cualquier tecla lo cierra
        setFullscreen(true);
        setContentView(buildUi());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        clockTick.run();
        if (Tmdb.hasToken()) {
            Net.async(() -> Tmdb.list("/trending/all/week"), (list, error) -> {
                if (list != null && !list.isEmpty()) {
                    items = list;
                    showNext();
                }
            });
        }
    }

    @Override
    public void onDreamingStopped() {
        h.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", ES).format(now));
            String d = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(now);
            date.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
            Weather w = Weather.last;
            weather.setText(w == null ? "" : Weather.emoji(w.code, w.day) + "  " + Weather.round(w.temp)
                    + "  " + Weather.describe(w.code) + " en " + BuildConfig.WEATHER_PLACE);
            h.postDelayed(this, 10_000);
        }
    };

    private void showNext() {
        Tmdb.Media m = items.get(index % items.size());
        index++;
        final ImageView incoming = back;
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        ImageLoader.load(Tmdb.img("w1280", m.backdrop), incoming, () -> {
            incoming.animate().alpha(1f).setDuration(1500).start();
            incoming.animate().scaleX(1.06f).scaleY(1.06f).setDuration(SLIDE_MS + 1500).start();
            front.animate().alpha(0f).setDuration(1500).start();
            ImageView old = front;
            front = incoming;
            back = old;
            caption.setText(m.title);
        });
        h.postDelayed(this::showNext, SLIDE_MS);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(HomeActivity.C_BG);

        front = image();
        back = image();
        root.addView(front, match());
        root.addView(back, match());

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x33000000, 0xE6000000}));
        root.addView(scrim, match());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("En tendencia esta semana", Fonts.body(this, 600), 16, HomeActivity.C_CYAN);
        caption = text("SunchuTV", Fonts.display(this, 700), 34, HomeActivity.C_TEXT);
        caption.setMaxWidth(dp(720));
        left.addView(label);
        left.addView(caption);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        llp.setMargins(dp(64), 0, 0, dp(56));
        root.addView(left, llp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clock = text("--:--", Fonts.display(this, 300), 110, HomeActivity.C_TEXT);
        date = text("", Fonts.body(this, 500), 20, HomeActivity.C_SOFT);
        weather = text("", Fonts.body(this, 500), 18, HomeActivity.C_SOFT);
        right.addView(clock);
        right.addView(date);
        right.addView(weather);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        rlp.setMargins(0, 0, dp(64), dp(48));
        root.addView(right, rlp);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_emblem);
        logo.setAdjustViewBounds(true);
        logo.setAlpha(0.85f);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.START);
        glp.setMargins(dp(64), dp(40), 0, 0);
        root.addView(logo, glp);
        return root;
    }

    private ImageView image() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private TextView text(String s, android.graphics.Typeface tf, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
