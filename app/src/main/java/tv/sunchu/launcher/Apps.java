package tv.sunchu.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Apps instaladas con entrada de Android TV, separadas en favoritas, otras y de sistema. */
final class Apps {

    /** Orden del mock. Las que no estén instaladas simplemente no aparecen. */
    static final List<String> FAVORITES = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.spotify.tv.android",
            "tv.twitch.android.app",
            "com.directv.dtvlatam");

    static final class App {
        String pkg;
        String label;
        ActivityInfo info;
        Drawable art;
        boolean hasBanner;
        boolean system;
    }

    private Apps() {}

    static List<App> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(c.getPackageName())) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.info = ai;
            a.label = ri.loadLabel(pm).toString();
            a.art = ai.loadBanner(pm);
            a.hasBanner = a.art != null;
            if (a.art == null) a.art = ai.loadIcon(pm);
            a.system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            out.add(a);
        }
        return out;
    }

    static List<App> favorites(List<App> all) {
        List<App> out = new ArrayList<>();
        for (String pkg : FAVORITES) {
            App a = find(all, pkg);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** Apps que no son favoritas, filtradas por si son de sistema o instaladas por vos. */
    static List<App> others(List<App> all, boolean system) {
        List<App> out = new ArrayList<>();
        for (App a : all) {
            if (FAVORITES.contains(a.pkg)) continue;
            if (a.system == system) out.add(a);
        }
        out.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    static App find(List<App> all, String pkg) {
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static String signature(List<App> all) {
        StringBuilder sb = new StringBuilder();
        for (App a : all) sb.append(a.pkg).append(';');
        return sb.toString();
    }

    static boolean launch(Context c, App a) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(a.pkg, a.info.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Traduce el nombre de plataforma de TMDB al paquete instalado en el TV. */
    static String packageForProvider(String providerName) {
        String n = providerName.toLowerCase(Locale.ROOT);
        if (n.contains("netflix")) return "com.netflix.ninja";
        if (n.contains("amazon") || n.contains("prime video")) return "com.amazon.amazonvideo.livingroom";
        if (n.contains("disney")) return "com.disney.disneyplus";
        if (n.equals("max") || n.contains("hbo") || n.startsWith("max ")) return "com.wbd.stream";
        if (n.contains("directv")) return "com.directv.dtvlatam";
        if (n.contains("youtube")) return "com.google.android.youtube.tv";
        return null;
    }
}
