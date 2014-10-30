package nl.hearushere.app;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.hearushere.app.bluetooth.BeaconScannerService;
import nl.hearushere.app.data.Track;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.main.R;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;

import org.apache.commons.io.FileUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

public class AudioWalkService extends Service implements LocationListener {

	public interface AudioEventListener {
		public void showNotification(String message);

		public void hideNotification();

		public void showNetworkErrorMessage();

		public void showLoader(boolean flag);

	}

	public static final String TAG = AudioWalkService.class.getSimpleName();

	private static int INTENT_ONGOING_ID = 1001;
	private static int INTENT_ACTIVITY_ID = 1002;
	private static final int LOCATION_TIME_DELTA = 1000 * 30;

	public AudioEventListener mAudioEventListener;
	private LocalBinder mBinder = new LocalBinder();
	protected Handler mHandler;
	private HandlerThread mHandlerThread;
	private Handler mUIHandler;
	private Track.List mTrackList;
	private boolean mSoundsLoaded;

	private boolean mStarted;

	private Walk mCurrentWalk;

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
			if (mCurrentWalk != null) {
				stopPlayback();
			}
			mCurrentWalk = walk;
			mTrackList = null;

			Log.v(TAG, "sync? "
					+ (mCurrentWalk.areTracksSynchronized() ? "Yes" : "no"));

			updateServiceNotification();

			if (mAudioEventListener != null) {
				mAudioEventListener.showLoader(true);
			}

			mAPI.getSoundCloudUserTracks(walk.getScUser(),
					new RequestListener<Track.List>() {
						@Override
						public void onRequestSuccess(Track.List list) {
							if (mAudioEventListener != null) {
								mAudioEventListener.showLoader(false);
							}

							for (Track t : list) {
								t.determineLocationAndRadius(walk.getRadius());
							}

							// if (Constants.USE_DEBUG_LOCATION) {
							// for (Track track : arg0) {
							// mMap.addMarker(new MarkerOptions()
							// .position(track.getLocation())
							// .alpha(0.5f).title(track.getTitle()));
							// }
							// }
							mTrackList = list;
							loadTracks();
						}

						@Override
						public void onRequestFailure(SpiceException arg0) {
							if (mAudioEventListener != null) {
								mAudioEventListener.showNetworkErrorMessage();
							}
						}
					});

		}

		public Walk getCurrentWalk() {
			return mCurrentWalk;
		}

		public void stopPlayback() {
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
			mCurrentWalk = null;
			updateServiceNotification();

			mSoundsLoaded = false;
			mTrackList = null;
		}

		public void setDebugLocation(final LatLng location) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					playLocationSounds(location);
				}
			});
		}
	}

	protected SpiceManager mSpiceManager = new SpiceManager(
			HttpSpiceService.class);

	private API mAPI;

	private LocationManager mLocationManager;

	private Location mLastLocation;

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

	public void loadTracks() {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; mTrackList != null && i < mTrackList.size(); i++) {
					Track track = mTrackList.get(i);

					final File cacheFile = track
							.getCacheFile(AudioWalkService.this);

					String url = track.getStreamUrl() + "?client_id="
							+ getString(R.string.area_soundcloud_client_id);

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

	private void playLocationSounds(LatLng location) {
		if (mTrackList == null) {
			return;
		}

		Log.v(TAG, "LOCATION UPDATE " + location.toString());

        boolean insideMapArea = isInsideMapArea(location);
        if (!insideMapArea) {
			showNotification("You are too far away from the sounds, please move closer.");
		} else {
			hideNotification();
		}
		List<Track> sorted = getDistanceSortedTracks(location);

		// loop through all sounds
		int soundsPlaying = 0;
		for (Track track : sorted) {

			boolean shouldPlay = track.getCurrentDistance() < track.getRadius()
					&& soundsPlaying < Constants.MAX_SIMULTANEOUS_SOUNDS;

            if (track.isBackground()) {
                shouldPlay = true;
                if (!insideMapArea) {
                    shouldPlay = false;
                }
            }

			// if we are in range, we should play this track
			if (shouldPlay) {
                System.out.println("should play: " + track.getTitle());
                float volume = track.getCalculatedVolume(mCurrentWalk
						.getRadius());
				mVolumeHandler.fadeToVolume(track, volume, Constants.FADE_TIME);

				soundsPlaying++;
			} else {
                MediaPlayer mp = track.getMediaPlayer();
				if (mp != null) {
					Log.v(TAG, "stop " + track.getTitle());
					mVolumeHandler.fadeToVolume(track, 0f, Constants.FADE_TIME);
				}
			}
		}

        System.out.println("Sounds playing: " + soundsPlaying);

	}

	private boolean isInsideMapArea(LatLng location) {
		ArrayList<LatLng> polyLoc = mCurrentWalk.getPoints();

		if (location == null)
			return false;
		
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

		return isInside;
	}

	private List<Track> getDistanceSortedTracks(LatLng position) {
		List<Track> result = new ArrayList<Track>();
        Track bg = null;
		float[] results = new float[3];
		for (Track track : mTrackList) {
            if (track.isBackground()) {
                bg = track;
                continue;
            }

            LatLng p = track.getLocation();
			if (p == null) {
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

        if (bg != null) {
            result.add(bg);
        }
        return result;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if (!mStarted) {
			Notification notification = buildServiceNotification();
			startForeground(INTENT_ONGOING_ID, notification);

            startService(new Intent(this, BeaconScannerService.class));

			mStarted = true;
		}

		return START_STICKY;
	}

	private void updateServiceNotification() {
		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
				.notify(INTENT_ONGOING_ID, buildServiceNotification());
	}

	private Notification buildServiceNotification() {
		Intent startIntent = new Intent(this, MainActivity.class);
		startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		PendingIntent p = PendingIntent.getActivity(this, INTENT_ACTIVITY_ID,
				startIntent, 0);

		Notification notification = new Notification.Builder(this)
				.setOngoing(true)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(p)
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

        stopService(new Intent(this, BeaconScannerService.class));

        super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
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
				Log.v(TAG, "Start track: " + track.getTitle());
			}
			track.setCurrentVolume(v);

			if (v < VOLUME_CUTOFF) {
				mp = track.getMediaPlayer();
				mp.stop();
				mp.reset();
				mp.release();
				track.setMediaPlayer(null);
				Log.v(TAG, "Stop track: " + track.getTitle());
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

		if (!isBetterLocation(location, mLastLocation)) {
			return;
		}
		mLastLocation = location;

		Runnable playLocationSounds = new Runnable() {
			@Override
			public void run() {
				playLocationSounds(new LatLng(location.getLatitude(),
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

}
