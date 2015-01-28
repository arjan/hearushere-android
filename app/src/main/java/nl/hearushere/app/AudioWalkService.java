package nl.hearushere.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.google.android.gms.maps.model.LatLng;
import com.octo.android.robospice.SpiceManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.hearushere.app.data.Track;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.main.R;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;

public class AudioWalkService extends Service implements LocationListener, BeaconManager.ServiceReadyCallback, BeaconManager.MonitoringListener {

    private static final String ACTION_STOP = "ACTION_STOP";
    private static final String ACTION_START = "ACTION_START";

    private MediaSessionManager mManager;
    private MediaSession mSession;
    private Object mController;
    private BeaconManager mBeaconManager;
    private HashMap<String, Track> mBluetoothTrackMap;
    private LatLng mLastLocation;

    public interface AudioEventListener {



        public void showNotification(String message);
		public void hideNotification();

		public void showNetworkErrorMessage();

		public void showLoader(boolean flag);

        public void uiUpdate();

	}

	public static final String TAG = AudioWalkService.class.getSimpleName();

	private static int NOTIFICATION_ID = 1001;

    private static int INTENT_ACTIVITY_ID = 1002;
    private static final int LOCATION_TIME_DELTA = 1000 * 30;
	public AudioEventListener mAudioEventListener;

    private LocalBinder mBinder = new LocalBinder();
    protected Handler mHandler;
    private HandlerThread mHandlerThread;
    private Handler mUIHandler;
    private List<Track> mTrackList;
    private Map<String, Track> mCurrentBluetoothSounds;
    private boolean mSoundsLoaded;
	private boolean mStarted;

	private Walk mCurrentWalk;

    private Walk mLastWalk;
	private VolumeManager mVolumeHandler;

	public int mTotalDuration = -1;

    private long mSoundStartTime;
	public class LocalBinder extends Binder {

        public void setAudioEventListener(AudioEventListener listener) {
			mAudioEventListener = listener;
		}
		public void stopService() {

			mLocationManager.removeUpdates(AudioWalkService.this);

			if (mTrackList != null) {
				for (Track track : mTrackList) {
					mVolumeHandler.removeMessages(track.getId());
					MediaPlayer mp = track.getMediaPlayer();
					if (mp != null) {
						mp.stop();
						mp.release();
					}
				}
			}
			mHandlerThread.quit();
			stopSelf();
		}

		public void startPlayback(final Walk walk) {
            AudioWalkService.this.startPlayback(walk);
		}

        public Walk getCurrentWalk() {
			return mCurrentWalk;
		}

		public void stopPlayback() {
            AudioWalkService.this.stopPlayback();
		}

