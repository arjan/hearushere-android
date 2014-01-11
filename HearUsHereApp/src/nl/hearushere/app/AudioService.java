package nl.hearushere.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class AudioService extends Service {

	public interface AudioEventListener {
		// here
	}

	public static final String TAG = AudioService.class.getSimpleName();

	private static int INTENT_ONGOING_ID = 1001;
	private static int INTENT_ACTIVITY_ID = 1002;

	public AudioEventListener mAudioEventListener;
	private LocalBinder mBinder = new LocalBinder();
	protected Handler mHandler;
	private HandlerThread mHandlerThread;
	private NotificationManager mNotificationManager;
	private Handler mUIHandler;

	private boolean mStarted;

	public class LocalBinder extends Binder {
		public void setAudioEventListener(AudioEventListener listener) {
			mAudioEventListener = listener;
		}

		public void stopService() {
			stopSelf();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mUIHandler = new Handler();

		mHandlerThread = new HandlerThread("Audio Background Handler");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());

		// setupMediaPlayer();
		Log.v(TAG, "hello audio service");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		Log.v(TAG, "starting ---");
		if (!mStarted) {
			PendingIntent p = PendingIntent
					.getActivity(this, INTENT_ACTIVITY_ID, new Intent(this,
							MainActivity.class), 0);

			Notification notification = new NotificationCompat.Builder(this)
					.setOngoing(true).setSmallIcon(R.drawable.ic_launcher)
					.setContentIntent(p)
					.setContentTitle(getString(R.string.app_name))
					.setContentText(getString(R.string.notification_progress_text))
					.build();
			startForeground(INTENT_ONGOING_ID, notification);

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

}
