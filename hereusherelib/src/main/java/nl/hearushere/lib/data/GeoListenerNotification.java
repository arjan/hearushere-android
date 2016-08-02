package nl.hearushere.lib.data;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by arjan on 27-10-15.
 */
public class GeoListenerNotification extends ListenerNotification {

    double latitude;
    double longitude;

    public GeoListenerNotification(Context context, Track track) {
        super(context, track);

        LatLng loc = track.getLocationLatLng();

        if (loc != null) {
            latitude = loc.latitude;
            longitude = loc.longitude;
        }
    }
}
