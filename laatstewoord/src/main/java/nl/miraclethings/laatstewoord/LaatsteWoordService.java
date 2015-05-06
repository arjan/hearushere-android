package nl.miraclethings.laatstewoord;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.IBinder;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Map;

import nl.hearushere.lib.AudioWalkService;
import nl.hearushere.lib.NotificationController;
import nl.hearushere.lib.Utils;
import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Triggers;

/**
 * Yay
 * <p/>
 * Created by Arjan Scherpenisse on 6-5-15.
 */
public class LaatsteWoordService extends AudioWalkService {
    private Triggers mTriggers;
    private State mCurrentState;
    private Map<Track, Triggers.Url> mTriggerLookup;

    @Override
    public int getStatIcon() {
        return R.drawable.ic_stat_laatstewoord;
    }

    @Override
    public int getAppIcon() {
        return R.mipmap.ic_launcher;
    }

    @Override
    public int getAppName() {
        return R.string.app_name;
    }

    @Override
    public Class<? extends Activity> getMainActivity() {
        return MainActivity.class;
    }

    public class LocalBinder extends AudioWalkService.LocalBinder {
        public Triggers getTriggers() {
            return mTriggers;
        }
    }

    private LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!mStarted && intent != null) {

            mTriggers = (Triggers) intent.getSerializableExtra("triggers");

            mNotificationController = new NotificationController(this);
            startForeground(NotificationController.NOTIFICATION_ID, mNotificationController.buildServiceNotification());

            mStarted = true;

            mTrackList = mTriggers.getSoundTracks();
            loadTracks();

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

    protected void soundsLoaded() {

        mTriggerLookup = new HashMap<>();

        mCurrentWalk = mTriggers.buildWalk();

        mNotificationController.setCurrentWalk(mCurrentWalk);
        mNotificationController.updateServiceNotification();

        Location loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            mLastLocation = new LatLng(loc.getLatitude(), loc.getLongitude());
        }

        super.soundsLoaded();
        showNotification("All sounds loaded, do something now!");

        uiUpdate();

        //setState(FIRST_SOUND);
    }

    @Override
    protected void onLocationUpdate(LatLng location) {
        super.onLocationUpdate(location);

        if (mTriggers == null) {
            return;
        }

        if (mCurrentState == FIRST_SOUND) {
            return;
        }

        Triggers.Area currentArea = getCurrentArea(location);
        if (currentArea != null) {

            setState(IN_TRIGGER_AREA);
            // we are in a trigger area
            Track trigger = currentArea.getClosestTrigger(location);

            if (trigger != null) {

                Triggers.Url sound;
                if (!mTriggerLookup.containsKey(trigger)) {
                    if (currentArea.sounds.size() == 0) {
                        System.out.println("huh?");
                        return;
                    }

                    // get the next sound, assign it to the trigger
                    sound = currentArea.sounds.get(0);
                    currentArea.sounds.remove(0);

                    mTriggerLookup.put(trigger, sound);

                    System.out.println("Connect trigger, " + sound.url + ", to " + trigger.toString());


                } else {
                    System.out.println("Existing trigger");
                    // just play the existing sound
                    sound = mTriggerLookup.get(trigger);
                    System.out.println(sound.url);
                }

                System.out.println("PLAY::: " + sound.url + " " + trigger.toString());

                IN_TRIGGER_AREA.playTriggerSound(sound);
            }


        } else if (Utils.isInsidePolygon(location, mTriggers.getDisplayCoords())) {
            // we are inside the map but not in a trigger area
            setState(INSIDE);
        } else {
            setState(OUTSIDE);
        }

        uiUpdate();
    }

    private void checkTriggersDone() {
        for (Triggers.Area area : mTriggers.areas) {
            if (area.sounds.size() > 0) {
                return;
            }
        }

        System.out.println("-- ALL DONE!!! --");
        setState(LAST_SOUND);
    }

    private Triggers.Area getCurrentArea(LatLng location) {
        for (Triggers.Area area : mTriggers.areas) {
            if (Utils.isInsidePolygon(location, area.getCoords())) {
                return area;
            }
        }
        return null;
    }


    private void setState(State newState) {
        if (mCurrentState == newState) {
            return;
        }

        if (mCurrentState != null) {
            mCurrentState.exit();
        }

        System.out.println("State: --> " + (newState == null ? "..." : newState.toString()));
        mCurrentState = newState;

        if (mCurrentState != null) {
            mCurrentState.enter();
        }

    }
    interface State {

        void enter();
        void exit();

    }

    private State OUTSIDE = new State() {

        public MediaPlayer player;

        @Override
        public void enter() {
            player = Utils.playSoundOnce(LaatsteWoordService.this, mTriggers.outOfBoundsSound, null);
        }

        @Override
        public void exit() {
        }

        @Override
        public String toString() {
            return "OUTSIDE";
        }
    };

    private State INSIDE = new State() {
        @Override
        public void enter() {

        }

        @Override
        public void exit() {

        }

        @Override
        public String toString() {
            return "INSIDE";
        }
    };

    private State FIRST_SOUND = new State() {
        private MediaPlayer player;

        @Override
        public void enter() {
            player = Utils.playSoundOnce(LaatsteWoordService.this, mTriggers.firstSound, null);
        }

        @Override
        public void exit() {
        }

        @Override
        public String toString() {
            return "FIRST_SOUND";
        }
    };
    class TriggerAreaState implements State {

        private String lastUrl;
        private MediaPlayer player;

        @Override
        public void enter() {
        }

        @Override
        public void exit() {
        }

        public void playTriggerSound(final Triggers.Url url) {
            if (lastUrl != null && lastUrl.equals(url.url)) {
                System.out.println("Already playing.. " + url.url);
                return;
            }
            lastUrl = url.url;

            System.out.println("playTriggerSound " + url.url);
            player = Utils.playSoundOnce(LaatsteWoordService.this, url, new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    player = null;
                    checkTriggersDone();
                }
            });
        }
        public String toString() {
            return "IN_TRIGGER_AREA";
        }

    }

    private TriggerAreaState IN_TRIGGER_AREA = new TriggerAreaState();

    private State LAST_SOUND = new State() {

        public MediaPlayer player;

        @Override
        public void enter() {
            player = Utils.playSoundOnce(LaatsteWoordService.this, mTriggers.lastSound, null);
        }

        @Override
        public void exit() {
        }

        @Override
        public String toString() {
            return "LAST_SOUND";
        }
    };


}

