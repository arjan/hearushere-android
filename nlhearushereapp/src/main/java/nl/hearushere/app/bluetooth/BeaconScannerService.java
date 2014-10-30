package nl.hearushere.app.bluetooth;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BeaconScannerService extends Service {
    private Handler handler;
    private BeaconManager beaconManager;
    private Region beaconRegion;
    private IForegroundListener mListener;

    public class LocalBinder extends Binder {
        public void setForegroundListener(IForegroundListener listener) {
            mListener = listener;
            startScanning();
        }
    }

    public interface IForegroundListener {

        void setStatusMessage(String msg);

        void setCurrentRegion(String i);
    }

    private LocalBinder mBinder = new LocalBinder();

    private static final String TAG = "BeaconService";

    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";

    private static int[] knownBeacons;

    private static final double enterThreshold = 1.5;
    private static final double exitThreshold = 2.5;


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler();

        beaconRegion = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
        
        beaconManager = new BeaconManager(getApplicationContext());
        com.estimote.sdk.utils.L.enableDebugLogging(false);

        beaconManager.setForegroundScanPeriod(500, 500);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        //sendBroadcast(intent);
                        for (Beacon b : beacons) {
                            System.out.println("BEACON: " + b.toString());
                        }
                    }
                });
            }
        });
    }

    private void startScanning() {
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    beaconManager.stopRanging(beaconRegion);
                    beaconManager.startRanging(beaconRegion);
                } catch (RemoteException e) {
                    Log.d(TAG, "Error while starting Ranging");
                }
            }
        });
    }

    private void stopScanning() {
        try {
            beaconManager.stopRanging(beaconRegion);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot stop but it does not matter now", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScanning();
        beaconManager.disconnect();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startScanning();
        return START_STICKY;
    }

    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

}