package nl.hearushere.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.widget.ImageView;
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
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;
import com.viewpagerindicator.PageIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.hearushere.app.AudioWalkService.AudioEventListener;
import nl.hearushere.app.AudioWalkService.LocalBinder;
import nl.hearushere.app.data.Track;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.main.R;
import nl.hearushere.app.net.API;
import nl.hearushere.app.net.HttpSpiceService;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

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
	private List<Walk> mWalks;
	private Button mButton;
	private ViewPager mViewPager;
	private LocationManager mLocationManager;
	private boolean mIsUniversal;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ImageLoader.getInstance().init(
				new ImageLoaderConfiguration.Builder(this).build());

		mTvNotification = (TextView) findViewById(R.id.tv_notification);
		mProgress = findViewById(R.id.progress);

		mLocationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);

		mAPI = new API(mSpiceManager);

		findViewById(R.id.fl_walk_info).setVisibility(View.GONE);

		mIsUniversal = getResources().getBoolean(R.bool.is_universal);
		showLoader(true);
		mAPI.getWalks(new RequestListener<Walk.List>() {

			@Override
			public void onRequestFailure(SpiceException arg0) {
				showLoader(false);
				showNetworkErrorMessage();
			}

			@Override
			public void onRequestSuccess(Walk.List walks) {
				showLoader(false);

                if (mIsUniversal) {
                    mWalks = walks;
                } else {
                    mWalks = Arrays.asList(walks.findById(getString(R.string.fixed_walk_id)));
                }
                waitForLocation();
			}
		});
	}

    private void waitForLocation() {
        Location loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            initPager(loc);
        } else {

            Criteria criteria = new Criteria();
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setBearingAccuracy(Criteria.ACCURACY_LOW);
            criteria.setSpeedAccuracy(Criteria.ACCURACY_MEDIUM);

            showNotification("Waiting on GPS signal.");

            mLocationManager.requestSingleUpdate(criteria, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    hideNotification();
                    initPager(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            }, null);

        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
    }

    @Override
	public void showLoader(boolean b) {
		mProgress.setVisibility(b ? View.VISIBLE : View.GONE);
	}

	private Walk mLastMapWalk = null;

	private void initMap(final Walk walk) {

		if (mLastMapWalk == walk) {
			return;
		}
		mLastMapWalk = walk; 

		mMap = getMapFragment().getMap();
		mMap.setMyLocationEnabled(true);
		mMap.clear();

		if (Constants.USE_DEBUG_LOCATION) {
			mMap.setOnMapClickListener(this);
		}

		mMap.setOnMapLoadedCallback(new OnMapLoadedCallback() {
			@Override
			public void onMapLoaded() {

                ArrayList<ArrayList<LatLng>> pointsList = walk.getPoints();

                Builder builder = new LatLngBounds.Builder();

                // draw polygons on map
                for (ArrayList<LatLng> points : pointsList) {
                    for (LatLng point : points) {
                        builder.include(point);
                    }

                    mMap.addPolygon(new PolygonOptions()
                            .addAll(points)
                            .strokeColor(
                                    getResources().getColor(R.color.polygon_stroke))
                            .strokeWidth(8f)
                            .fillColor(
                                    getResources().getColor(R.color.polygon_fill)));
                }

				mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80));
			}
		});
	}

	private MapFragment getMapFragment() {
		return (MapFragment) getFragmentManager().findFragmentById(R.id.map);
	}

	@Override
	protected void onResume() {
		super.onResume();
		connectAudioService();
		checkServicesConnected();
	}

	protected void initPager(Location loc) {

        System.out.println("-- init pager--");
        System.out.println("-- " + loc.getLatitude() + " " + loc.getLongitude());
        sortWalksByClosestDistance(new LatLng(loc.getLatitude(), loc.getLongitude()));

        View container = findViewById(R.id.fl_walk_info);
		mViewPager = (ViewPager) findViewById(R.id.vp_walks);

		if (mWalks.size() == 1) {
			container.setVisibility(View.GONE);

		} else {

			mViewPager.setAdapter(new WalksPagerAdapter());
			container.setVisibility(View.VISIBLE);

			PageIndicator indicator = (PageIndicator) findViewById(R.id.vp_indicator);
			indicator.setViewPager(mViewPager);

			indicator.setOnPageChangeListener(new OnPageChangeListener() {

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
		mButton = (Button) findViewById(R.id.button_start_stop);

		invalidateOptionsMenu();
		walkSelected(0);
	}

	protected void walkSelected(int arg0) {
		Walk walk = mWalks.get(arg0);
		initMap(walk);

		Walk current = mServiceInterface.getCurrentWalk();
		if (current == null || !walk.getTitle().equals(current.getTitle())) {
            getActionBar().setTitle(walk.getTitle());
			mButton.setText("START");
		} else {
            getActionBar().setTitle(getString(R.string.app_name));
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
	}

    @Override
    public void uiUpdate() {
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		if (!mIsUniversal) {
			menu.findItem(R.id.menu_credits).setVisible(true);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_credits:
			openWalkCredits(mWalks.get(0));
			return true;
		}
		return super.onOptionsItemSelected(item);
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

				if (mWalks != null && mViewPager == null) {
					waitForLocation();
				}
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
				if (mServiceInterface.getCurrentWalk() == null) {
					mServiceInterface.stopService();
				}
			}
			unbindService(mServiceConnection);
		}
		super.onPause();
	}

	@Override
	public void showNotification(String text) {
		mTvNotification.setText(text);
		mTvNotification.animate().alpha(1f).setDuration(500).start();
	}

	@Override
	public void hideNotification() {
		mTvNotification.animate().alpha(0f).setDuration(500).start();
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

	protected void openWalkCredits(Walk walk) {
		if (walk == null || walk.getCredits() == null) {
			return;
		}
		Intent intent = new Intent(this, CreditsActivity.class);
		intent.putExtra("credits", Utils.serialize(walk, Walk.class));
		startActivity(intent);
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

			((TextView) root.findViewById(R.id.tv_item_distance))
					.setText(walk.getFormattedDistance());

			Log.v(TAG, walk.getTitle());

            if (walk.getImageUrl() != null) {
                ImageLoader.getInstance().displayImage(
                        walk.getImageUrl(),
                        (ImageView) root.findViewById(R.id.iv_item_logo));
            }

			root.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openWalkCredits(walk);
				}
			});

			container.addView(root);
			return root;
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == arg1;
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

    public void sortWalksByClosestDistance(final LatLng loc) {
        float[] results = new float[3];

        for (Walk walks: mWalks) {
            LatLng c = walks.getCenter();
            Location.distanceBetween(loc.latitude, loc.longitude,
                    c.latitude, c.longitude, results);
            double distance = results[0];
            walks.setCurrentDistance(distance);
        }

        Collections.sort(mWalks, new Comparator<Walk>() {
            @Override
            public int compare(Walk lhs, Walk rhs) {
                return (int) (lhs.getCurrentDistance() - rhs
                        .getCurrentDistance());
            }
        });
    }

}
