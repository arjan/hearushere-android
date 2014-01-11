package nl.hearushere.app;

import nl.hearushere.app.AudioService.AudioEventListener;
import nl.hearushere.app.AudioService.LocalBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class MainActivity extends Activity implements AudioEventListener {
	public static final String TAG = AudioService.class.getSimpleName();

	private ServiceConnection mServiceConnection;
	protected LocalBinder mServiceInterface;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "hello");
		startAudioService();
	}

	private void startAudioService() {
		Intent service = new Intent(this, AudioService.class);
		startService(service);

		mServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mServiceInterface = (AudioService.LocalBinder) service;
				Log.v(TAG, "Bound to service!");
				mServiceInterface.setAudioEventListener(MainActivity.this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				mServiceInterface = null;
				Log.v(TAG, "Disconnected from service!");
			}
		};

		bindService(service, mServiceConnection, 0);
	}

	@Override
	protected void onDestroy() {
		if (mServiceConnection != null) {
			unbindService(mServiceConnection);
			if (mServiceInterface != null) {
				mServiceInterface.setAudioEventListener(null);
			}
		}
		super.onDestroy();
	}

}
