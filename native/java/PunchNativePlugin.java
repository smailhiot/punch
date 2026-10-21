package ca.punch.heures;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

/** Pont entre l'app web et Android : autorisations, batterie, widget. */
@CapacitorPlugin(
    name = "PunchNative",
    permissions = {
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }),
        @Permission(alias = "background", strings = { Manifest.permission.ACCESS_BACKGROUND_LOCATION })
    }
)
public class PunchNativePlugin extends Plugin {
    static final String PREFS = "punch_widget";
    private JSObject pending = null;

    @Override
    public void load() {
        if (getActivity() != null) captureIntent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (captureIntent(intent)) {
            JSObject a = pending;
            pending = null;
            notifyListeners("widgetAction", a);
        }
    }

    /** Lit l'action d'un toucher sur le widget, s'il y en a une. */
    private boolean captureIntent(Intent intent) {
        if (intent == null || !PunchWidget.ACTION.equals(intent.getAction())) return false;
        JSObject a = new JSObject();
        a.put("op", intent.getStringExtra("op"));
        String clientId = intent.getStringExtra("clientId");
        if (clientId != null) a.put("clientId", clientId);
        pending = a;
        intent.setAction(Intent.ACTION_MAIN); // ne pas rejouer l'action si l'écran est recréé
        return true;
    }

    @PluginMethod
    public void getPendingAction(PluginCall call) {
        JSObject r = new JSObject();
        if (pending != null) r.put("action", pending);
        pending = null;
        call.resolve(r);
    }

    @PluginMethod
    public void batteryStatus(PluginCall call) {
        JSObject r = new JSObject();
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        r.put("unrestricted", pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        call.resolve(r);
    }

    @PluginMethod
    public void requestBattery(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                getActivity().startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                call.resolve();
            } catch (Exception e2) {
                call.reject(e2.getMessage());
            }
        }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void updateWidget(PluginCall call) {
        String data = call.getString("data", "{}");
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", data).apply();
        PunchWidget.refreshAll(getContext());
        call.resolve();
    }

    // ---------- Zone (géorepérage Android, économe en batterie) ----------

    @PluginMethod
    public void startZone(PluginCall call) {
        PunchZone.start(getContext(), call.getString("client", ""), call.getLong("start", System.currentTimeMillis()),
            call.getDouble("lat", 0.0), call.getDouble("lng", 0.0), call.getFloat("radius", 2000f), call.getString("mode", "ask"));
        call.resolve();
    }

    @PluginMethod
    public void stopZone(PluginCall call) {
        PunchZone.stop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void travel(PluginCall call) {
        PunchZone.travel(getContext(), call.getInt("minutes", 60));
        call.resolve();
    }

    @PluginMethod
    public void getZoneState(PluginCall call) {
        android.content.SharedPreferences p = PunchZone.prefs(getContext());
        JSObject r = new JSObject();
        r.put("active", p.getBoolean("active", false));
        r.put("leftTime", p.getLong("leftTime", 0));
        r.put("travelUntil", p.getLong("travelUntil", 0));
        r.put("recentered", p.getLong("recentered", 0));
        r.put("lat", p.getString("lat", null));
        r.put("lng", p.getString("lng", null));
        r.put("error", p.getString("zoneError", ""));
        call.resolve(r);
    }

    @PluginMethod
    public void clearLeft(PluginCall call) {
        PunchZone.prefs(getContext()).edit().remove("leftTime").remove("recentered").apply();
        PunchZone.cancel(getContext(), PunchZone.NOTIF_LEFT);
        call.resolve();
    }

    @PluginMethod
    public void setClients(PluginCall call) {
        PunchZone.setClients(getContext(), call.getString("clients", "[]"), call.getFloat("radius", 300f), call.getBoolean("enabled", false));
        call.resolve();
    }

    @PluginMethod
    public void pinWidget(PluginCall call) {
        JSObject r = new JSObject();
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 26) {
            AppWidgetManager m = AppWidgetManager.getInstance(getContext());
            if (m.isRequestPinAppWidgetSupported()) {
                ok = m.requestPinAppWidget(new ComponentName(getContext(), PunchWidget.class), null, null);
            }
        }
        r.put("ok", ok);
        call.resolve(r);
    }
}
