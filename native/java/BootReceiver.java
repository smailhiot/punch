package ca.punch.heures;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Remet les zones et la notification « En service » après un redémarrage du téléphone. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) PunchZone.restore(c);
    }
}
