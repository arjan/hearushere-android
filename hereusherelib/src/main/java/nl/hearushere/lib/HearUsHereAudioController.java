package nl.hearushere.lib;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Walk;

/**
 * Controls the main hear-us-here algorithm
 *
 * Created by Arjan Scherpenisse on 5-5-15.
 */
public class HearUsHereAudioController implements BeaconManager.ServiceReadyCallback, BeaconManager.MonitoringListener {

    private static final String TAG = "HearUsHere";
    private HandlerThread mVolumeHandlerThread;
    private int mTotalDuration;
    private long mSoundStartTime;
    private HashMap<String, Track> mBluetoothTrackMap;
    private LatLng mCurrentLocation;

    interface UICallback {

        void showNotification(String message);

        void hideNotification();
    }

    private Context context;

    private UICallback callback;
    private Walk mWalk;
    private BeaconManager mBeaconManager;
    private Map<String, Track> mCurrentBluetoothSounds;
    private VolumeManager mVolumeHandler;

    public HearUsHereAudioController(Context context, UICallback callback) {
        this.context = context;
        this.callback = callback;

        mVolumeHandlerThread = new HandlerThread("Audio Background Handler");
        mVolumeHandlerThread.start();
        mVolumeHandler = new VolumeManager(mVolumeHandlerThread.getLooper());
    }

    public void startWalk(Walk walk) {
        mWalk = walk;
        if (hasBluetoothSupport() && mWalk.hasBluetooth()) {
            startBluetoothScan();
        }
        mSoundStartTime = -1;
    }

    public void stopWalk() {

        if (hasBluetoothSupport() && mWalk != null && mWalk.hasBluetooth()) {
            stopBluetoothScan();
        }
        stopSounds();
        mWalk = null;
    }

    public void stopService() {
        mVolumeHandlerThread.quit();
        stopSounds();
    }

    private void stopSounds() {
        if (mWalk == null) {
            return;
        }
        if (mWalk.getSounds() != null) {
            for (Track track : mWalk.getSounds()) {
                mVolumeHandler.removeMessages(track.getId());
                MediaPlayer mp = track.getMediaPlayer();
                if (mp != null) {
                    mp.stop();
                    mp.release();
                    track.setMediaPlayer(null);
                }
            }
        }
    }


    /// audio

    public void playLocationSounds(LatLng location) {
        mCurrentLocation = location;

        List<Track> sorted = getDistanceSortedTracks(location);

        boolean insideMapArea = isInsideMapArea(location);
        if (!insideMapArea) {
            callback.showNotification("You are too far away from the sounds, please move closer.");
        } else {
            callback.hideNotification();
        }

        // loop through all sounds
        int soundsPlaying = 0;
        for (Track track : sorted) {
            if (track.getStreamUrl() == null) {
                continue; // invalid file
            }

            boolean shouldPlay = track.getCurrentDistance() < track.getRadius()
                    && soundsPlaying < Constants.MAX_SIMULTANEOUS_SOUNDS
                    && !track.isBackground()
                    && !track.isBluetooth();

            if (track.isBackground()) {
                shouldPlay = insideMapArea;
            }
            if (track.isBluetooth() && mCurrentBluetoothSounds != null && mCurrentBluetoothSounds.containsValue(track)) {
                System.out.println("PLAY BT!!!");
                shouldPlay = true;
            }

            // if we are in range, we should play this track
            if (shouldPlay) {
                System.out.println("should play: " + track.getFile());
                float volume = track.getCalculatedVolume(mWalk
                        .getRadius());
                mVolumeHandler.fadeToVolume(track, volume, Constants.FADE_TIME);

                soundsPlaying++;
            } else {
                MediaPlayer mp = track.getMediaPlayer();
                if (mp != null) {
                    Log.v(TAG, "stop " + track.getFile());
                    mVolumeHandler.fadeToVolume(track, 0f, Constants.FADE_TIME);
                }
            }
        }

        System.out.println("Sounds playing: " + soundsPlaying);

    }

    private boolean isInsideMapArea(LatLng location) {

        if (location == null) {
            System.out.println("- no location..");
            return false;
        }

        List<ArrayList<LatLng>> polyLocs = mWalk.getPoints();

        for (ArrayList<LatLng> polyLoc : polyLocs) {
            if (Utils.isInsidePolygon(location, polyLoc)) return true;
        }
        return false;
    }


