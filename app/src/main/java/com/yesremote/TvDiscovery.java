package com.yesremote;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class TvDiscovery {
    private static final String TAG = "TvDiscovery";
    private static final String SERVICE_TYPE = "_androidtvremote2._tcp.";

    public interface Listener {
        void onDeviceFound(String name, String host, int port);
        void onDiscoveryFailed();
    }

    private final NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private final Listener listener;
    private final List<String> found = new ArrayList<>();

    public TvDiscovery(Context ctx, Listener listener) {
        this.nsdManager = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    public void start() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String s, int e) { listener.onDiscoveryFailed(); }
            @Override public void onStopDiscoveryFailed(String s, int e) {}
            @Override public void onDiscoveryStarted(String s) { Log.d(TAG, "Discovery started"); }
            @Override public void onDiscoveryStopped(String s) {}

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                Log.d(TAG, "Found: " + info.getServiceName());
                nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo i, int e) {}
                    @Override
                    public void onServiceResolved(NsdServiceInfo i) {
                        String host = i.getHost().getHostAddress();
                        String name = i.getServiceName();
                        int port = i.getPort();
                        if (!found.contains(host)) {
                            found.add(host);
                            listener.onDeviceFound(name, host, port);
                        }
                    }
                });
            }

            @Override public void onServiceLost(NsdServiceInfo info) {}
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            listener.onDiscoveryFailed();
        }
    }

    public void stop() {
        try { if (discoveryListener != null) nsdManager.stopServiceDiscovery(discoveryListener); }
        catch (Exception ignored) {}
    }
}
