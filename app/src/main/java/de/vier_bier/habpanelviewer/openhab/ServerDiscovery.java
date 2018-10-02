package de.vier_bier.habpanelviewer.openhab;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * mDNS discovery for openHAB.
 */
public class ServerDiscovery {
    private static final String TAG = "HPV-ServerDiscovery";

    private final NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private final HashSet<String> mUrls = new HashSet<>();
    private DiscoveryListener mListener;

    public ServerDiscovery(NsdManager nsdManager) {
        mNsdManager = nsdManager;
    }

    public synchronized void discover(final DiscoveryListener l) {
        if (mDiscoveryListener != null) {
            return;
        }

        mListener = l;
        Log.v(TAG, "starting discovery...");
        mUrls.clear();

        ArrayList<String> types = new ArrayList<>();
        types.add("_openhab-server._tcp");
        types.add("_openhab-server-ssl._tcp");

        try {
            for (String serviceType : types) {
                try {
                    Log.v(TAG, "starting discovery for " + serviceType + "...");
                    mDiscoveryListener = new NsdDiscoveryListener();
                    mNsdManager.discoverServices(
                            serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

                    Log.v(TAG, "waiting for results...");
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for discovery", e);
                } finally {
                    Log.v(TAG, "stopping discovery for " + serviceType + "...");
                    stopDiscovery();
                }
            }
        } finally {
            stopDiscovery();
        }

        mListener = null;
        Log.v(TAG, "discovery finished.");
    }

    public void terminate() {
        stopDiscovery();
    }

    private synchronized void stopDiscovery() {
        if (mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            mDiscoveryListener = null;
        }
    }

    private class ResolveListener implements NsdManager.ResolveListener {
        CountDownLatch mLatch;

        ResolveListener(CountDownLatch finishLatch) {
            mLatch = finishLatch;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.v(TAG, "service resolve failed: name= " + serviceInfo.getServiceName() + " " + errorCode);
            mLatch.countDown();
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo serviceInfo) {
            final int port = serviceInfo.getPort();
            final String host = serviceInfo.getHost().getHostName();

            Log.v(TAG, "service resolved: name= " + serviceInfo.getServiceName()
                    + ", host=" + host + ", port=" + port);

            synchronized (mUrls) {
                if (serviceInfo.getServiceName().contains("openhab-ssl")) {
                    if (mUrls.add("https://" + host + ":" + port)) {
                        mListener.found("https://" + host + ":" + port);
                    }
                } else {
                    if (mUrls.add("http://" + host + ":" + port)) {
                        mListener.found("http://" + host + ":" + port);
                    }
                }
            }

            mLatch.countDown();
        }
    }

    private class NsdDiscoveryListener implements NsdManager.DiscoveryListener {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.v(TAG, "discovery started");
        }

        @Override
        public synchronized void onServiceFound(final NsdServiceInfo service) {
            executor.submit(() -> {
                Log.v(TAG, "starting to resolve service " + service.getServiceName()
                        + service.getHost() + ":" + service.getPort() + "...");

                CountDownLatch finishLatch = new CountDownLatch(1);
                mNsdManager.resolveService(service, new ResolveListener(finishLatch));

                try {
                    finishLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                Log.v(TAG, "fisnihed resolving service " + service.getServiceName()
                        + service.getHost() + ":" + service.getPort() + "...");
            });
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            Log.v(TAG, "service lost: name= " + service.getServiceName());
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.v(TAG, "discovery stopped");
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.v(TAG, "discovery start failed: " + errorCode);
            mDiscoveryListener = null;
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.v(TAG, "discovery stop failed: " + errorCode);
        }
    }

    public interface DiscoveryListener {
        void found(String serverUrl);
    }
}
