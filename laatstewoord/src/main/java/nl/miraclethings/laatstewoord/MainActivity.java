package nl.miraclethings.laatstewoord;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolygonOptions;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import java.util.ArrayList;

import nl.hearushere.lib.Constants;
import nl.hearushere.lib.data.Triggers;
import nl.hearushere.lib.data.Walk;
import nl.hearushere.lib.net.API;
import nl.hearushere.lib.net.HttpSpiceService;

public class MainActivity extends Activity implements GoogleMap.OnMapClickListener {

    protected SpiceManager mSpiceManager = new SpiceManager(
            HttpSpiceService.class);

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private API mAPI;
    private TextView mTvNotification;
    private View mProgress;
    private Button mButton;
    private Triggers mTriggers;

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
                mButton.setVisibility(View.VISIBLE);
                //Walk walk = triggers.buildWalk();
                initMap();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
//        setUpMapIfNeeded();
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

    public void showLoader(boolean b) {
        mProgress.setVisibility(b ? View.VISIBLE : View.GONE);
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

                System.out.println("Hello");

                ArrayList<LatLng> points = mTriggers.getDisplayCoords();

                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                // draw polygons on map
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

                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80));
            }
        });
    }

    @Override
    public void onMapClick(LatLng latLng) {

    }
}
