package ca.punch.heures;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/** Widget d'écran d'accueil : gros bouton IN/OUT, chrono, raccourcis clients. */
public class PunchWidget extends AppWidgetProvider {
    static final String ACTION = "ca.punch.heures.WIDGET";
    private static final int[] CLIENT_IDS = { R.id.w_c1, R.id.w_c2, R.id.w_c3 };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(ctx, m, id);
    }

    static void refreshAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, PunchWidget.class));
        for (int id : ids) update(ctx, m, id);
    }

    private static PendingIntent action(Context ctx, int requestCode, String op, String clientId) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(ACTION);
        i.putExtra("op", op);
        if (clientId != null) i.putExtra("clientId", clientId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void update(Context ctx, AppWidgetManager m, int widgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.punch_widget);
        JSONObject s;
        try {
            s = new JSONObject(ctx.getSharedPreferences(PunchNativePlugin.PREFS, Context.MODE_PRIVATE).getString("state", "{}"));
        } catch (Exception e) {
            s = new JSONObject();
        }
        boolean active = s.optBoolean("active", false);
        v.setOnClickPendingIntent(R.id.w_root, action(ctx, 1, "open", null));

        if (active) {
            long start = s.optLong("start", System.currentTimeMillis());
            long base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - start);
            v.setTextViewText(R.id.w_status, "● EN SERVICE");
            v.setTextColor(R.id.w_status, 0xFF16A34A);
            v.setTextViewText(R.id.w_client, s.optString("client", ""));
            v.setChronometer(R.id.w_chrono, base, null, true);
            v.setViewVisibility(R.id.w_chrono, View.VISIBLE);
            v.setTextViewText(R.id.w_sub, "depuis " + s.optString("startText", ""));
            v.setTextViewText(R.id.w_button, "OUT");
            v.setInt(R.id.w_button, "setBackgroundResource", R.drawable.w_btn_out);
            v.setOnClickPendingIntent(R.id.w_button, action(ctx, 2, "out", null));
            v.setViewVisibility(R.id.w_clients, View.GONE);
        } else {
            v.setTextViewText(R.id.w_status, "HORS SERVICE");
            v.setTextColor(R.id.w_status, 0xFF66706B);
            String def = s.optString("defaultName", "");
            v.setTextViewText(R.id.w_client, def.isEmpty() ? "Punch" : def);
            v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false);
            v.setViewVisibility(R.id.w_chrono, View.GONE);
            v.setTextViewText(R.id.w_sub, s.optString("todayText", "Touchez IN pour commencer"));
            v.setTextViewText(R.id.w_button, "IN");
            v.setInt(R.id.w_button, "setBackgroundResource", R.drawable.w_btn_in);
            v.setOnClickPendingIntent(R.id.w_button, action(ctx, 3, "in", null));
            JSONArray clients = s.optJSONArray("clients");
            int n = clients == null ? 0 : Math.min(clients.length(), CLIENT_IDS.length);
            for (int k = 0; k < CLIENT_IDS.length; k++) {
                if (k < n) {
                    JSONObject c = clients.optJSONObject(k);
                    v.setTextViewText(CLIENT_IDS[k], c.optString("name", ""));
                    v.setViewVisibility(CLIENT_IDS[k], View.VISIBLE);
                    v.setOnClickPendingIntent(CLIENT_IDS[k], action(ctx, 10 + k, "in", c.optString("id", null)));
                } else {
                    v.setViewVisibility(CLIENT_IDS[k], View.GONE);
                }
            }
            v.setViewVisibility(R.id.w_clients, n > 0 ? View.VISIBLE : View.GONE);
        }
        m.updateAppWidget(widgetId, v);
    }
}
