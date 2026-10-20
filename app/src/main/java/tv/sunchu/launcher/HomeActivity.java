package tv.sunchu.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends Activity {

    // Paleta del mock
    static final int C_BG = 0xFF05070B;
    static final int C_TEXT = 0xFFF1F5F9;
    static final int C_SOFT = 0xFFCBD5E1;
    static final int C_MUTED = 0xFF94A3B8;
    static final int C_CYAN = 0xFF00A8FF;
    static final int C_ORANGE = 0xFFFF6B00;
    static final int C_CARD = 0xFF0C1017;
    static final int C_BUTTON = 0xFF1E293B;

    private static final String[] TABS = {"Inicio", "Películas", "Series", "Apps", "System Apps"};
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;
    private static final long ROTATION_MS = 7000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, List<Tmdb.Media>> listCache = new HashMap<>();
    private final Map<String, List<Tmdb.Media>> platformCache = new HashMap<>();
    private final List<TextView> tabViews = new ArrayList<>();

    private ScrollView scroll;
    private ImageView heroBackdrop;
    private LinearLayout railsContainer;
    private TextView heroMeta, heroTitle, heroOverview, btnPlay, btnTrailer, btnList;
    private View btnWhere;
    private TextView weatherChip, clock;
    private FrameLayout weatherPanel;
    private LinearLayout weatherCard;
    private LinearLayout myListRow;
    private LinearLayout heroButtons;
    private View firstTile;

    private MyList myList;
    private int currentTab = 0;
    private int railGeneration = 0;
    private boolean heroInitialized = false;
    private boolean appFocused = false;
    private String heroBackdropUrl;
    private String appsSignature = "";

    private Tmdb.Media currentMedia;
    private Tmdb.Media providersFor;
    private List<String> currentProviders = Collections.emptyList();
    private Runnable pendingDetails;

    // Fondo rotativo con lo popular de la plataforma de la app enfocada
    private String rotationPkg;
    private List<Tmdb.Media> rotationItems;
    private int rotationIndex;
    private Runnable pendingRotation;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BootActivity.shown) {
            startActivity(new Intent(this, BootActivity.class));
        }
        myList = new MyList(this);
        setContentView(buildUi());
        showTab(0);
        scroll.post(() -> {
            if (firstTile != null) firstTile.requestFocus();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockTick.run();
        weatherLoop.run();
        if (appFocused && rotationPkg != null) startPlatformRotation(rotationPkg);
        String sig = Apps.signature(Apps.all(this));
        if (!sig.equals(appsSignature) && (currentTab == 0 || currentTab >= 3)) {
            showTab(currentTab); // se instaló o desinstaló una app
        }
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(clockTick);
        ui.removeCallbacks(weatherLoop);
        stopRotation();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goHome(); // botón Home del control con SunchuTV como launcher
    }

    /** Atrás nunca cierra el launcher: cierra el panel o vuelve al inicio. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (weatherPanel.getVisibility() == View.VISIBLE) {
                    closeWeatherPanel();
                } else if (!isDefaultHome() && currentTab == 0 && scroll.getScrollY() == 0) {
                    finish(); // abierta desde "Apps" para probar: Atrás la cierra
                } else {
                    goHome();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = getPackageManager()
                .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void goHome() {
        if (weatherPanel.getVisibility() == View.VISIBLE) closeWeatherPanel();
        if (currentTab != 0) showTab(0);
        scroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
        else tabViews.get(0).requestFocus();
    }

    // ------------------------------------------------------------------ estructura

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);

        heroBackdrop = new ImageView(this);
        heroBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBackdrop.setAlpha(0f);
        root.addView(heroBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));

        View scrim = new View(this);
        scrim.setBackground(heroScrim());
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(561)));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setSmoothScrollingEnabled(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(24), dp(56), dp(40));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        content.addView(buildHeader());
        content.addView(buildHero());

        railsContainer = new LinearLayout(this);
        railsContainer.setOrientation(LinearLayout.VERTICAL);
        railsContainer.setClipChildren(false);
        content.addView(railsContainer);

        TextView credits = text(
                "Películas y series: datos de TMDB. Esta app usa la API de TMDB pero no está "
                        + "avalada ni certificada por TMDB. Clima: Open-Meteo.",
                Fonts.body(this, 400), 12, C_MUTED);
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = dp(36);
        content.addView(credits, clp);

        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        weatherPanel = buildWeatherPanel();
        weatherPanel.setVisibility(View.GONE);
        root.addView(weatherPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_emblem);
        logo.setAdjustViewBounds(true);
        row.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        TextView brand = text("SunchuTV", Fonts.display(this, 700), 22, C_TEXT);
        LinearLayout.LayoutParams blp = wrap();
        blp.leftMargin = dp(10);
        row.addView(brand, blp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setClipChildren(false);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView tab = text(TABS[i], Fonts.body(this, 600), 16, C_MUTED);
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            tab.setBackground(stateBg(0x3300A8FF, 0x00000000, 999));
            makeFocusable(tab, 1.06f, null);
            tab.setOnClickListener(v -> showTab(index));
            tabViews.add(tab);
            tabs.addView(tab, wrap());
        }
        LinearLayout.LayoutParams tlp = wrap();
        tlp.leftMargin = dp(36);
        row.addView(tabs, tlp);

        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        weatherChip = text("Clima…", Fonts.body(this, 600), 16, C_TEXT);
        weatherChip.setPadding(dp(16), dp(8), dp(16), dp(8));
        weatherChip.setBackground(stateBg(0x3300A8FF, 0x14FFFFFF, 999));
        makeFocusable(weatherChip, 1.06f, null);
        weatherChip.setOnClickListener(v -> openWeatherPanel());
        row.addView(weatherChip, wrap());

        clock = text("--:--", Fonts.display(this, 600), 22, C_TEXT);
        LinearLayout.LayoutParams clp = wrap();
        clp.leftMargin = dp(20);
        row.addView(clock, clp);
        return row;
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setMinimumHeight(dp(300));
        hero.setPadding(0, dp(24), 0, dp(8));
        hero.setClipChildren(false);
        hero.setClipToPadding(false);

        heroMeta = text("", Fonts.body(this, 600), 14, C_CYAN);
        hero.addView(heroMeta, wrap());

        heroTitle = text("Bienvenido a SunchuTV", Fonts.display(this, 800), 40, C_TEXT);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setMaxWidth(dp(760));
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = dp(4);
        hero.addView(heroTitle, tlp);

        heroOverview = text("Elegí una app o un título para empezar.", Fonts.body(this, 400), 15, C_SOFT);
        heroOverview.setMaxLines(3);
        heroOverview.setEllipsize(TextUtils.TruncateAt.END);
        heroOverview.setMaxWidth(dp(640));
        heroOverview.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams olp = wrap();
        olp.topMargin = dp(8);
        hero.addView(heroOverview, olp);

        LinearLayout buttons = new LinearLayout(this);
        heroButtons = buttons;
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setVisibility(View.INVISIBLE); // se muestran al enfocar un título
        buttons.setClipChildren(false);
        btnPlay = pillButton("▶  Reproducir Ahora");
        btnTrailer = pillButton("Ver Tráiler");
        btnList = pillButton("+");
        btnPlay.setOnClickListener(v -> playCurrent());
        btnTrailer.setOnClickListener(v -> openTrailer());
        btnList.setOnClickListener(v -> toggleMyList());
        buttons.addView(btnPlay, wrap());
        btnWhere = iconButton(R.drawable.ic_globe, "Dónde ver");
        btnWhere.setOnClickListener(v -> openWhere());
        LinearLayout.LayoutParams gw = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
        gw.leftMargin = dp(14);
        buttons.addView(btnWhere, gw);
        LinearLayout.LayoutParams g1 = wrap();
        g1.leftMargin = dp(14);
        buttons.addView(btnTrailer, g1);
        LinearLayout.LayoutParams g2 = wrap();
        g2.leftMargin = dp(14);
        buttons.addView(btnList, g2);
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(18);
        hero.addView(buttons, blp);
        return hero;
    }

    // ------------------------------------------------------------------ pestañas y filas

    private void showTab(int index) {
        currentTab = index;
        railGeneration++;
        stopRotation();
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            boolean on = i == index;
            t.setTextColor(on ? C_CYAN : C_MUTED);
            t.setTypeface(Fonts.body(this, on ? 700 : 600));
        }
        railsContainer.removeAllViews();
        myListRow = null;
        firstTile = null;

        List<Apps.App> all = Apps.all(this);
        appsSignature = Apps.signature(all);

        switch (index) {
            case 0:
                addAppRail("Mis Aplicaciones", Apps.favorites(all));
                addMediaRail("Tendencias de la semana", "/trending/all/week", 20, false);
                addMyListRail();
                addMediaRail("Películas populares", "/movie/popular", 20, false);
                addMediaRail("Series en tendencia", "/trending/tv/week", 20, false);
                break;
            case 1:
                addMediaRail("Tendencias", "/trending/movie/week", 20, false);
                addMediaRail("Populares en Argentina", "/movie/popular", 20, false);
                addMediaRail("Mejor puntuadas", "/movie/top_rated", 20, false);
                addMediaRail("Próximos estrenos", "/movie/upcoming", 20, false);
                break;
            case 2:
                addMediaRail("Tendencias", "/trending/tv/week", 20, false);
                addMediaRail("Populares", "/tv/popular", 20, false);
                addMediaRail("Mejor puntuadas", "/tv/top_rated", 20, false);
                addMediaRail("En emisión", "/tv/on_the_air", 20, false);
                break;
            case 3:
                addAppRail("Mis Aplicaciones", Apps.favorites(all));
                addAppRail("Otras apps instaladas", Apps.others(all, false));
                break;
            default:
                addAppRail("System Apps", Apps.others(all, true));
                break;
        }
    }

    private LinearLayout addRail(String title) {
        TextView heading = text(title, Fonts.display(this, 600), 19, C_TEXT);
        LinearLayout.LayoutParams hlp = wrap();
        hlp.topMargin = dp(20);
        railsContainer.addView(heading, hlp);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), dp(24), dp(12));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        hs.addView(row);
        railsContainer.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addAppRail(String title, List<Apps.App> apps) {
        LinearLayout row = addRail(title);
        if (apps.isEmpty()) {
            row.addView(status("No hay apps en esta categoría."));
            return;
        }
        for (Apps.App a : apps) {
            View tile = appTile(a);
            if (firstTile == null) firstTile = tile;
            row.addView(tile);
        }
    }

    private void addMediaRail(String title, String path, int limit, boolean ranked) {
        LinearLayout row = addRail(title);
        if (!Tmdb.hasToken()) {
            row.addView(status("Falta tmdb.token en local.properties."));
            return;
        }
        List<Tmdb.Media> cached = listCache.get(path);
        if (cached != null) {
            fillMedia(row, cached, limit, ranked);
            return;
        }
        TextView loading = status("Cargando…");
        row.addView(loading);
        final int gen = railGeneration;
        Net.async(() -> Tmdb.list(path), (list, error) -> {
            if (gen != railGeneration) return; // el usuario cambió de pestaña
            if (list == null) {
                loading.setText("No se pudo cargar: " + Net.explain(error) + ".");
                return;
            }
            listCache.put(path, list);
            fillMedia(row, list, limit, ranked);
        });
    }

    private void fillMedia(LinearLayout row, List<Tmdb.Media> list, int limit, boolean ranked) {
        row.removeAllViews();
        if (list.isEmpty()) {
            row.addView(status("No hay títulos para mostrar."));
            return;
        }
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            View tile = mediaTile(list.get(i), ranked ? i + 1 : 0);
            if (firstTile == null) firstTile = tile;
            row.addView(tile);
        }
        if (!heroInitialized) {
            heroInitialized = true;
            if (appFocused) setBackdrop(Tmdb.img("w1280", list.get(0).backdrop)); // no pisar la app enfocada
            else setHero(list.get(0));
        }
    }

    private void addMyListRail() {
        List<Tmdb.Media> items = myList.all();
        if (items.isEmpty()) return;
        myListRow = addRail("Mi lista");
        fillMedia(myListRow, items, 30, false);
    }

    // ------------------------------------------------------------------ tarjetas

    private View appTile(Apps.App a) {
        FrameLayout card = baseCard(dp(176), dp(99));
        ImageView img = new ImageView(this);
        img.setImageDrawable(a.art);
        if (a.hasBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(22), dp(14), dp(22), dp(14));
        }
        card.addView(img, matchParent());
        card.setContentDescription(a.label);
        makeFocusable(card, 1.05f, () -> showAppInHero(a));
        card.setOnClickListener(v -> {
            if (!Apps.launch(this, a)) toast("No se pudo abrir " + a.label + ".");
        });
        return card;
    }

    private View mediaTile(Tmdb.Media m, int rank) {
        FrameLayout card = baseCard(dp(224), dp(126));
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, matchParent());
        ImageLoader.load(Tmdb.img("w300", m.backdrop), img);

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xE6000000}));
        card.addView(shade, matchParent());

        TextView title = text(m.title, Fonts.body(this, 600), 14, C_TEXT);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(10), 0, dp(10), dp(8));
        card.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        if (rank > 0) {
            TextView badge = text(String.valueOf(rank), Fonts.display(this, 800), 18, C_TEXT);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(C_ORANGE, 8));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.START);
            blp.setMargins(dp(8), dp(8), 0, 0);
            card.addView(badge, blp);
        }

        card.setContentDescription(m.title);
        makeFocusable(card, 1.05f, () -> setHero(m));
        card.setOnClickListener(v -> {
            setHero(m);
            scroll.smoothScrollTo(0, 0);
            btnPlay.requestFocus(); // OK sobre un título lleva a sus acciones
        });
        return card;
    }

    private FrameLayout baseCard(int w, int h) {
        FrameLayout card = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(dp(8), 0, dp(8), 0);
        card.setLayoutParams(lp);
        card.setBackground(rounded(C_CARD, 10));
        card.setClipToOutline(true);
        card.setForeground(stateStroke());
        card.setOutlineSpotShadowColor(C_CYAN);
        card.setOutlineAmbientShadowColor(C_CYAN);
        return card;
    }

    // ------------------------------------------------------------------ hero

    private void setHero(Tmdb.Media m) {
        appFocused = false;
        stopRotation();
        rotationPkg = null;
        heroMeta.setVisibility(View.VISIBLE);
        heroOverview.setVisibility(View.VISIBLE);
        currentMedia = m;
        providersFor = null;
        currentProviders = Collections.emptyList();
        heroTitle.setText(m.title);
        heroOverview.setText(m.overview.isEmpty() ? "Sin sinopsis en español." : m.overview);
        heroMeta.setText(metaLine(m, null));
        btnPlay.setText("▶  Reproducir Ahora");
        btnList.setText(myList.contains(m) ? "✓" : "+");
        heroButtons.setVisibility(View.VISIBLE);
        setBackdrop(Tmdb.img("w1280", m.backdrop));

        // Las plataformas se piden con una pequeña demora para no consultar en cada movimiento del foco.
        if (pendingDetails != null) ui.removeCallbacks(pendingDetails);
        pendingDetails = () -> loadProviders(m, null);
        ui.postDelayed(pendingDetails, 450);
    }

    private void showAppInHero(Apps.App a) {
        appFocused = true;
        currentMedia = null;
        heroTitle.setText(a.label);
        // INVISIBLE (no GONE) para que el hero no cambie de alto al pasar de apps a títulos
        heroMeta.setVisibility(View.INVISIBLE);
        heroOverview.setVisibility(View.INVISIBLE);
        heroButtons.setVisibility(View.INVISIBLE);
        startPlatformRotation(a.pkg);
    }

    private void startPlatformRotation(String pkg) {
        stopRotation();
        rotationPkg = pkg;
        if (!Tmdb.hasToken()) return;
        // Pequeña demora: si el usuario sigue moviéndose entre apps no se consulta nada.
        pendingRotation = () -> {
            List<Tmdb.Media> cached = platformCache.get(pkg);
            if (cached != null) {
                beginRotation(pkg, cached);
                return;
            }
            Net.async(() -> Tmdb.platformTop(pkg), (list, error) -> {
                if (list == null) return; // sin datos: queda el fondo actual
                platformCache.put(pkg, list);
                beginRotation(pkg, list);
            });
        };
        ui.postDelayed(pendingRotation, 400);
    }

    private void beginRotation(String pkg, List<Tmdb.Media> items) {
        // Spotify, Twitch, etc. no tienen catálogo en TMDB: se mantiene el fondo actual.
        if (!appFocused || !pkg.equals(rotationPkg) || items.isEmpty()) return;
        rotationItems = items;
        rotationIndex = 0;
        rotationTick.run();
    }

    private final Runnable rotationTick = new Runnable() {
        @Override
        public void run() {
            List<Tmdb.Media> items = rotationItems;
            if (items == null || items.isEmpty()) return;
            setBackdrop(Tmdb.img("w1280", items.get(rotationIndex % items.size()).backdrop));
            rotationIndex++;
            ImageLoader.prefetch(Tmdb.img("w1280", items.get(rotationIndex % items.size()).backdrop));
            ui.postDelayed(this, ROTATION_MS);
        }
    };

    private void stopRotation() {
        if (pendingRotation != null) ui.removeCallbacks(pendingRotation);
        ui.removeCallbacks(rotationTick);
        rotationItems = null;
    }

    private String metaLine(Tmdb.Media m, List<String> providers) {
        StringBuilder sb = new StringBuilder(m.type.equals("tv") ? "Serie" : "Película");
        if (!m.year.isEmpty()) sb.append("   ").append(m.year);
        if (m.vote > 0) sb.append("   ★ ").append(String.format(Locale.US, "%.1f", m.vote));
        if (providers != null && !providers.isEmpty()) {
            sb.append("   En ").append(TextUtils.join(", ", providers));
        }
        return sb.toString();
    }

    private void setBackdrop(String url) {
        if (url == null || url.equals(heroBackdropUrl)) return;
        heroBackdropUrl = url;
        heroBackdrop.animate().alpha(0f).setDuration(150).withEndAction(() ->
                ImageLoader.load(url, heroBackdrop, () ->
                        heroBackdrop.animate().alpha(1f).setDuration(350).start())).start();
    }

    private void loadProviders(Tmdb.Media m, Runnable then) {
        Net.async(() -> Tmdb.providers(m), (list, error) -> {
            if (m != currentMedia) return;
            providersFor = m;
            currentProviders = list == null ? Collections.emptyList() : list;
            heroMeta.setText(metaLine(m, currentProviders));
            Apps.App app = installedProviderApp();
            btnPlay.setText(app != null ? "▶  Ver en " + app.label : "▶  Reproducir Ahora");
            if (then != null) then.run();
        });
    }

    private Apps.App installedProviderApp() {
        List<Apps.App> all = Apps.all(this);
        for (String name : currentProviders) {
            String pkg = Apps.packageForProvider(name);
            if (pkg == null) continue;
            Apps.App a = Apps.find(all, pkg);
            if (a != null) return a;
        }
        return null;
    }

    private void playCurrent() {
        Tmdb.Media m = currentMedia;
        if (m == null) {
            toast("Elegí una película o serie de las filas.");
            return;
        }
        if (providersFor != m) {
            loadProviders(m, this::playCurrent);
            return;
        }
        Apps.App app = installedProviderApp();
        if (app != null) {
            if (!Apps.launch(this, app)) toast("No se pudo abrir " + app.label + ".");
        } else if (currentProviders.isEmpty()) {
            toast("“" + m.title + "” no figura en ninguna plataforma de streaming en Argentina.");
        } else {
            toast("Está en " + TextUtils.join(", ", currentProviders) + ", pero esa app no está instalada.");
        }
    }

    private void openTrailer() {
        Tmdb.Media m = currentMedia;
        if (m == null) {
            toast("Elegí una película o serie de las filas.");
            return;
        }
        Net.async(() -> Tmdb.trailerKey(m), (key, error) -> {
            if (key == null) {
                toast(error != null ? "No se pudo buscar el tráiler: " + Net.explain(error) + "."
                        : "No hay tráiler disponible para “" + m.title + "”.");
                return;
            }
            Intent yt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key))
                    .setPackage("com.google.android.youtube.tv")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(yt);
            } catch (ActivityNotFoundException e) {
                toast("Instalá YouTube para ver tráileres.");
            }
        });
    }

    /** Globo: abre en el navegador la búsqueda del título en el sitio de where.url. */
    private void openWhere() {
        Tmdb.Media m = currentMedia;
        if (m == null) {
            toast("Elegí una película o serie de las filas.");
            return;
        }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(WhereToWatch.searchUrl(m)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(web);
        } catch (ActivityNotFoundException e) {
            toast("Este TV no tiene un navegador instalado.");
        }
    }

    private void toggleMyList() {
        Tmdb.Media m = currentMedia;
        if (m == null) {
            toast("Elegí una película o serie para agregarla a Mi lista.");
            return;
        }
        boolean added = myList.toggle(m);
        btnList.setText(added ? "✓" : "+");
        toast(added ? "Agregada a Mi lista." : "Quitada de Mi lista.");
        if (myListRow != null) fillMedia(myListRow, myList.all(), 30, false);
    }

    // ------------------------------------------------------------------ clima y reloj

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            ui.postDelayed(this, 10_000);
        }
    };

    private final Runnable weatherLoop = new Runnable() {
        @Override
        public void run() {
            Net.async(Weather::fetch, (w, error) -> {
                if (w != null) {
                    Weather.last = w;
                    weatherChip.setText(Weather.emoji(w.code, w.day) + "  " + Weather.round(w.temp)
                            + "  " + BuildConfig.WEATHER_PLACE);
                } else if (Weather.last == null) {
                    weatherChip.setText("Clima no disponible");
                }
            });
            ui.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    private FrameLayout buildWeatherPanel() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackground(new ColorDrawable(0xD9000000));
        weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(36), dp(32), dp(36), dp(28));
        weatherCard.setBackground(rounded(0xFF0F141C, 20));
        overlay.addView(weatherCard, new FrameLayout.LayoutParams(dp(660),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return overlay;
    }

    private void openWeatherPanel() {
        Weather w = Weather.last;
        if (w == null) {
            toast("Todavía no hay datos del clima.");
            return;
        }
        weatherCard.removeAllViews();
        weatherCard.addView(text("Clima en " + BuildConfig.WEATHER_PLACE + ", Córdoba",
                Fonts.body(this, 600), 16, C_MUTED), wrap());

        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.addView(text(Weather.emoji(w.code, w.day), Fonts.body(this, 400), 48, C_TEXT), wrap());
        TextView big = text(Weather.round(w.temp), Fonts.display(this, 800), 64, C_TEXT);
        LinearLayout.LayoutParams bl = wrap();
        bl.leftMargin = dp(16);
        now.addView(big, bl);
        TextView desc = text(Weather.describe(w.code) + "\nSensación " + Weather.round(w.feels),
                Fonts.body(this, 500), 17, C_SOFT);
        desc.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams dl = wrap();
        dl.leftMargin = dp(20);
        now.addView(desc, dl);
        LinearLayout.LayoutParams nl = wrap();
        nl.topMargin = dp(8);
        weatherCard.addView(now, nl);

        TextView day = text("Máxima " + Weather.round(w.max) + "    Mínima " + Weather.round(w.min)
                        + "\nAmanecer " + w.sunrise + "    Atardecer " + w.sunset,
                Fonts.body(this, 500), 15, C_SOFT);
        day.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams dyl = wrap();
        dyl.topMargin = dp(12);
        weatherCard.addView(day, dyl);

        LinearLayout hours = new LinearLayout(this);
        hours.setOrientation(LinearLayout.HORIZONTAL);
        for (Weather.Hour h : w.next) {
            TextView col = text(h.time + "\n" + Weather.emoji(h.code, h.day) + "\n" + Weather.round(h.temp),
                    Fonts.body(this, 600), 15, C_TEXT);
            col.setGravity(Gravity.CENTER);
            col.setLineSpacing(0, 1.3f);
            hours.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(22);
        weatherCard.addView(hours, hl);

        TextView close = pillButton("Cerrar");
        close.setOnClickListener(v -> closeWeatherPanel());
        LinearLayout.LayoutParams cl = wrap();
        cl.topMargin = dp(24);
        cl.gravity = Gravity.END;
        weatherCard.addView(close, cl);

        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        weatherPanel.setVisibility(View.VISIBLE);
        close.requestFocus();
    }

    private void closeWeatherPanel() {
        weatherPanel.setVisibility(View.GONE);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        weatherChip.requestFocus();
    }

    // ------------------------------------------------------------------ helpers visuales

    /** Foco del mock: leve aumento, sube 4dp y brillo cian. */
    private void makeFocusable(View v, float scale, Runnable onFocus) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate()
                    .scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                    .translationY(has ? -dp(4) : 0)
                    .translationZ(has ? dp(14) : 0)
                    .setDuration(180).start();
            if (has && onFocus != null) onFocus.run();
        });
    }

    private TextView pillButton(String label) {
        TextView b = text(label, Fonts.body(this, 700), 16, C_TEXT);
        b.setPadding(dp(24), dp(11), dp(24), dp(11));
        b.setMinWidth(dp(52));
        b.setGravity(Gravity.CENTER);
        b.setBackground(stateBg(0xFFFFFFFF, C_BUTTON, 12));
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{C_BG, C_TEXT}));
        makeFocusable(b, 1.05f, null);
        return b;
    }

    /** Botón de solo ícono con el mismo foco que pillButton: fondo blanco e ícono oscuro. */
    private ImageView iconButton(int drawableRes, String description) {
        ImageView b = new ImageView(this);
        b.setImageResource(drawableRes);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(25), dp(10), dp(25), dp(10));
        b.setBackground(stateBg(0xFFFFFFFF, C_BUTTON, 12));
        b.setImageTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{C_BG, C_TEXT}));
        b.setContentDescription(description);
        makeFocusable(b, 1.05f, null);
        return b;
    }

    private TextView text(String s, Typeface tf, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView status(String s) {
        TextView t = text(s, Fonts.body(this, 500), 15, C_MUTED);
        t.setPadding(dp(8), dp(24), dp(8), dp(24));
        return t;
    }

    private Drawable heroScrim() {
        GradientDrawable left = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{C_BG, 0xCC05070B, 0x0005070B});
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4005070B, 0x0005070B, C_BG});
        return new LayerDrawable(new Drawable[]{left, bottom});
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private StateListDrawable stateBg(int focused, int normal, int radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(focused, radiusDp));
        s.addState(new int[]{}, rounded(normal, radiusDp));
        return s;
    }

    private StateListDrawable stateStroke() {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x00000000);
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), C_CYAN);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, ring);
        s.addState(new int[]{}, new ColorDrawable(0x00000000));
        return s;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}