package com.yesremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * מקבל broadcasts מהווידג'ט ושולח פקודה ל-RemoteService.
 * BroadcastReceiver יכול לקרוא startService (לא FGS) - בטוח ב-Android 14.
 */
public class KeyReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.yesremote.ACTION_KEY";
    public static final String EXTRA_KEYCODE = "keycode";
    private static final String TAG = "KeyReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        int kc = intent.getIntExtra(EXTRA_KEYCODE, -1);
        if (kc < 0) return;
        Log.d(TAG, "Received key: " + kc);
        // שלח ל-Service (startService - לא FGS - בטוח מכל context)
        Intent si = new Intent(ctx, RemoteService.class);
        si.setAction(RemoteService.ACTION);
        si.putExtra(RemoteService.EXTRA, kc);
        ctx.startService(si);  // ← לא startForegroundService!
    }
}
