package nl.hearushere.app;

import java.util.ArrayList;

import nl.hearushere.app.AudioWalkService.AudioEventListener;
import nl.hearushere.app.AudioWalkService.LocalBinder;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.data.Walk.List;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

public class MainActivity extends Activity implements AudioEventListener,
		OnMapClickListener {
	protected SpiceManager mSpiceManager = new SpiceManager(
			HttpSpiceService.class);
	public static final String TAG = AudioWalkService.class.getSimpleName();

	private ServiceConnection mServiceConnection;
	protected LocalBinder mServiceInterface;

	private TextView mTvNotification;

	private GoogleMap mMap;

	private API mAPI;
	private View mProgress;
	private Marker mDebugMarker;
	private Walk.List mWalks;
	private Button mButton;
	private ViewPager mViewPager;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mTvNotification = (TextView) findViewById(R.id.tv_notification);
		mProgress = findViewById(R.id.progress);

		Constants.SOUNDCLOUD_CLIENT_ID = getResources().getString(
				R.string.area_soundcloud_client_id);

		mAPI = new API(mSpiceManager);

		showLoader(true);
		mAPI.getWalks(new RequestListener<Walk.List>() {

			@Override
			public void onRequestFailure(SpiceException arg0) {
				showLoader(false);
				showNetworkErrorMessage();
			}

			@Override
			public void onRequestSuccess(List arg0) {
				showLoader(false);
				mWalks = arg0;
				System.out.println("woot");
				initPager();
			}
		});

	}

	@Override
	public void showLoader(boolean b) {
		mProgress.setVisibility(b ? View.VISIBLE : View.GONE);
	}

	private void initMap(final Walk walk) {

		mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
				.getMap();
		mMap.setMyLocationEnabled(true);
		mMap.clear();

		if (Constants.USE_DEBUG_LOCATION) {
			mMap.setOnMapClickListener(this);
		}

		mMap.setOnMapLoadedCallback(new OnMapLoadedCallback() {
			@Override
			public void onMapLoaded() {

				Builder builder = new LatLngBounds.Builder();

				ArrayList<LatLng> points = walk.getPoints();
				for (LatLng point : points) {
					builder.include(point);
				}
				final LatLngBounds bounds = builder.build();

				mMap.addPolygon(new PolygonOptions()
						.addAll(points)
						.strokeColor(
								getResources().getColor(R.color.polygon_stroke))
						.strokeWidth(3f)
						.fillColor(
								getResources().getColor(R.color.polygon_fill)));

				mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		connectAudioService();
		checkServicesConnected();
	}

	protected void initPager() {
		mViewPager = (ViewPager) findViewById(R.id.vp_walks);
		mViewPager.setAdapter(new WalksPagerAdapter());
		mButton = (Button)findViewById(R.id.button_start_stop);
		
		walkSelected(0);
		
		mViewPager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageSelected(int arg0) {
				walkSelected(arg0);
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageScrollStateChanged(int arg0) {
			}
		});
	}

	protected void walkSelected(int arg0) {
		Walk walk = mWalks.get(arg0);
		initMap(walk);
		
		Walk current = mServiceInterface.getCurrentWalk();
		if (current == null || !walk.getTitle().equals(current.getTitle())) {
			mButton.setText("START");
		} else {
			mButton.setText("STOP");
		}
	}
	
	public void clickStartStop(View v) {
		Walk walk = mWalks.get(mViewPager.getCurrentItem());
		Walk current = mServiceInterface.getCurrentWalk();
		if (current == null || !walk.getTitle().equals(current.getTitle())) {
			mServiceInterface.startPlayback(walk);
		} else {
			mServiceInterface.stopPlayback();
		}
		walkSelected(mViewPager.getCurrentItem());
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

	private void connectAudioService() {
		Intent service = new Intent(this, AudioWalkService.class);
		startService(service);

		mServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mServiceInterface = (AudioWalkService.LocalBinder) service;
				Log.v(TAG, "Bound to service!");
				mServiceInterface.setAudioEventListener(MainActivity.this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				mServiceInterface = null;
				Log.v(TAG, "Disconnected from service!");
			}
		};

		bindService(service, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onPause() {
		if (mServiceConnection != null) {
			if (mServiceInterface != null) {
				mServiceInterface.setAudioEventListener(null);
			}
			unbindService(mServiceConnection);
		}
		super.onPause();
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
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
			// Log.d("Location Updates", "Google Play services is available.");
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

	@Override
	public void showNetworkErrorMessage() {
		mProgress.setVisibility(View.GONE);
		new AlertDialog.Builder(MainActivity.this)
				.setCancelable(false)
				.setMessage(
						"No network connection, please try again when connected.")
				.setPositiveButton("Close", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
					}
				}).show();
	}

	class WalksPagerAdapter extends PagerAdapter {

		@Override
		public int getCount() {
			return mWalks.size();
		}

		public Walk getItem(int currentItem) {
			return mWalks.get(currentItem);
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {

			View root = View.inflate(MainActivity.this,
					R.layout.view_item_walk, null);

			final Walk walk = mWalks.get(position);
			((TextView) root.findViewById(R.id.tv_item_title)).setText(walk
					.getTitle());
			Log.v(TAG, walk.getTitle());

			root.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

				}
			});

			container.addView(root);
			return root;
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == ((View) arg1);
		}

		@Override
		public int getItemPosition(Object object) {
			return POSITION_NONE;
		}

		@Override
		public void destroyItem(View arg0, int arg1, Object arg2) {
			((ViewPager) arg0).removeView((View) arg2);
		}

	}
}
