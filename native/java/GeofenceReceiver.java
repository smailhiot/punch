package ca.punch.heures;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reçoit les sorties de zone et les arrivées chez un client, même app fermée. */
public class GeofenceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        GeofencingEvent ev = GeofencingEvent.fromIntent(intent);
        if (ev == null || ev.hasError() || ev.getTriggeringGeofences() == null) return;
        SharedPreferences p = PunchZone.prefs(c);
        Location loc = ev.getTriggeringLocation();
        for (Geofence g : ev.getTriggeringGeofences()) {
            String id = g.getRequestId();
            if (PunchZone.ZONE_ID.equals(id) && ev.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
                if (!p.getBoolean("active", false) || p.getLong("leftTime", 0) > 0 || p.getLong("travelUntil", 0) > 0) continue;
                long t = loc != null ? loc.getTime() : System.currentTimeMillis();
                SharedPreferences.Editor e = p.edit().putLong("leftTime", t);
                if (loc != null) e.putString("leftLat", String.valueOf(loc.getLatitude())).putString("leftLng", String.valueOf(loc.getLongitude()));
                e.apply();
                PunchZone.removeZone(c);
                leftNotification(c, p, t);
            } else if (id.startsWith("client:") && ev.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
                if (p.getBoolean("active", false)) continue;
                String cid = id.substring(7);
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
                if (today.equals(p.getString("notified:" + cid, ""))) continue;
                p.edit().putString("notified:" + cid, today).apply();
                String name = nameOf(p, cid);
                NotificationCompat.Builder b = new NotificationCompat.Builder(c, PunchZone.CH_ZONE)
                    .setSmallIcon(R.drawable.ic_stat_punch)
                    .setContentTitle("Vous êtes chez " + name)
                    .setContentText("Puncher IN maintenant ?")
                    .setAutoCancel(true)
                    .setContentIntent(PunchZone.appAction(c, 50, "open", null))
                    .addAction(0, "Puncher IN", PunchZone.appAction(c, 51 + Math.abs(cid.hashCode() % 1000), "in", cid));
                PunchZone.notify(c, 4100 + Math.abs(cid.hashCode() % 800), b);
            }
        }
    }

    static void leftNotification(Context c, SharedPreferences p, long t) {
        String time = android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date(t));
        String client = p.getString("client", "");
        boolean auto = "auto".equals(p.getString("mode", "ask"));
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, PunchZone.CH_ZONE)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setAutoCancel(true)
            .setContentIntent(PunchZone.appAction(c, 60, "left", null));
        if (auto) {
            b.setContentTitle("Punch OUT : " + client)
             .setContentText("Vous avez quitté la zone vers " + time + ". Touchez pour confirmer.");
        } else {
            b.setContentTitle("Vous avez quitté la zone")
             .setContentText(client + " · vers " + time + ". Punch OUT, changer de client ou déplacement ?")
             .setStyle(new NotificationCompat.BigTextStyle().bigText(client + " · vers " + time + ".\nPunch OUT, changer de client ou déplacement ?"))
             .addAction(0, "Puncher OUT", PunchZone.appAction(c, 61, "leftOut", null))
             .addAction(0, "Changer de client", PunchZone.appAction(c, 62, "leftSwitch", null))
             .addAction(0, "Déplacement 1 h", PunchZone.appAction(c, 63, "leftTravel", null));
        }
        PunchZone.notify(c, PunchZone.NOTIF_LEFT, b);
    }

    static String nameOf(SharedPreferences p, String id) {
        try {
            JSONArray arr = new JSONArray(p.getString("clients", "[]"));
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                if (id.equals(o.optString("id"))) return o.optString("name", "un client");
            }
        } catch (Exception e) { }
        return "un client";
    }
}