		public void setDebugLocation(final LatLng location) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					onLocationUpdate(location);
				}
			});
		}

    }
	protected SpiceManager mSpiceManager = new SpiceManager(
			HttpSpiceService.class);

	private API mAPI;

	private LocationManager mLocationManager;

	private Location mLastSensedLocation;

	@Override
	public void onCreate() {
		super.onCreate();

		mUIHandler = new Handler();

		mHandlerThread = new HandlerThread("Audio Background Handler");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());
		mVolumeHandler = new VolumeManager(mHandlerThread.getLooper());

		mLocationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		// Register the listener with the Location Manager to receive location
		// updates
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				0, 0, AudioWalkService.this);

		mAPI = new API(mSpiceManager);
		mSpiceManager.start(this);
    }

    private void startPlayback(Walk walk) {
        if (mCurrentWalk != null) {
            stopPlayback();
        }
        mCurrentWalk = walk;

        Log.v(TAG, "sync? "
                + (mCurrentWalk.areTracksSynchronized() ? "Yes" : "no"));

        updateServiceNotification();

        mTrackList = mCurrentWalk.getSounds();
        loadTracks();

        if (mAudioEventListener != null) {
            mAudioEventListener.uiUpdate();
        }

        if (hasBluetoothSupport() && mCurrentWalk.hasBluetooth()) {
            startBluetoothScan();
        }
    }



    private boolean hasBluetoothSupport() {
        return Build.VERSION.SDK_INT >= 18 && getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void startBluetoothScan() {
        mBeaconManager = new BeaconManager(this);
        mBeaconManager.connect(this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBluetoothScan() {
        System.out.println("stop cscan");
        mBeaconManager.disconnect();
    }

    public void loadTracks() {
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
							break;
						}
					}
				}
				mSoundStartTime = -1;
				if (mTrackList != null) {
					mSoundsLoaded = true;
					showNotification("Please go to the area located on the map to begin.");
				} else {
					// starting aborted
					hideNotification();
				}
			}
		});
	}

    private void stopPlayback() {
        mAudioEventListener.showLoader(false);
        hideNotification();

        if (mTrackList != null) {
            for (Track track : mTrackList) {
                mVolumeHandler.removeMessages(track.getId());
                MediaPlayer mp = track.getMediaPlayer();
                if (mp != null) {
                    mp.stop();
                    mp.reset();
                    mp.release();
                }
            }
        }

        if (hasBluetoothSupport() && mCurrentWalk.hasBluetooth()) {
            stopBluetoothScan();
        }

        mLastWalk = mCurrentWalk;
        mCurrentWalk = null;

        updateServiceNotification();

        mSoundsLoaded = false;
        mTrackList = null;

        if (mAudioEventListener != null) {
            mAudioEventListener.uiUpdate();
        }
    }

	private void onLocationUpdate(LatLng location) {
        if (mTrackList == null) {
            return;
        }

        Log.v(TAG, "LOCATION UPDATE " + location.toString());

        mLastLocation = location;
        playLocationSounds();
    }

    private void playLocationSounds() {
		List<Track> sorted = getDistanceSortedTracks(mLastLocation);

        boolean insideMapArea = isInsideMapArea(mLastLocation);
        if (!insideMapArea) {
            showNotification("You are too far away from the sounds, please move closer.");
        } else {
            hideNotification();
        }

        // loop through all sounds
		int soundsPlaying = 0;
		for (Track track : sorted) {
            if (track.getFile() == null) {
                continue; // invalid file
            }

			boolean shouldPlay = track.getCurrentDistance() < track.getRadius()
					&& soundsPlaying < Constants.MAX_SIMULTANEOUS_SOUNDS
                    && !track.isBackground()
                    && !track.isBluetooth();

            if (track.isBackground()) {
                shouldPlay = true;
                if (!insideMapArea) {
                    shouldPlay = false;
                }
            }
            if (track.isBluetooth() && mCurrentBluetoothSounds.containsValue(track)) {
                System.out.println("PLAY BT!!!");
                shouldPlay = true;
            }

			// if we are in range, we should play this track
			if (shouldPlay) {
                System.out.println("should play: " + track.getFile());
                float volume = track.getCalculatedVolume(mCurrentWalk
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

		if (location == null)
			return false;
        ArrayList<ArrayList<LatLng>> polyLocs = mCurrentWalk.getPoints();

        for (ArrayList<LatLng> polyLoc : polyLocs) {
            LatLng lastPoint = polyLoc.get(polyLoc.size() - 1);
            boolean isInside = false;
            double x = location.longitude;

            for (LatLng point : polyLoc) {
                double x1 = lastPoint.longitude;
                double x2 = point.longitude;
                double dx = x2 - x1;

                if (Math.abs(dx) > 180.0) {
                    // we have, most likely, just jumped the dateline (could do
                    // further validation to this effect if needed). normalise the
                    // numbers.
                    if (x > 0) {
                        while (x1 < 0)
                            x1 += 360;
                        while (x2 < 0)
                            x2 += 360;
                    } else {
                        while (x1 > 0)
                            x1 -= 360;
                        while (x2 > 0)
                            x2 -= 360;
                    }
                    dx = x2 - x1;
                }

                if ((x1 <= x && x2 > x) || (x1 >= x && x2 < x)) {
                    double grad = (point.latitude - lastPoint.latitude) / dx;
                    double intersectAtLat = lastPoint.latitude + ((x - x1) * grad);

                    if (intersectAtLat > location.latitude)
                        isInside = !isInside;
                }
                lastPoint = point;
            }

            if (isInside) return true;
        }
        return false;
	}

	private List<Track> getDistanceSortedTracks(LatLng position) {
		List<Track> result = new ArrayList<Track>();

		float[] results = new float[3];
		for (Track track : mTrackList) {
            if (track.isBackground() || track.isBluetooth()) {
                continue;
            }

            LatLng p = track.getLocationLatLng();
			if (p == null || track.getStreamUrl() == null) {
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
        for (Track track : mTrackList) {
            if (track.isBackground() || track.isBluetooth()) {
                result.add(track);
            }
        }

        return result;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if (!mStarted) {
            if (Build.VERSION.SDK_INT >= 21) {
                initMediaSession();
            }
			Notification notification = buildServiceNotification();
			startForeground(NOTIFICATION_ID, notification);

			mStarted = true;
		}

        if (ACTION_START.equals(intent.getAction())) {
            startPlayback(mLastWalk);
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopPlayback();
        }


		return START_STICKY;
	}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initMediaSession() {
        mManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mSession = new MediaSession(getApplicationContext(), "sample session");
        mController = new MediaController(getApplicationContext(), mSession.getSessionToken());
        mSession.setActive(true);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                System.out.println("play");
            }

            @Override
            public void onPause() {
                super.onPause();
                System.out.println("pause");
            }
        });
    }

    private void updateServiceNotification() {
		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
				.notify(NOTIFICATION_ID, buildServiceNotification());
	}

    private Notification buildServiceNotification() {
		Intent startIntent = new Intent(this, MainActivity.class);
		startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		PendingIntent p = PendingIntent.getActivity(this, INTENT_ACTIVITY_ID,
				startIntent, 0);

        if (Build.VERSION.SDK_INT < 21) {
            return buildNotificationPreLollipop(p);
        }

        return buildMediaNotification(p);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Notification buildMediaNotification(PendingIntent p) {
        Notification.Builder builder = new Notification.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_huh)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setContentIntent(p)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(
                        mCurrentWalk == null ? getString(R.string.app_name)
                                : mCurrentWalk.getTitle())
                .setContentText(
                        getString(mCurrentWalk == null ? R.string.notification_not_started_text
                                : R.string.notification_progress_text_small));

        boolean hasAction = false;
        if (mCurrentWalk != null) {
            builder.setTicker(getString(R.string.notification_progress_text_small));
            builder.addAction(generateAction(android.R.drawable.ic_media_pause, "Stop", ACTION_STOP));
            hasAction = true;
        } else {
            if (mLastWalk != null) {
                builder.addAction(generateAction(android.R.drawable.ic_media_play, "Start", ACTION_START));
                hasAction = true;
            }
        }
        if (hasAction) {
            builder.setStyle(new Notification.MediaStyle()
                    .setShowActionsInCompactView(0 /* #1: pause button */)
                    .setMediaSession(mSession.getSessionToken()));
        }

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Notification.Action generateAction( int icon, String title, String intentAction ) {
        Intent intent = new Intent( getApplicationContext(), AudioWalkService.class );
        intent.setAction( intentAction );
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);
        return new Notification.Action.Builder( icon, title, pendingIntent ).build();
    }

    private Notification buildNotificationPreLollipop(PendingIntent p) {
        Notification notification = new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_huh)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setContentIntent(p)
                .setTicker(getString(R.string.notification_progress_text_small))
                .setContentTitle(
                        mCurrentWalk == null ? getString(R.string.app_name)
                                : mCurrentWalk.getTitle())
                .setContentText(
                        getString(mCurrentWalk == null ? R.string.notification_not_started_text
                                : R.string.notification_progress_text)).build();
        return notification;
    }


    @Override
	public void onDestroy() {
		mHandlerThread.quit();

        super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onUnbind(Intent intent) {

        if (mSession != null) {
            mSession.release();
        }

        return super.onUnbind(intent);
    }

    private void showNotification(final String msg) {
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

	private void hideNotification() {
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
				mp.setDataSource(track.getCacheFile(AudioWalkService.this)
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

			if (mCurrentWalk.areTracksSynchronized()) {
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

	@Override
	public void onLocationChanged(final Location location) {
		if (location == null || !mSoundsLoaded || mTrackList == null
				|| Constants.USE_DEBUG_LOCATION) {
			return;
		}

		if (!isBetterLocation(location, mLastSensedLocation)) {
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

	/**
	 * Determines whether one Location reading is better than the current
	 * Location fix
	 *
	 * @param location
	 *            The new Location that you want to evaluate
	 * @param currentBestLocation
	 *            The current Location fix, to which you want to compare the new
	 *            one
	 */
	protected boolean isBetterLocation(Location location,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > LOCATION_TIME_DELTA;
		boolean isSignificantlyOlder = timeDelta < -LOCATION_TIME_DELTA;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use
		// the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be
			// worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
				.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Determine location quality using a combination of timeliness and
		// accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate) {
			return true;
		}
		return false;
	}

    @Override
    public void onServiceReady() {

        System.out.println("-- ready for beacon service --");
        mBeaconManager.setBackgroundScanPeriod(5 * 1000, 15 * 1000);

        try {
            mBeaconManager.setMonitoringListener(this);

            mCurrentBluetoothSounds = new HashMap<>();
            mBluetoothTrackMap = new HashMap<>();

            for (Track t : mCurrentWalk.getBluetoothTracks()) {
                Region region = convertTrackToRegion(t);
                if (region != null) {
                    mBeaconManager.startMonitoring(region);
                    mBluetoothTrackMap.put(""+t.getId(), t);
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
        playLocationSounds();
    }

    @Override
    public void onExitedRegion(Region region) {
        System.out.println("Gone..!!!" + region);
        mCurrentBluetoothSounds.remove(region.getIdentifier());
        playLocationSounds();
    }

}
