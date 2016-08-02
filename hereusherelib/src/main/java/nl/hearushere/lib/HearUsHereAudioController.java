package nl.hearushere.lib;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Walk;
import nl.miraclethings.beaconlib.BeaconScannerService;
import nl.miraclethings.beaconlib.Zone;
import nl.miraclethings.beaconlib.ZoneMap;

/**
 * Controls the main hear-us-here algorithm
 * <p/>
 * Created by Arjan Scherpenisse on 5-5-15.
 */
public class HearUsHereAudioController implements BeaconScannerService.ServiceConnectedCallback, BeaconScannerService.IForegroundListener {

    static Logger logger = LoggerFactory.getLogger(HearUsHereAudioController.class);

    private static final String TAG = "HearUsHere";
    private HandlerThread mVolumeHandlerThread;
    private int mTotalDuration;

    private long mSoundStartTime;
    private HashMap<String, Track> mBluetoothTrackMap;
    private LatLng mCurrentLocation;

    @Override
    public void setStatusMessage(String msg) {
        logger.info("BEACON: {}", msg);
    }

    @Override
    public void setCurrentRegion(String i) {
        logger.info("Set current region: {}", i);

        mCurrentBluetoothSounds.clear();
        Track bluetoothTrack = mBluetoothTrackMap.get(i);
        if (bluetoothTrack != null) {
            mCurrentBluetoothSounds.put(i, bluetoothTrack);
            logger.info("Play bluetooth sound: {}", bluetoothTrack);
            playLocationSounds(mCurrentLocation);
        }

    }

    @Override
    public void onBeaconServiceConnected(BeaconScannerService.LocalBinder binder) {
        mBeaconService = binder;
        mBeaconService.setForegroundListener(this);
        initializeBluetoothSounds();

        logger.info("connected to beacon service");
    }

    @Override
    public void onBeaconServiceDisconnected() {
        mBeaconService = null;
    }

    interface UICallback {

        void showNotification(String message);

        void hideNotification();
    }

    private Context context;

    private UICallback callback;
    private Walk mWalk;
    private Map<String, Track> mCurrentBluetoothSounds;
    private VolumeManager mVolumeHandler;

    private ServiceConnection mBeaconServiceConnection;
    private BeaconScannerService.LocalBinder mBeaconService;


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
                mVolumeHandler.removeMessages(track.getTrackHash());
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

                if (mp != null) {
                    Log.v(TAG, "Start track: " + track.getStreamUrl());
                    new NotifyTask(track).execute();
                }
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
            removeMessages(track.getTrackHash());

            float current = track.getCurrentVolume();
            float delta = (v - current) / (time / Constants.FADE_STEP);
            for (int t = 0; t < time; t += Constants.FADE_STEP) {
                current += delta;
                Message m = obtainMessage(track.getTrackHash(),
                        (int) (current * 1000), 0);
                m.obj = track;
                sendMessageDelayed(m, t);
            }
            Message m = obtainMessage(track.getTrackHash(), (int) (v * 1000), 0);
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
        mBeaconServiceConnection = BeaconScannerService.ensureBeaconService(context, this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBluetoothScan() {
        System.out.println("stop scan");
        if (mBeaconServiceConnection != null && context != null) {
            context.unbindService(mBeaconServiceConnection);
        }
    }

    private void initializeBluetoothSounds() {
        mCurrentBluetoothSounds = new HashMap<>();
        mBluetoothTrackMap = new HashMap<>();

        ZoneMap m = new ZoneMap();
        for (Track t : mWalk.getBluetoothTracks()) {
            String zoneId = t.getId();
            Zone z = createBeaconZone(t);
            m.put(zoneId, z);
            logger.info("ZoneId: {}, {}", zoneId, z.getBeacons().get(0).toString());
            mBluetoothTrackMap.put(zoneId, t);
        }

        logger.info("Set zone map!");
        mBeaconService.setZoneMap(m);
    }

    private Zone createBeaconZone(Track t) {
        Zone z = new Zone();
        Zone.Beacon b = new Zone.Beacon(t.getUuid(), Integer.parseInt(t.getMajor()), Integer.parseInt(t.getMinor()));
        z.getBeacons().add(b);
        return z;
    }

    private class NotifyTask extends AsyncTask<Void, Void, Void> {
        private Track track;

        public NotifyTask(Track track) {
            this.track = track;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                String postBody = new Gson().toJson(track.getListenerNotification(context, mBeaconService.getLastBeacon()));

                Request request = new Request.Builder()
                        .url(Constants.API_URL_PREFIX + "listeners")
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), postBody))
                        .build();

                Response response = new OkHttpClient().newCall(request).execute();
                logger.info("Posted listeners information: {}", response.toString());

            } catch (Throwable e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
