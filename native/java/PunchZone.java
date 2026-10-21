package ca.punch.heures;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Zones surveillées par Android (géorepérage) : très peu de batterie, pas de GPS continu.
 * - « zone » : sortie de la zone de 2 km autour du punch IN
 * - « client:<id> » : arrivée chez un client (si l'option est activée)
 * L'état est gardé dans SharedPreferences pour survivre à la fermeture de l'app et au redémarrage.
 */
public class PunchZone {
    static final String PREFS = "punch_zone";
    static final String ZONE_ID = "zone";
    static final String CH_ZONE = "punch_zone";
    static final String CH_SHIFT = "punch_shift";
    static final int NOTIF_SHIFT = 4001, NOTIF_LEFT = 4004;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static boolean hasLocation(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static PendingIntent geofenceIntent(Context c) {
        Intent i = new Intent(c, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    /** Ouvre l'app avec une action (même mécanisme que le widget). */
    static PendingIntent appAction(Context c, int requestCode, String op, String clientId) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(PunchWidget.ACTION);
        i.putExtra("op", op);
        if (clientId != null) i.putExtra("clientId", clientId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ZONE, "Zone et arrivée", NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel shift = new NotificationChannel(CH_SHIFT, "En service", NotificationManager.IMPORTANCE_LOW);
        shift.setShowBadge(false);
        nm.createNotificationChannel(shift);
    }

    static void notify(Context c, int id, NotificationCompat.Builder b) {
        channels(c);
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build()); } catch (Exception e) { /* notifications refusées */ }
    }

    static void cancel(Context c, int id) {
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
    }

    // ---------- Quart en cours ----------

    static void start(Context c, String client, long start, double lat, double lng, float radius, String mode) {
        prefs(c).edit()
            .putBoolean("active", true).putString("client", client).putLong("start", start)
            .putString("lat", String.valueOf(lat)).putString("lng", String.valueOf(lng))
            .putFloat("radius", radius).putString("mode", mode)
            .remove("leftTime").remove("leftLat").remove("leftLng").putLong("travelUntil", 0).apply();
        if (!"off".equals(mode)) addZone(c, lat, lng, radius); else removeZone(c);
        shiftNotification(c);
    }

    static void stop(Context c) {
        prefs(c).edit().putBoolean("active", false).remove("leftTime").putLong("travelUntil", 0).apply();
        removeZone(c);
        cancelTravelAlarm(c);
        cancel(c, NOTIF_SHIFT);
        cancel(c, NOTIF_LEFT);
    }

    static void shiftNotification(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("active", false)) return;
        long start = p.getLong("start", System.currentTimeMillis());
        java.text.DateFormat f = android.text.format.DateFormat.getTimeFormat(c);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_SHIFT)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setContentTitle("En service : " + p.getString("client", ""))
            .setContentText("Depuis " + f.format(new java.util.Date(start)))
            .setWhen(start).setUsesChronometer(true).setShowWhen(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(appAction(c, 40, "open", null))
            .addAction(0, "Puncher OUT", appAction(c, 41, "out", null));
        notify(c, NOTIF_SHIFT, b);
    }

    static void addZone(Context c, double lat, double lng, float radius) {
        if (!hasLocation(c)) return;
        Geofence g = new Geofence.Builder()
            .setRequestId(ZONE_ID)
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(120000)   // 2 min : économise la batterie
            .build();
        GeofencingRequest r = new GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(g).build();
        try {
            client(c).addGeofences(r, geofenceIntent(c))
                .addOnSuccessListener(v -> prefs(c).edit().putString("zoneError", "").apply())
                .addOnFailureListener(e -> prefs(c).edit().putString("zoneError", String.valueOf(e.getMessage())).apply());
        } catch (SecurityException e) {
            prefs(c).edit().putString("zoneError", "permission").apply();
        }
    }

    static void removeZone(Context c) {
        try { client(c).removeGeofences(Arrays.asList(ZONE_ID)); } catch (Exception e) { }
    }

    static GeofencingClient client(Context c) { return LocationServices.getGeofencingClient(c); }

    // ---------- Déplacement : zone en pause puis recentrée ----------

    static void travel(Context c, int minutes) {
        long until = System.currentTimeMillis() + minutes * 60000L;
        prefs(c).edit().putLong("travelUntil", until).remove("leftTime").apply();
        removeZone(c);
        cancel(c, NOTIF_LEFT);
        scheduleTravelAlarm(c, until);
    }

    static PendingIntent travelIntent(Context c) {
        return PendingIntent.getBroadcast(c, 1, new Intent(c, TravelReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleTravelAlarm(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, travelIntent(c));
    }

    static void cancelTravelAlarm(Context c) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(travelIntent(c));
    }

    // ---------- Arrivée chez les clients ----------

    static void setClients(Context c, String json, float radius, boolean enabled) {
        SharedPreferences p = prefs(c);
        String old = p.getString("clientIds", "");
        List<String> oldIds = new ArrayList<>();
        for (String s : old.split(",")) if (!s.isEmpty()) oldIds.add(s);
        if (!oldIds.isEmpty()) {
            try { client(c).removeGeofences(oldIds); } catch (Exception e) { }
        }
        p.edit().putString("clients", json).putFloat("clientRadius", radius).putBoolean("arrival", enabled).putString("clientIds", "").apply();
        if (!enabled || !hasLocation(c)) return;
        try {
            JSONArray arr = new JSONArray(json);
            GeofencingRequest.Builder rb = new GeofencingRequest.Builder().setInitialTrigger(0);
            StringBuilder ids = new StringBuilder();
            int n = 0;
            for (int k = 0; k < arr.length() && n < 90; k++) {
                JSONObject o = arr.getJSONObject(k);
                if (!o.has("lat") || o.isNull("lat")) continue;
                String id = "client:" + o.getString("id");
                rb.addGeofence(new Geofence.Builder().setRequestId(id)
                    .setCircularRegion(o.getDouble("lat"), o.getDouble("lng"), Math.max(150f, radius))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setNotificationResponsiveness(180000)
                    .build());
                ids.append(id).append(",");
                n++;
            }
            if (n == 0) return;
            p.edit().putString("clientIds", ids.toString()).apply();
            client(c).addGeofences(rb.build(), geofenceIntent(c));
        } catch (SecurityException e) {
            p.edit().putString("zoneError", "permission").apply();
        } catch (Exception e) { }
    }

    /** Après un redémarrage, Android oublie les zones : on les remet. */
    static void restore(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("active", false)) {
            long travelUntil = p.getLong("travelUntil", 0);
            if (travelUntil > 0) scheduleTravelAlarm(c, Math.max(travelUntil, System.currentTimeMillis() + 60000));
            else if (!"off".equals(p.getString("mode", "ask")) && p.getLong("leftTime", 0) == 0)
                addZone(c, Double.parseDouble(p.getString("lat", "0")), Double.parseDouble(p.getString("lng", "0")), p.getFloat("radius", 2000));
            shiftNotification(c);
        }
        setClients(c, p.getString("clients", "[]"), p.getFloat("clientRadius", 300), p.getBoolean("arrival", false));
    }
}
