package com.yesremote;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
public class RemoteWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context ctx,AppWidgetManager mgr,int[] ids){for(int id:ids)update(ctx,mgr,id);}
    static void update(Context ctx,AppWidgetManager mgr,int id){
        RemoteViews v=new RemoteViews(ctx.getPackageName(),R.layout.widget_remote);
        int[] wids={R.id.w0,R.id.w1,R.id.w2,R.id.w3,R.id.w4,R.id.w5,R.id.w6,R.id.w7,R.id.w8,R.id.w9};
        int[] kcs={TvClient.KEY_0,TvClient.KEY_1,TvClient.KEY_2,TvClient.KEY_3,TvClient.KEY_4,
                   TvClient.KEY_5,TvClient.KEY_6,TvClient.KEY_7,TvClient.KEY_8,TvClient.KEY_9};
        for(int i=0;i<wids.length;i++)v.setOnClickPendingIntent(wids[i],pi(ctx,kcs[i],id*20+i));
        v.setOnClickPendingIntent(R.id.w_ch_up,pi(ctx,TvClient.KEY_CH_UP,id*20+10));
        v.setOnClickPendingIntent(R.id.w_ch_down,pi(ctx,TvClient.KEY_CH_DOWN,id*20+11));
        v.setOnClickPendingIntent(R.id.w_vol_up,pi(ctx,TvClient.KEY_VOL_UP,id*20+12));
        v.setOnClickPendingIntent(R.id.w_vol_down,pi(ctx,TvClient.KEY_VOL_DOWN,id*20+13));
        v.setOnClickPendingIntent(R.id.w_ok,pi(ctx,TvClient.KEY_OK,id*20+14));
        v.setOnClickPendingIntent(R.id.w_back,pi(ctx,TvClient.KEY_BACK,id*20+15));
        mgr.updateAppWidget(id,v);
    }
    private static PendingIntent pi(Context ctx,int kc,int req){
        Intent i=new Intent(ctx,RemoteService.class);i.setAction(RemoteService.ACTION);i.putExtra(RemoteService.EXTRA,kc);
        int f=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        return PendingIntent.getService(ctx,req,i,f);
    }
}
