package ca.punch.heures;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** Fin du déplacement : la zone se recentre là où vous êtes (une seule position, économie d'énergie). */
public class TravelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        SharedPreferences p = PunchZone.prefs(c);
        if (!p.getBoolean("active", false) || p.getLong("travelUntil", 0) == 0) return;
        if (!PunchZone.hasLocation(c)) return;
        final PendingResult pending = goAsync();
        try {
            LocationServices.getFusedLocationProviderClient(c)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnCompleteListener(task -> {
                    try {
                        android.location.Location l = task.isSuccessful() ? task.getResult() : null;
                        if (l != null) {
                            p.edit().putString("lat", String.valueOf(l.getLatitude())).putString("lng", String.valueOf(l.getLongitude()))
                                .putLong("travelUntil", 0).putLong("recentered", System.currentTimeMillis()).apply();
                            PunchZone.addZone(c, l.getLatitude(), l.getLongitude(), p.getFloat("radius", 2000));
                        } else {
                            PunchZone.scheduleTravelAlarm(c, System.currentTimeMillis() + 15 * 60000L);   // réessayer dans 15 min
                        }
                    } finally {
                        pending.finish();
                    }
                });
        } catch (SecurityException e) {
            pending.finish();
        }
    }
}
