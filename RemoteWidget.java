package com.yesremote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import com.yesremote.remote.AndroidTvRemoteClient;

public class RemoteWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_remote);

        int[] wIds = {R.id.w0,R.id.w1,R.id.w2,R.id.w3,R.id.w4,R.id.w5,R.id.w6,R.id.w7,R.id.w8,R.id.w9};
        int[] kcs  = {AndroidTvRemoteClient.KEYCODE_0,AndroidTvRemoteClient.KEYCODE_1,AndroidTvRemoteClient.KEYCODE_2,
                      AndroidTvRemoteClient.KEYCODE_3,AndroidTvRemoteClient.KEYCODE_4,AndroidTvRemoteClient.KEYCODE_5,
                      AndroidTvRemoteClient.KEYCODE_6,AndroidTvRemoteClient.KEYCODE_7,AndroidTvRemoteClient.KEYCODE_8,
                      AndroidTvRemoteClient.KEYCODE_9};
        for (int i = 0; i < wIds.length; i++)
            v.setOnClickPendingIntent(wIds[i], pi(ctx, kcs[i], id * 20 + i));

        v.setOnClickPendingIntent(R.id.w_ch_up,   pi(ctx, AndroidTvRemoteClient.KEYCODE_CHANNEL_UP,   id*20+10));
        v.setOnClickPendingIntent(R.id.w_ch_down, pi(ctx, AndroidTvRemoteClient.KEYCODE_CHANNEL_DOWN, id*20+11));
        v.setOnClickPendingIntent(R.id.w_vol_up,  pi(ctx, AndroidTvRemoteClient.KEYCODE_VOLUME_UP,    id*20+12));
        v.setOnClickPendingIntent(R.id.w_vol_down,pi(ctx, AndroidTvRemoteClient.KEYCODE_VOLUME_DOWN,  id*20+13));
        v.setOnClickPendingIntent(R.id.w_ok,      pi(ctx, AndroidTvRemoteClient.KEYCODE_DPAD_CENTER,  id*20+14));
        v.setOnClickPendingIntent(R.id.w_back,    pi(ctx, AndroidTvRemoteClient.KEYCODE_BACK,         id*20+15));

        mgr.updateAppWidget(id, v);
    }

    private static PendingIntent pi(Context ctx, int kc, int req) {
        Intent i = new Intent(ctx, RemoteService.class);
        i.setAction(RemoteService.ACTION_KEY);
        i.putExtra(RemoteService.EXTRA_KEY, kc);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(ctx, req, i, f);
    }
}
