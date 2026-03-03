package com.yesremote;
import android.os.IBinder;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Intent;

import android.app.AlertDialog;
import android.text.InputType;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TvClient client;
    private RemoteService remoteService;
    private boolean serviceBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            RemoteService.LocalBinder lb = (RemoteService.LocalBinder) binder;
            remoteService = lb.getService();
            serviceBound = true;
            // עדכן client reference
            client = remoteService.getClient();
        }
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private TvDiscovery discovery;
    private TvPairing pairing;
    private TextView tvStatus;
    private EditText etIp;
    private String currentIp = "";
    private final List<String[]> foundDevices = new ArrayList<>();

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        client = new TvClient(this);
        tvStatus = findViewById(R.id.tvStatus);
        etIp = findViewById(R.id.etIp);
        String saved = client.getSavedIp();
        if (!saved.isEmpty()) { etIp.setText(saved); currentIp = saved; }
        client.setListener(new TvClient.Listener() {
            public void onConnected()    { runOnUiThread(()->setStatus("מחובר ✅",0xFF4CAF50)); }
            public void onDisconnected() { runOnUiThread(()->setStatus("מנותק",0xFFE94560)); }
            public void onError(String m){ runOnUiThread(()->setStatus("שגיאה: "+m,0xFFFF9800)); }
        });
        startDiscovery();
        if (!saved.isEmpty() && client.isPaired(saved)) {
            setStatus("מתחבר...",0xFF8892A4); // הפעל service עם IP חדש
                Intent si = new Intent(this, RemoteService.class);
                si.putExtra("ip", saved);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(si);
                } else {
                    startService(si);
                }
                if (remoteService != null) client = remoteService.getClient();
        }
        setupButtons();
    }

    private void startDiscovery() {
        discovery = new TvDiscovery(this, new TvDiscovery.Listener() {
            public void onDeviceFound(String name, String host, int port) {
                runOnUiThread(() -> {
                    for (String[] d : foundDevices) if (d[1].equals(host)) return;
                    foundDevices.add(new String[]{name, host});
                    if (etIp.getText().toString().isEmpty()) { etIp.setText(host); currentIp=host; }
                    if (foundDevices.size() > 1) findViewById(R.id.btnChooseDevice).setVisibility(View.VISIBLE);
                    setStatus("נמצא: "+name, 0xFF2196F3);
                });
            }
            public void onDiscoveryFailed() {}
        });
        discovery.start();
    }

    private void showDevicePicker() {
        String[] names = new String[foundDevices.size()];
        for (int i=0;i<foundDevices.size();i++)
            names[i] = foundDevices.get(i)[0] + "  (" + foundDevices.get(i)[1] + ")";
        new AlertDialog.Builder(this).setTitle("בחר מכשיר")
            .setItems(names,(d,w)->{
                String host=foundDevices.get(w)[1];
                etIp.setText(host); currentIp=host;
                setStatus("נבחר: "+foundDevices.get(w)[0],0xFF2196F3);
            }).show();
    }

    private void doConnect() {
        String ip = etIp.getText().toString().trim();
        if (ip.isEmpty()) { Toast.makeText(this,"הכנס IP",Toast.LENGTH_SHORT).show(); return; }
        currentIp=ip; client.saveIp(ip);
        if (client.isPaired(ip)) { setStatus("מתחבר...",0xFF8892A4); client.connect(ip); return; }
        setStatus("מתחיל pairing...",0xFFFF9800);
        pairing = new TvPairing(ip, new TvPairing.Callback() {
            public void onShowPin() { runOnUiThread(()->{ setStatus("הסתכל על הטלוויזיה לקוד",0xFFFF9800); showPinDialog(); }); }
            public void onPaired(byte[] key, byte[] cert) {
                client.savePairing(currentIp,key,cert);
                runOnUiThread(()->{ setStatus("מחובר ✅",0xFF4CAF50); client.connect(currentIp); });
            }
            public void onError(String m) { runOnUiThread(()->setStatus("שגיאה: "+m,0xFFFF9800)); }
        });
        pairing.start();
    }

    private void doReset() {
        new AlertDialog.Builder(this).setTitle("איפוס חיבור")
            .setMessage("למחוק את נתוני ה-pairing ולהתחיל מחדש?")
            .setPositiveButton("כן",(d,w)->{
                if (!currentIp.isEmpty()) client.clearPairing(currentIp);
                setStatus("נתוני חיבור נמחקו - לחץ חבר",0xFF8892A4);
            })
            .setNegativeButton("ביטול",null).show();
    }

    private void showPinDialog() {
        EditText pin = new EditText(this);
        pin.setHint("קוד מהטלוויזיה");
        pin.setInputType(android.text.InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        pin.setTextColor(0xFF000000);
        new AlertDialog.Builder(this).setTitle("קוד אישור")
            .setMessage("הכנס את הקוד מהטלוויזיה:").setView(pin)
            .setPositiveButton("אשר",(d,w)->{String p=pin.getText().toString().trim();if(!p.isEmpty()&&pairing!=null)pairing.sendPin(p);})
            .setCancelable(false).show();
    }

    private void setStatus(String t,int c){tvStatus.setText(t);tvStatus.setTextColor(c);}

    private void setupButtons(){
        findViewById(R.id.btnConnect).setOnClickListener(v->doConnect());
        findViewById(R.id.btnReset).setOnClickListener(v->doReset());
        findViewById(R.id.btnChooseDevice).setOnClickListener(v->showDevicePicker());
        int[] ids={R.id.btn0,R.id.btn1,R.id.btn2,R.id.btn3,R.id.btn4,
                   R.id.btn5,R.id.btn6,R.id.btn7,R.id.btn8,R.id.btn9};
        for(int i=0;i<ids.length;i++){final int d=i;Button b=findViewById(ids[i]);
            if(b!=null)b.setOnClickListener(v->client.sendKey(TvClient.digit(d)));}
        bind(R.id.btnUp,TvClient.KEY_UP);bind(R.id.btnDown,TvClient.KEY_DOWN);
        bind(R.id.btnLeft,TvClient.KEY_LEFT);bind(R.id.btnRight,TvClient.KEY_RIGHT);
        bind(R.id.btnOk,TvClient.KEY_OK);bind(R.id.btnBack,TvClient.KEY_BACK);
        bind(R.id.btnHome,TvClient.KEY_HOME);bind(R.id.btnMenu,TvClient.KEY_MENU);
        bind(R.id.btnVolUp,TvClient.KEY_VOL_UP);bind(R.id.btnVolDown,TvClient.KEY_VOL_DOWN);
        bind(R.id.btnMute,TvClient.KEY_MUTE);bind(R.id.btnChUp,TvClient.KEY_CH_UP);
        bind(R.id.btnChDown,TvClient.KEY_CH_DOWN);bind(R.id.btnPower,TvClient.KEY_POWER);
    }
    private void bind(int id,int kc){View v=findViewById(id);if(v!=null)v.setOnClickListener(x->client.sendKey(kc));}
    @Override protected void onDestroy(){super.onDestroy();client.disconnect();if(discovery!=null)discovery.stop();}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) unbindService(serviceConnection);
    }

}