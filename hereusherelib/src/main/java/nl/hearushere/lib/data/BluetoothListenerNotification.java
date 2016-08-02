package nl.hearushere.lib.data;

import android.content.Context;

import org.altbeacon.beacon.Beacon;

/**
 * Created by arjan on 27-10-15.
 */
public class BluetoothListenerNotification extends ListenerNotification {

    String uuid;
    int major;
    int minor;
    int rssi;

    public BluetoothListenerNotification(Context context, Track track, Beacon lastBeacon) {
        super(context, track);
        uuid = track.getUuid();
        major = Integer.parseInt(track.getMajor());
        minor = Integer.parseInt(track.getMinor());
        rssi = 0;
        if (lastBeacon != null) {
            rssi = lastBeacon.getRssi();
        }
    }
}
