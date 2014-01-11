package nl.hearushere.app;

import java.util.ArrayList;

import nl.hearushere.app.AudioService.AudioEventListener;
import nl.hearushere.app.AudioService.LocalBinder;
import nl.hearushere.app.data.Track;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;
import android.R.layout;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLoadedCallback;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLngBounds.Builder;
import com.google.android.gms.maps.model.LatLngBoundsCreator;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

public class MainActivity extends Activity implements AudioEventListener,
		OnMapClickListener {
	protected SpiceManager mSpiceManager = new SpiceManager(
			HttpSpiceService.class);
	public static final String TAG = AudioService.class.getSimpleName();

	private ServiceConnection mServiceConnection;
	protected LocalBinder mServiceInterface;

	private TextView mTvNotification;

	private GoogleMap mMap;

	private Polygon mPolygon;

	private LatLng mCenter;
	private API mAPI;
	private View mProgress;
	private Marker mDebugMarker;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mTvNotification = (TextView) findViewById(R.id.tv_notification);
		mProgress = findViewById(R.id.progress);

		Constants.SOUNDCLOUD_CLIENT_ID = getResources().getString(
				R.string.area_soundcloud_client_id);
		Constants.SOUNDCLOUD_USER_ID = getResources().getString(
				R.string.area_soundcloud_user_id);

		mAPI = new API(mSpiceManager);
	}

	private void initMap() {

		mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
				.getMap();
		mMap.setMyLocationEnabled(true);

		if (Constants.USE_DEBUG_LOCATION) {
			mMap.setOnMapClickListener(this);
		}

		Builder builder = new LatLngBounds.Builder();

		// build the polygon
		String[] source = getResources().getStringArray(R.array.audio_area);
		ArrayList<LatLng> points = new ArrayList<LatLng>();
		for (String line : source) {
			String[] m = line.split(" ");
			assert (m.length == 2);
			double lat = Double.parseDouble(m[0]);
			double lng = Double.parseDouble(m[1]);
			LatLng point = new LatLng(lat, lng);
			builder.include(point);
			points.add(point);
		}
		final LatLngBounds bounds = builder.build();

		mPolygon = mMap.addPolygon(new PolygonOptions().addAll(points)
				.strokeColor(getResources().getColor(R.color.polygon_stroke))
				.strokeWidth(3f)
				.fillColor(getResources().getColor(R.color.polygon_fill)));

		final ViewTreeObserver vto = findViewById(android.R.id.content)
				.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				//vto.removeGlobalOnLayoutListener(this);
				mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
			}
		});

	}

	@Override
	protected void onResume() {
		super.onResume();
		initMap();
		startAudioService();
		checkServicesConnected();
	}

	@Override
	protected void onStart() {
		super.onStart();
		mSpiceManager.start(this);
	}

	@Override
	protected void onStop() {
		mSpiceManager.shouldStop();
		super.onStop();
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
				loadTrackList();
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

	@Override
	public void showNotification(String text) {
		mTvNotification.setText(text);
		mTvNotification.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideNotification() {
		mTvNotification.setVisibility(View.GONE);
	}

	private void loadTrackList() {
		if (mServiceInterface.hasTracks()) {
			return;
		}

		mProgress.setVisibility(View.VISIBLE);
		mAPI.getUserTracks(Constants.SOUNDCLOUD_USER_ID,
				new RequestListener<Track.List>() {
					@Override
					public void onRequestSuccess(Track.List arg0) {
						mProgress.setVisibility(View.GONE);

						if (Constants.USE_DEBUG_LOCATION) {
							for (Track track : arg0) {
								mMap.addMarker(new MarkerOptions()
										.position(track.getLocation())
										.alpha(0.5f).title(track.getTitle()));
							}
						}

						mServiceInterface.loadTrackList(arg0);
					}

					@Override
					public void onRequestFailure(SpiceException arg0) {
						mProgress.setVisibility(View.GONE);
						new AlertDialog.Builder(MainActivity.this)
								.setCancelable(false)
								.setMessage(
										"No network connection, please try again when connected.")
								.setPositiveButton("Close",
										new OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int which) {
												dialog.dismiss();
												finish();
											}
										}).show();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_start_stop:
			mServiceInterface.stopService();
			finish();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void checkServicesConnected() {
		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d("Location Updates", "Google Play services is available.");
			// Continue
			return;
			// Google Play services was not available for some reason
		} else {
			new AlertDialog.Builder(MainActivity.this)
					.setCancelable(false)
					.setMessage(
							"Sorry, we were unable to connect to the Location Service. Please enable the location in the settings and try again.")
					.setPositiveButton("Close", new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							finish();
						}
					}).show();
		}
	}

	@Override
	public void onMapClick(LatLng arg0) {
		if (mDebugMarker != null) {
			mDebugMarker.remove();
		}
		mDebugMarker = mMap.addMarker(new MarkerOptions().position(arg0)
				.draggable(true));
		mServiceInterface.setDebugLocation(arg0);
	}
}
