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
            public void onStartDiscoveryFailed(String s, int e) { listener.onDiscoveryFailed(); }
            public void onStopDiscoveryFailed(String s, int e) {}
            public void onDiscoveryStarted(String s) {}
            public void onDiscoveryStopped(String s) {}
            public void onServiceFound(NsdServiceInfo info) {
                nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo i, int e) {}
                    public void onServiceResolved(NsdServiceInfo i) {
                        String host = i.getHost().getHostAddress();
                        if (!found.contains(host)) {
                            found.add(host);
                            listener.onDeviceFound(i.getServiceName(), host, i.getPort());
                        }
                    }
                });
            }
            public void onServiceLost(NsdServiceInfo info) {}
        };
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) { listener.onDiscoveryFailed(); }
    }

    public void stop() {
        try { if (discoveryListener != null) nsdManager.stopServiceDiscovery(discoveryListener); }
        catch (Exception ignored) {}
    }
}
