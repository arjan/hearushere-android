package nl.hearushere.lib.data;

import android.content.Context;
import android.provider.Settings;

/**
 * Created by arjan on 27-10-15.
 */
public class ListenerNotification {

    String trackId;
    double playPosition;
    double volume;
    String guid;

    public ListenerNotification(Context context, Track track) {
        guid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        volume = track.getCurrentVolume();
        playPosition = 0.0;
        trackId = track.getId();
    }
}
