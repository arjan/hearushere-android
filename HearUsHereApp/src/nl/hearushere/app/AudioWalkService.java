package nl.hearushere.app;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.hearushere.app.data.Track;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;

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
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

public class AudioWalkService extends Service implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {

	public interface AudioEventListener {
		public void showNotification(String message);

		public void hideNotification();

		public void showNetworkErrorMessage();

		public void showLoader(boolean flag);

	}

	public static final String TAG = AudioWalkService.class.getSimpleName();

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

	private Walk mCurrentWalk;

	private LocationClient mLocationClient;

	private LocationRequest mLocationRequest;

	private VolumeManager mVolumeHandler;

	public int mTotalDuration = -1;
	private long mSoundStartTime;

	public class LocalBinder extends Binder {
		public void setAudioEventListener(AudioEventListener listener) {
			mAudioEventListener = listener;
		}

		public void stopService() {
			mLocationClient.disconnect();
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

		public void startPlayback(Walk walk) {
			if (mCurrentWalk != null) {
				stopPlayback();
			}
			mCurrentWalk = walk;
			mTrackList = null;

			System.out.println("STARTPLA");
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

		public void loadTrackList(Track.List list) {
			stopPlayback();
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

	@Override
	public void onCreate() {
		super.onCreate();

		mUIHandler = new Handler();

		mHandlerThread = new HandlerThread("Audio Background Handler");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());
		mVolumeHandler = new VolumeManager(mHandlerThread.getLooper());

		mLocationClient = new LocationClient(this, this, this);

		mAPI = new API(mSpiceManager);
		mSpiceManager.start(this);
	}

	public void loadTracks() {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				Log.v(TAG, "Loading next track");
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
				hideNotification();
				mSoundStartTime = -1;
				mSoundsLoaded = true;
			}
		});
	}

	private void playLocationSounds(LatLng location) {
		if (mTrackList == null) {
			return;
		}
		
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

				float volume = track.getCalculatedVolume();
				mVolumeHandler.fadeToVolume(track, volume, Constants.FADE_TIME);

				soundsPlaying++;
			} else {
				if (mp != null) {
					Log.v(TAG, "stop " + track.getTitle());
					mVolumeHandler.fadeToVolume(track, 0f, Constants.FADE_TIME);
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
		if (!mSoundsLoaded || mTrackList == null || Constants.USE_DEBUG_LOCATION) {
			return;
		}

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

		mLocationRequest = LocationRequest.create();
		// Use high accuracy
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		mLocationRequest.setInterval(30 * 1000);
		mLocationRequest.setFastestInterval(5 * 1000);
		mLocationClient.requestLocationUpdates(mLocationRequest, this);

		onLocationChanged(mLocationClient.getLastLocation());
	}

	@Override
	public void onDisconnected() {
		Log.v(TAG, "Location services disconnected!!");
		showNotification("Location disconnected");

		if (mLocationRequest != null) {
			mLocationClient.requestLocationUpdates(mLocationRequest, this);
		}
	}

	private class VolumeManager extends Handler {
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
				mp = buildMediaPlayer(track);
				track.setMediaPlayer(mp);
				Log.v(TAG, "Start track: " + track.getTitle());
			}
			track.setCurrentVolume(v);

			if (v < 0.000001f) {
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

			if (Constants.TRACKS_ARE_SYNCHRONIZED) {
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
}
