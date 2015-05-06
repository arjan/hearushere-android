package nl.hearushere.lib;

import android.content.Context;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Parcelable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;

import nl.hearushere.lib.data.Triggers;

public class Utils {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static ObjectMapper getObjectMapper() {
        return mapper;
    }

    public static String serialize(Object value, Class<?> clazz) {
        if (value == null)
            return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static <T> T deserialize(String value, Class<T> clazz) {
        try {
            return mapper.readValue(value, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final int LOCATION_TIME_DELTA = 1000 * 30;


    public static boolean isInsidePolygon(LatLng location, ArrayList<LatLng> polyLoc) {
        LatLng lastPoint = polyLoc.get(polyLoc.size() - 1);
        boolean isInside = false;
        double x = location.longitude;

        for (LatLng point : polyLoc) {
            double x1 = lastPoint.longitude;
            double x2 = point.longitude;
            double dx = x2 - x1;

            if (Math.abs(dx) > 180.0) {
                // we have, most likely, just jumped the dateline (could do
                // further validation to this effect if needed). normalise the
                // numbers.
                if (x > 0) {
                    while (x1 < 0)
                        x1 += 360;
                    while (x2 < 0)
                        x2 += 360;
                } else {
                    while (x1 > 0)
                        x1 -= 360;
                    while (x2 > 0)
                        x2 -= 360;
                }
                dx = x2 - x1;
            }

            if ((x1 <= x && x2 > x) || (x1 >= x && x2 < x)) {
                double grad = (point.latitude - lastPoint.latitude) / dx;
                double intersectAtLat = lastPoint.latitude + ((x - x1) * grad);

                if (intersectAtLat > location.latitude)
                    isInside = !isInside;
            }
            lastPoint = point;
        }

        if (isInside) return true;
        return false;
    }

    /**
     * Determines whether one Location reading is better than the current
     * Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new
     *                            one
     */
    protected static boolean isBetterLocation(Location location,
                                              Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > LOCATION_TIME_DELTA;
        boolean isSignificantlyOlder = timeDelta < -LOCATION_TIME_DELTA;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use
        // the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be
            // worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
                .getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Determine location quality using a combination of timeliness and
        // accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate) {
            return true;
        }
        return false;
    }

    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static double measureGeoDistance(LatLng a, LatLng b) {
        float[] results = new float[3];
        Location.distanceBetween(a.latitude, a.longitude,
                b.latitude, b.longitude, results);
        return results[0];
    }

    public static MediaPlayer playSoundOnce(Context context, Triggers.Url url, MediaPlayer.OnCompletionListener onDone) {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(context, Uri.fromFile(url.getCacheFile(context)));
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.prepare();
            if (onDone != null) {
                player.setOnCompletionListener(onDone);
            }
            player.setLooping(false);
            player.setVolume(1.0f, 1.0f);
            player.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return player;
    }
}