    private List<Track> getDistanceSortedTracks(LatLng position) {
        List<Track> result = new ArrayList<>();

        float[] results = new float[3];
        for (Track track : mWalk.getSounds()) {
            if (track.isBackground() || track.isBluetooth()) {
                continue;
            }

            LatLng p = track.getLocationLatLng();
            if (p == null || position == null || track.getStreamUrl() == null) {
                continue;
            }

            Location.distanceBetween(position.latitude, position.longitude,
                    p.latitude, p.longitude, results);
            double distance = results[0];
            track.setCurrentDistance(distance);

            result.add(track);
        }
        Collections.sort(result, new Comparator<Track>() {
            @Override
            public int compare(Track lhs, Track rhs) {
                return (int) (lhs.getCurrentDistance() - rhs
                        .getCurrentDistance());
            }
        });

        // add all bluetooth and background tracks
        for (Track track : mWalk.getSounds()) {
            if (track.isBackground() || track.isBluetooth()) {
                result.add(track);
            }
        }

        return result;
    }


    private class VolumeManager extends Handler {

        public static final float VOLUME_CUTOFF = 0.000001f;

        public VolumeManager(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // super.handleMessage(msg);

            Track track = (Track) msg.obj;
            float v = msg.arg1 / 1000.0f;

            MediaPlayer mp = track.getMediaPlayer();
            if (mp == null) {
                if (v < VOLUME_CUTOFF) {
                    return;
                }
                mp = buildMediaPlayer(track);
                track.setMediaPlayer(mp);
                Log.v(TAG, "Start track: " + track.getStreamUrl());
            }
            track.setCurrentVolume(v);

            if (v < VOLUME_CUTOFF) {
                mp = track.getMediaPlayer();
                mp.stop();
                mp.reset();
                mp.release();
                track.setMediaPlayer(null);
                Log.v(TAG, "Stop track: " + track.getStreamUrl());
            }
        }

        private MediaPlayer buildMediaPlayer(Track track) {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mp.setDataSource(track.getCacheFile(context)
                        .getAbsolutePath());
                mp.prepare();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            mp.setVolume(0f, 0f);
            mp.setLooping(true);

            if (mTotalDuration == -1) {
                mTotalDuration = mp.getDuration();
            }
            if (mSoundStartTime == -1) {
                mSoundStartTime = Calendar.getInstance().getTimeInMillis();
            }

            if (mWalk.areTracksSynchronized()) {
                mp.seekTo((int) ((Calendar.getInstance().getTimeInMillis() - mSoundStartTime) % mTotalDuration));
            }

            mp.start();

            return mp;
        }

        public void fadeToVolume(Track track, float v, int time) {
            removeMessages(track.getId());

            float current = track.getCurrentVolume();
            float delta = (v - current) / (time / Constants.FADE_STEP);
            for (int t = 0; t < time; t += Constants.FADE_STEP) {
                current += delta;
                Message m = obtainMessage(track.getId(),
                        (int) (current * 1000), 0);
                m.obj = track;
                sendMessageDelayed(m, t);
            }
            Message m = obtainMessage(track.getId(), (int) (v * 1000), 0);
            m.obj = track;
            sendMessageDelayed(m, time);
        }

    }


    /// bluetooth

    private boolean hasBluetoothSupport() {
        return Build.VERSION.SDK_INT >= 18 && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void startBluetoothScan() {
        mBeaconManager = new BeaconManager(context);
        mBeaconManager.connect(this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBluetoothScan() {
        System.out.println("stop scan");
        mBeaconManager.disconnect();
    }

    @Override
    public void onServiceReady() {

        System.out.println("-- ready for beacon service --");
        mBeaconManager.setBackgroundScanPeriod(5 * 1000, 15 * 1000);

        try {
            mBeaconManager.setMonitoringListener(this);

            mCurrentBluetoothSounds = new HashMap<>();
            mBluetoothTrackMap = new HashMap<>();

            for (Track t : mWalk.getBluetoothTracks()) {
                Region region = convertTrackToRegion(t);
                if (region != null) {
                    mBeaconManager.startMonitoring(region);
                    mBluetoothTrackMap.put("" + t.getId(), t);
                }
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private Region convertTrackToRegion(Track t) {
        if (t.getUuid() == null || t.getMajor() == null || t.getMinor() == null) {
            return null;
        }
        return new Region("" + t.getId(), t.getUuid(), Integer.parseInt(t.getMajor()), Integer.parseInt(t.getMinor()));
    }

    @Override
    public void onEnteredRegion(Region region, List<Beacon> beacons) {
        Track bluetoothTrack = mBluetoothTrackMap.get(region.getIdentifier());
        mCurrentBluetoothSounds.put(region.getIdentifier(), bluetoothTrack);
        System.out.println("Play bluetooth sound!");
        playLocationSounds(mCurrentLocation);
    }

    @Override
    public void onExitedRegion(Region region) {
        System.out.println("Gone..!!!" + region);
        mCurrentBluetoothSounds.remove(region.getIdentifier());
        playLocationSounds(mCurrentLocation);
    }
}
