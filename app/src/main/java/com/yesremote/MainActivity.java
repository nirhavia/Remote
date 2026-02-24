package com.yesremote;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
public class MainActivity extends AppCompatActivity {
    private TvClient client;
    private TextView tvStatus;
    private EditText etIp;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        client = new TvClient(this);
        tvStatus = findViewById(R.id.tvStatus);
        etIp = findViewById(R.id.etIp);
        String saved = client.getSavedIp();
        if (!saved.isEmpty()) etIp.setText(saved);

        client.setListener(new TvClient.Listener() {
            public void onPairingStarted(String msg) {
                runOnUiThread(() -> {
                    tvStatus.setText("בצמד: הסתכל על מסך הטלוויזיה לקוד PIN");
                    tvStatus.setTextColor(0xFFFF9800);
                    Toast.makeText(MainActivity.this, "אשר את החיבור על מסך הטלוויזיה", Toast.LENGTH_LONG).show();
                });
            }
            public void onConnected() {
                runOnUiThread(() -> { tvStatus.setText("מחובר"); tvStatus.setTextColor(0xFF4CAF50); });
            }
            public void onDisconnected() {
                runOnUiThread(() -> { tvStatus.setText("מנותק"); tvStatus.setTextColor(0xFFE94560); });
            }
            public void onError(String m) {
                runOnUiThread(() -> { tvStatus.setText("שגיאה: " + m); tvStatus.setTextColor(0xFFFF9800); });
            }
        });

        setup();
        if (!saved.isEmpty()) client.connect(saved);
    }

    private void setup() {
        findViewById(R.id.btnConnect).setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) { Toast.makeText(this,"הכנס IP",Toast.LENGTH_SHORT).show(); return; }
            client.saveIp(ip);
            tvStatus.setText("מתחבר...");
            tvStatus.setTextColor(0xFF8892A4);
            client.connect(ip);
        });
        int[] ids = {R.id.btn0,R.id.btn1,R.id.btn2,R.id.btn3,R.id.btn4,R.id.btn5,R.id.btn6,R.id.btn7,R.id.btn8,R.id.btn9};
        for (int i = 0; i < ids.length; i++) {
            final int d = i;
            Button b = findViewById(ids[i]);
            if (b != null) b.setOnClickListener(v -> client.sendKey(TvClient.digit(d)));
        }
        bind(R.id.btnUp,TvClient.KEY_UP); bind(R.id.btnDown,TvClient.KEY_DOWN);
        bind(R.id.btnLeft,TvClient.KEY_LEFT); bind(R.id.btnRight,TvClient.KEY_RIGHT);
        bind(R.id.btnOk,TvClient.KEY_OK); bind(R.id.btnBack,TvClient.KEY_BACK);
        bind(R.id.btnHome,TvClient.KEY_HOME); bind(R.id.btnMenu,TvClient.KEY_MENU);
        bind(R.id.btnVolUp,TvClient.KEY_VOL_UP); bind(R.id.btnVolDown,TvClient.KEY_VOL_DOWN);
        bind(R.id.btnMute,TvClient.KEY_MUTE); bind(R.id.btnChUp,TvClient.KEY_CH_UP);
        bind(R.id.btnChDown,TvClient.KEY_CH_DOWN); bind(R.id.btnPower,TvClient.KEY_POWER);
    }

    private void bind(int id, int kc) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> client.sendKey(kc));
    }

    @Override protected void onDestroy() { super.onDestroy(); client.disconnect(); }
}
