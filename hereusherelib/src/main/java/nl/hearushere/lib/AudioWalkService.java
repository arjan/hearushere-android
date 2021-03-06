package nl.hearushere.lib;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.octo.android.robospice.SpiceManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Walk;
import nl.hearushere.lib.net.HttpSpiceService;

public abstract class AudioWalkService extends Service implements LocationListener, HearUsHereAudioController.UICallback {

    protected LatLng mLastLocation;
    private HearUsHereAudioController mHearUsHereAudioController;
    protected NotificationController mNotificationController;

    public interface AudioEventListener {

        void showNotification(String message);
        void hideNotification();
        void showNetworkErrorMessage();
        void showLoader(boolean flag);
        void uiUpdate();
        boolean useDebugLocation();
    }

    public static final String TAG = AudioWalkService.class.getSimpleName();

    @Nullable
    public AudioEventListener mAudioEventListener;

    private LocalBinder mBinder = new LocalBinder();
    protected Handler mHandler;
    private HandlerThread mHandlerThread;
    private Handler mUIHandler;
    protected List<Track> mTrackList;
    private boolean mSoundsLoaded;
    protected boolean mStarted;

    protected Walk mCurrentWalk;

    protected Walk mLastWalk;

    public class LocalBinder extends Binder {

        public void setAudioEventListener(AudioEventListener listener) {
            mAudioEventListener = listener;
            uiUpdate();
        }

        public void stopService() {
            AudioWalkService.this.stop();
        }

        public void beginWalk(final Walk walk) {
            AudioWalkService.this.startPlayback(walk);
        }

        public Walk getCurrentWalk() {
            return mCurrentWalk;
        }

        public void endWalk() {
            AudioWalkService.this.stopPlayback();
        }

        public void setDebugLocation(final LatLng location) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onLocationUpdate(location);
                    uiUpdate();
                }
            });
        }

        public LatLng getLastLocation() {
            return mLastLocation;
        }

    }

    protected void stop() {
        mLocationManager.removeUpdates(AudioWalkService.this);

        mHearUsHereAudioController.stopService();
        mHandlerThread.quit();

        stopForeground(true);
        stopSelf();
    }

    protected SpiceManager mSpiceManager = new SpiceManager(
            HttpSpiceService.class);

    protected LocationManager mLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mUIHandler = new Handler();

        mHandlerThread = new HandlerThread("Background Handler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mLocationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

        // Register the listener with the Location Manager to receive location
        // updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                0, 0, AudioWalkService.this);

        mHearUsHereAudioController = new HearUsHereAudioController(this, this);

        mSpiceManager.start(this);
    }

    protected void startPlayback(Walk walk) {
        if (mCurrentWalk != null) {
            stopPlayback();
        }

        mCurrentWalk = walk;

        mNotificationController.setCurrentWalk(mCurrentWalk);
        mNotificationController.updateServiceNotification();

        Location loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            mLastLocation = new LatLng(loc.getLatitude(), loc.getLongitude());
        }

        mTrackList = mCurrentWalk.getSounds();
        loadTracks();

        uiUpdate();

    }

    protected void loadTracks() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                System.out.println("Loading tracks " + mTrackList.size());
                for (int i = 0; mTrackList != null && i < mTrackList.size(); i++) {
                    Track track = mTrackList.get(i);

                    String url = track.getStreamUrl();
                    if (url == null) {
                        continue;
                    }

                    final File cacheFile = track
                            .getCacheFile(AudioWalkService.this);

                    if (!cacheFile.exists()) {
                        showNotification(String.format(
                                getString(R.string.load_progress), i + 1,
                                mTrackList.size()));
                        Log.v(TAG, "Downloading " + url);

                        try {
                            FileUtils.copyURLToFile(new URL(url), cacheFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (mTrackList != null) {
                    soundsLoaded();
                } else {
                    // starting aborted
                    hideNotification();
                }
            }
        });
    }

    protected void soundsLoaded() {
        mHearUsHereAudioController.startWalk(mCurrentWalk);
        mSoundsLoaded = true;
        showNotification("Please go to the area located on the map to begin.");
        if (mLastLocation != null) {
            onLocationUpdate(mLastLocation);
        }
    }

    protected void stopPlayback() {
        if (mAudioEventListener != null) {
            mAudioEventListener.showLoader(false);
        }
        hideNotification();

        mHearUsHereAudioController.stopWalk();

        mLastWalk = mCurrentWalk;
        mCurrentWalk = null;

        mNotificationController.setLastWalk(mLastWalk);
        mNotificationController.setCurrentWalk(null);
        mNotificationController.updateServiceNotification();

        mSoundsLoaded = false;
        mTrackList = null;

        uiUpdate();
    }

    protected void onLocationUpdate(LatLng location) {
        mLastLocation = location;

        if (mTrackList == null) {
            return;
        }

        Log.v(TAG, "LOCATION UPDATE " + location.toString());

        mHearUsHereAudioController.playLocationSounds(location);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!mStarted) {

            mNotificationController = new NotificationController(this);
            startForeground(NotificationController.NOTIFICATION_ID, mNotificationController.buildServiceNotification());

            mStarted = true;
        }

        if (intent != null) {
            if (NotificationController.ACTION_START.equals(intent.getAction()) && mLastWalk != null) {
                startPlayback(mLastWalk);
            }
            if (NotificationController.ACTION_STOP.equals(intent.getAction())) {
                stopPlayback();
            }
        }

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        mHandlerThread.quit();

        System.out.println("AudioWalkService onDestroy");

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onUnbind(Intent intent) {
        if (mNotificationController != null) {
            mNotificationController.unbind();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void showNotification(final String msg) {
        if (mAudioEventListener == null) {
            return;
        }
        mUIHandler.post(new Runnable() {

            @Override
            public void run() {
                mAudioEventListener.showNotification(msg);
            }
        });
    }

    @Override
    public void hideNotification() {
        if (mAudioEventListener == null) {
            return;
        }
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                mAudioEventListener.hideNotification();
            }
        });
    }

    public void uiUpdate() {
        if (mAudioEventListener == null) {
            return;
        }
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                mAudioEventListener.uiUpdate();
            }
        });
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }

    private Location mLastSensedLocation;

    @Override
    public void onLocationChanged(final Location location) {
        if (location == null || !mSoundsLoaded || mTrackList == null
                || (mAudioEventListener != null && mAudioEventListener.useDebugLocation()) ) {
            return;
        }

        if (!Utils.isBetterLocation(location, mLastSensedLocation)) {
            return;
        }
        mLastSensedLocation = location;

        Runnable playLocationSounds = new Runnable() {
            @Override
            public void run() {
                onLocationUpdate(new LatLng(location.getLatitude(),
                        location.getLongitude()));
            }
        };
        mHandler.removeCallbacks(playLocationSounds);
        mHandler.postDelayed(playLocationSounds, 200);
    }

    public abstract int getStatIcon();
    public abstract int getAppIcon();
    public abstract int getAppName();
    public abstract Class<? extends Service> getAudioService();
    public abstract Class<? extends Activity> getMainActivity();
}
