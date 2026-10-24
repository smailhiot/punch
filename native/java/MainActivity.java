package ca.punch.heures;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PunchNativePlugin.class);
        super.onCreate(savedInstanceState);
        // Bouton retour : la page ferme d'abord la fiche ouverte (comme Annuler) ou la sélection,
        // revient à l'onglet Punch, et seulement ensuite l'app se ferme.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView wv = getBridge() != null ? getBridge().getWebView() : null;
                if (wv == null) { exit(); return; }
                wv.evaluateJavascript("(window.punchBack && window.punchBack()) ? 'true' : 'false'", value -> {
                    if (!"\"true\"".equals(value) && !"true".equals(value)) exit();
                });
            }
            private void exit() {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }
}
