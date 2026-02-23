package com.yesremote;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class RemoteService extends Service {
    public static final String ACTION="com.yesremote.SEND_KEY";
    public static final String EXTRA="kc";
    private TvClient client;
    @Override public void onCreate(){super.onCreate();client=new TvClient(this);String ip=client.getSavedIp();if(!ip.isEmpty())client.connect(ip);}
    @Override public int onStartCommand(Intent i,int f,int s){
        if(i!=null&&ACTION.equals(i.getAction())){int kc=i.getIntExtra(EXTRA,-1);if(kc>=0){if(!client.isConnected()){String ip=client.getSavedIp();if(!ip.isEmpty())client.connect(ip);}client.sendKey(kc);}}
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){super.onDestroy();client.disconnect();}
}
