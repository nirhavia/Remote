package com.yesremote;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.github.cybernhl.androidtvremote2.AndroidTvRemote;
import com.github.cybernhl.androidtvremote2.AndroidTvRemoteOptions;
import com.github.cybernhl.androidtvremote2.PairingManager;

public class MainActivity extends AppCompatActivity {

    private AndroidTvRemote remote;
    private TvDiscovery discovery;
    private TextView tvStatus;
    private EditText etIp;
    private String currentIp = "";

    private static final String PREFS = "tvprefs";

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        etIp     = findViewById(R.id.etIp);

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("ip", "");
        if (!saved.isEmpty()) { etIp.setText(saved); currentIp = saved; }

        startDiscovery();
        setupButtons();

        if (!saved.isEmpty()) connectTo(saved);
    }

    private void startDiscovery() {
        discovery = new TvDiscovery(this, new TvDiscovery.Listener() {
            public void onDeviceFound(String name, String host, int port) {
                runOnUiThread(() -> {
                    if (etIp.getText().toString().isEmpty()) {
                        etIp.setText(host);
                        currentIp = host;
                        setStatus("נמצא: " + name, 0xFF2196F3);
                    }
                });
            }
            public void onDiscoveryFailed() {}
        });
        discovery.start();
    }

    private void connectTo(String ip) {
        currentIp = ip;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("ip", ip).apply();
        setStatus("מתחבר...", 0xFF8892A4);

        AndroidTvRemoteOptions opts = new AndroidTvRemoteOptions.Builder()
            .setClientName("YES Remote")
            .setClientCertificate(getSharedPreferences(PREFS, MODE_PRIVATE).getString("cert_" + ip, null))
            .build();

        remote = new AndroidTvRemote(this, ip, opts);

        remote.setConnectionListener(new AndroidTvRemote.ConnectionListener() {
            public void onConnected() {
                runOnUiThread(() -> setStatus("מחובר ✅", 0xFF4CAF50));
            }
            public void onConnectionError(Throwable t) {
                runOnUiThread(() -> setStatus("שגיאה: " + t.getMessage(), 0xFFFF9800));
            }
            public void onDisconnected() {
                runOnUiThread(() -> setStatus("מנותק", 0xFFE94560));
            }
        });

        remote.setPairingListener(new AndroidTvRemote.PairingListener() {
            public void onPairingRequired() {
                runOnUiThread(() -> {
                    setStatus("הסתכל על הטלוויזיה לקוד PIN", 0xFFFF9800);
                    showPinDialog();
                });
            }
            public void onPairingSuccess(String certPem) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("cert_" + ip, certPem).apply();
                runOnUiThread(() -> setStatus("מחובר ✅", 0xFF4CAF50));
            }
            public void onPairingFailed(Throwable t) {
                runOnUiThread(() -> setStatus("PIN שגוי, נסה שוב", 0xFFE94560));
            }
        });

        remote.connect();
    }

    private void showPinDialog() {
        EditText pinInput = new EditText(this);
        pinInput.setHint("קוד מהטלוויזיה");
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        pinInput.setTextColor(0xFF000000);
        new AlertDialog.Builder(this)
            .setTitle("קוד אישור")
            .setMessage("הכנס את הקוד שמוצג על מסך הטלוויזיה:")
            .setView(pinInput)
            .setPositiveButton("אשר", (d, w) -> {
                String pin = pinInput.getText().toString().trim();
                if (!pin.isEmpty() && remote != null) remote.sendPairingSecret(pin);
            })
            .setCancelable(false)
            .show();
    }

    private void sendKey(int keycode) {
        if (remote != null) remote.sendKeyCode(keycode);
    }

    private void setStatus(String text, int color) {
        tvStatus.setText(text);
        tvStatus.setTextColor(color);
    }

    private void setupButtons() {
        findViewById(R.id.btnConnect).setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (!ip.isEmpty()) connectTo(ip);
        });

        int[] ids = {R.id.btn0,R.id.btn1,R.id.btn2,R.id.btn3,R.id.btn4,
                     R.id.btn5,R.id.btn6,R.id.btn7,R.id.btn8,R.id.btn9};
        int[] kcs  = {7,8,9,10,11,12,13,14,15,16};
        for (int i=0;i<ids.length;i++){
            final int kc=kcs[i];
            Button b=findViewById(ids[i]);
            if(b!=null) b.setOnClickListener(v->sendKey(kc));
        }
        bind(R.id.btnUp,19);  bind(R.id.btnDown,20);
        bind(R.id.btnLeft,21); bind(R.id.btnRight,22);
        bind(R.id.btnOk,23);   bind(R.id.btnBack,4);
        bind(R.id.btnHome,3);  bind(R.id.btnMenu,82);
        bind(R.id.btnVolUp,24); bind(R.id.btnVolDown,25);
        bind(R.id.btnMute,164); bind(R.id.btnChUp,166);
        bind(R.id.btnChDown,167); bind(R.id.btnPower,26);
    }

    private void bind(int id, int kc) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> sendKey(kc));
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (remote != null) remote.disconnect();
        if (discovery != null) discovery.stop();
    }
}
