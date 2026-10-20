package tv.sunchu.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class HomeActivity extends Activity {

    private TextView heroTitle;
    private TextView heroSub;
    private LinearLayout appsRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BootActivity.shown) {
            startActivity(new Intent(this, BootActivity.class));
        }
        setContentView(R.layout.activity_home);

        heroTitle = findViewById(R.id.heroTitle);
        heroSub = findViewById(R.id.heroSub);
        appsRow = findViewById(R.id.appsRow);

        loadApps();

        getWindow().getDecorView().post(() -> {
            if (appsRow.getChildCount() > 0) {
                appsRow.getChildAt(0).requestFocus();
            }
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);

        int tileW = dp(200);
        int tileH = dp(113);

        for (ResolveInfo ri : apps) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName())) continue;

            String label = ri.loadLabel(pm).toString();
            Drawable art = ai.loadBanner(pm);
            if (art == null) art = ai.loadIcon(pm);

            ImageView tile = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.setMargins(dp(12), dp(8), dp(12), dp(8));
            tile.setLayoutParams(lp);
            tile.setImageDrawable(art);
            tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.setBackgroundResource(R.drawable.tile_focus);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setContentDescription(label);

            tile.setOnClickListener(v -> launch(ai));
            tile.setOnFocusChangeListener((v, hasFocus) -> {
                float s = hasFocus ? 1.08f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(180).start();
                if (hasFocus) {
                    heroTitle.setText(label);
                    heroSub.setText("Presioná OK para abrir");
                }
            });

            appsRow.addView(tile);
        }
    }

    private void launch(ActivityInfo ai) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
