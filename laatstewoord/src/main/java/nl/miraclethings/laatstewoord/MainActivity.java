package nl.miraclethings.laatstewoord;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.hearushere.lib.AudioWalkService;
import nl.hearushere.lib.Constants;
import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Triggers;
import nl.hearushere.lib.net.API;
import nl.hearushere.lib.net.HttpSpiceService;

public class MainActivity extends Activity implements GoogleMap.OnMapClickListener, AudioWalkService.AudioEventListener {

    private static final String TAG = "MainActivity";
    protected SpiceManager mSpiceManager = new SpiceManager(
            HttpSpiceService.class);

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private API mAPI;
    private TextView mTvNotification;
    private View mProgress;
    private Button mButton;
    private Triggers mTriggers;
    private ServiceConnection mServiceConnection;
    private LaatsteWoordService.LocalBinder mServiceInterface;
    private Marker mDebugMarker;
    private boolean hasMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //setUpMapIfNeeded();

        mTvNotification = (TextView) findViewById(R.id.tv_notification);
        mProgress = findViewById(R.id.progress);

        mAPI = new API(mSpiceManager);

        mButton = (Button) findViewById(R.id.button_start_stop);
        mButton.setVisibility(View.GONE);

        showLoader(true);
        mAPI.getLaatsteWoordTriggers(new RequestListener<Triggers>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                showLoader(false);
                Toast.makeText(MainActivity.this, "Failed to load the tracks", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRequestSuccess(Triggers triggers) {
                showLoader(false);

                System.out.println("size: " + triggers.areas.size());

                mTriggers = triggers;
                //Walk walk = triggers.buildWalk();
                initMap();

                connectAudioService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServicesConnected();
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
            mServiceConnection = null;
        }

        super.onPause();
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
    public void showNotification(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void hideNotification() {

    }

    @Override
    public void showNetworkErrorMessage() {
        Toast.makeText(this, "Network error...", Toast.LENGTH_SHORT).show();

    }

    public void showLoader(boolean b) {
        mProgress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    @Override
    public void uiUpdate() {
        if (mServiceInterface == null) {
            mButton.setVisibility(View.GONE);
            return;
        }

        System.out.println("UI UPDATE");

        if (!hasMap && mServiceInterface.getTriggers() != null) {
            hasMap = true;
        }
        mMap.clear();

        if (mServiceInterface.getCurrentWalk() != null) {
            addMapMarkers(mServiceInterface.getCurrentWalk().getSounds());
        }

        if (mServiceInterface.getTriggers() != null) {
            addMapTriggers(mServiceInterface.getTriggers());
        }

        if (mServiceInterface.getLastLocation() != null) {
            mDebugMarker = mMap.addMarker(new MarkerOptions().position(mServiceInterface.getLastLocation())
                    .draggable(false));
        }
    }


    private MapFragment getMapFragment() {
        return (MapFragment) getFragmentManager().findFragmentById(R.id.map);
    }

    private void initMap() {

        mMap = getMapFragment().getMap();
        mMap.setMyLocationEnabled(true);
        mMap.clear();

        if (Constants.USE_DEBUG_LOCATION) {
            mMap.setOnMapClickListener(this);
        }

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {

                ArrayList<LatLng> points = mTriggers.getDisplayCoords();

                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                // draw polygons on map
                for (LatLng point : points) {
                    builder.include(point);
                }

                mMap.addPolygon(new PolygonOptions()
                        .addAll(points)
                        .strokeWidth(0f)
                        .fillColor(
                                getResources().getColor(R.color.polygon_fill)));

                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80));
            }
        });
    }

    @Override
    public void onMapClick(LatLng latlng) {
        mServiceInterface.setDebugLocation(latlng);

        if (mDebugMarker != null) {
            mDebugMarker.setPosition(latlng);
        } else {
            mDebugMarker = mMap.addMarker(new MarkerOptions().position(mServiceInterface.getLastLocation())
                    .draggable(false));
        }

    }

    private void checkServicesConnected() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS != resultCode) {
            new AlertDialog.Builder(MainActivity.this)
                    .setCancelable(false)
                    .setMessage(
                            "Sorry, we were unable to connect to the Location Service. Please enable the location in the settings and try again.")
                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            finish();
                        }
                    }).show();
        }
    }

    private void connectAudioService() {
        Intent service = new Intent(this, LaatsteWoordService.class);
        service.putExtra("triggers", mTriggers);
        startService(service);

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceInterface = (LaatsteWoordService.LocalBinder) service;
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

    private void addMapMarkers(List<Track> sounds) {
        for (Track sound : sounds) {
            addTriggerMarker(sound, R.color.black);
        }
    }

    private void addTriggerMarker(Track sound, int color) {
        mMap.addCircle(new CircleOptions()
                .radius(1f)
                .center(sound.getLocationLatLng())
                .strokeColor(getResources().getColor(color))
                .strokeWidth(6f));

        mMap.addCircle(new CircleOptions()
                .radius(sound.getRadius())
                .center(sound.getLocationLatLng())
                .strokeColor(getResources().getColor(color))
                .strokeWidth(6f));
    }

    private void addMapTriggers(Triggers triggers) {
        for (Triggers.Area area : triggers.areas) {
            mMap.addPolygon(new PolygonOptions()
                    .addAll(area.getCoords())
                    .strokeWidth(4f)
                    .strokeColor(getResources().getColor(R.color.red)));

            for (Track trigger : area.triggers) {
                if (trigger.getUrl() != null) {
                    continue;
                }
                addTriggerMarker(trigger, R.color.red);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
            //menu.findItem(R.id.menu_credits).setVisible(true);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.menu_credits:
//                openWalkCredits(mWalks.get(0));
//                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
