package com.yesremote;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.yesremote.remote.AndroidTvRemoteClient;

public class MainActivity extends AppCompatActivity {

    private AndroidTvRemoteClient client;
    private TextView tvStatus;
    private EditText etIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new AndroidTvRemoteClient(this);
        tvStatus = findViewById(R.id.tvStatus);
        etIp = findViewById(R.id.etIp);

        String saved = client.getSavedIp();
        if (!saved.isEmpty()) etIp.setText(saved);

        client.setListener(new AndroidTvRemoteClient.Listener() {
            public void onConnected() {
                runOnUiThread(() -> { tvStatus.setText("✅ מחובר"); tvStatus.setTextColor(0xFF4CAF50); });
            }
            public void onDisconnected() {
                runOnUiThread(() -> { tvStatus.setText("❌ מנותק"); tvStatus.setTextColor(0xFFE94560); });
            }
            public void onError(String msg) {
                runOnUiThread(() -> {
                    tvStatus.setText("⚠️ " + msg);
                    tvStatus.setTextColor(0xFFFF9800);
                });
            }
        });

        setupButtons();
        if (!saved.isEmpty()) client.connect(saved);
    }

    private void setupButtons() {
        findViewById(R.id.btnConnect).setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) { Toast.makeText(this, "הכנס IP", Toast.LENGTH_SHORT).show(); return; }
            client.saveIp(ip);
            tvStatus.setText("🔄 מתחבר...");
            tvStatus.setTextColor(0xFF8892A4);
            client.connect(ip);
        });

        int[] ids = {R.id.btn0,R.id.btn1,R.id.btn2,R.id.btn3,R.id.btn4,R.id.btn5,R.id.btn6,R.id.btn7,R.id.btn8,R.id.btn9};
        for (int i = 0; i < ids.length; i++) {
            final int d = i;
            Button b = findViewById(ids[i]);
            if (b != null) b.setOnClickListener(v -> client.sendKey(AndroidTvRemoteClient.digitKeycode(d)));
        }

        bind(R.id.btnUp,      AndroidTvRemoteClient.KEYCODE_DPAD_UP);
        bind(R.id.btnDown,    AndroidTvRemoteClient.KEYCODE_DPAD_DOWN);
        bind(R.id.btnLeft,    AndroidTvRemoteClient.KEYCODE_DPAD_LEFT);
        bind(R.id.btnRight,   AndroidTvRemoteClient.KEYCODE_DPAD_RIGHT);
        bind(R.id.btnOk,      AndroidTvRemoteClient.KEYCODE_DPAD_CENTER);
        bind(R.id.btnBack,    AndroidTvRemoteClient.KEYCODE_BACK);
        bind(R.id.btnHome,    AndroidTvRemoteClient.KEYCODE_HOME);
        bind(R.id.btnMenu,    AndroidTvRemoteClient.KEYCODE_MENU);
        bind(R.id.btnVolUp,   AndroidTvRemoteClient.KEYCODE_VOLUME_UP);
        bind(R.id.btnVolDown, AndroidTvRemoteClient.KEYCODE_VOLUME_DOWN);
        bind(R.id.btnMute,    AndroidTvRemoteClient.KEYCODE_VOLUME_MUTE);
        bind(R.id.btnChUp,    AndroidTvRemoteClient.KEYCODE_CHANNEL_UP);
        bind(R.id.btnChDown,  AndroidTvRemoteClient.KEYCODE_CHANNEL_DOWN);
        bind(R.id.btnPower,   AndroidTvRemoteClient.KEYCODE_POWER);
    }

    private void bind(int id, int keycode) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(view -> client.sendKey(keycode));
    }

    @Override protected void onDestroy() { super.onDestroy(); client.disconnect(); }
}
