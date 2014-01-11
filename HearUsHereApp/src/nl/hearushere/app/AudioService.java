package nl.hearushere.app;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.hearushere.app.data.Track;

import org.apache.commons.io.FileUtils;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

public class AudioService extends Service implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {

	public interface AudioEventListener {
		// here

		public void showNotification(String message);

		public void hideNotification();

	}

	public static final String TAG = AudioService.class.getSimpleName();

	private static int INTENT_ONGOING_ID = 1001;
	private static int INTENT_ACTIVITY_ID = 1002;

	public AudioEventListener mAudioEventListener;
	private LocalBinder mBinder = new LocalBinder();
	protected Handler mHandler;
	private HandlerThread mHandlerThread;
	private Handler mUIHandler;
	private Track.List mTrackList;
	private boolean mSoundsLoaded;

	private boolean mStarted;

	private LocationClient mLocationClient;

	public class LocalBinder extends Binder {
		public void setAudioEventListener(AudioEventListener listener) {
			mAudioEventListener = listener;
		}

		public void stopService() {
			mLocationClient.disconnect();
			if (mTrackList != null) {
				for (Track track : mTrackList) {
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

		public boolean hasTracks() {
			return mTrackList != null;
		}

		public void loadTrackList(Track.List list) {
			mTrackList = list;
			loadTracks();
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

	@Override
	public void onCreate() {
		super.onCreate();

		mUIHandler = new Handler();

		mHandlerThread = new HandlerThread("Audio Background Handler");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());

		mLocationClient = new LocationClient(this, this, this);
	}

	public void loadTracks() {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				Log.v(TAG, "Loading next track");
				for (int i = 0; i < mTrackList.size(); i++) {
					Track track = mTrackList.get(i);

					final File cacheFile = track
							.getCacheFile(AudioService.this);

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
				hideNotification();
				mSoundsLoaded = true;
			}
		});
	}

	private void playLocationSounds(LatLng location) {
		Log.v(TAG, "LOCATION UPDATE " + location.toString());
		List<Track> sorted = getDistanceSortedTracks(location);

		// loop through all sounds
		int soundsPlaying = 0;
		for (Track track : sorted) {
			boolean shouldPlay = track.getCurrentDistance() < Constants.MAX_SOUND_DISTANCE
					&& soundsPlaying < Constants.MAX_SIMULTANEOUS_SOUNDS;

			MediaPlayer mp = track.getMediaPlayer();

			// if we are in range, we should play this track
			if (shouldPlay) {

				if (mp != null) {
					assert (mp.isPlaying());
					float volume = track.getVolume();
					Log.v(TAG, "... still playing " + track.getTitle() + " "
							+ volume);
					mp.setVolume(volume, volume);
				} else {
					float volume = track.getVolume();
					Log.v(TAG, "start " + track.getTitle() + " " + volume);
					mp = new MediaPlayer();
					mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
					try {
						mp.setDataSource(track.getCacheFile(AudioService.this)
								.getAbsolutePath());
						mp.prepare();
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
					mp.setVolume(volume, volume);
					mp.setLooping(true);
					mp.start();
					track.setMediaPlayer(mp);
				}
				soundsPlaying++;
			} else {
				if (mp != null) {
					Log.v(TAG, "stop " + track.getTitle());
					mp.stop();
					mp.release();
					track.setMediaPlayer(null);
				}
			}
		}

		if (soundsPlaying == 0) {
			showNotification("You are too far away from the sounds, please move closer.");
		} else {
			hideNotification();
		}

	}

	private List<Track> getDistanceSortedTracks(LatLng position) {
		List<Track> result = new ArrayList<Track>();
		float[] results = new float[3];
		for (Track track : mTrackList) {
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

		return result;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if (!mStarted) {
			Intent startIntent = new Intent(this, MainActivity.class);
			startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			PendingIntent p = PendingIntent.getActivity(this,
					INTENT_ACTIVITY_ID, startIntent, 0);

			Notification notification = new NotificationCompat.Builder(this)
					.setOngoing(true)
					.setSmallIcon(R.drawable.ic_launcher)
					.setContentIntent(p)
					.setContentTitle(getString(R.string.app_name))
					.setContentText(
							getString(R.string.notification_progress_text))
					.build();
			startForeground(INTENT_ONGOING_ID, notification);

			mLocationClient.connect();

			mStarted = true;
		}

		return START_STICKY;
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

	@Override
	public void onLocationChanged(final Location location) {
		if (!mSoundsLoaded || Constants.USE_DEBUG_LOCATION) {
			return;
		}
		Log.v(TAG, "-location changed-");

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

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		showNotification("Location connection failed");
	}

	@Override
	public void onConnected(Bundle arg0) {
		Log.v(TAG, "Location services connected");

		LocationRequest locationRequest = LocationRequest.create();
		// Use high accuracy
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setInterval(30 * 1000);
		locationRequest.setFastestInterval(5 * 1000);
		mLocationClient.requestLocationUpdates(locationRequest, this);

		onLocationChanged(mLocationClient.getLastLocation());
	}

	@Override
	public void onDisconnected() {
		Log.v(TAG, "Location services disconnected!!");
		showNotification("Location disconnected");
	}

}
