package nl.hearushere.lib.data;

import android.content.Context;
import android.media.MediaPlayer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

import org.altbeacon.beacon.Beacon;

import java.io.File;
import java.io.Serializable;

import nl.hearushere.lib.Constants;
import nl.hearushere.lib.Utils;

@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public class Track implements Serializable {

    @JsonProperty
    private boolean bluetooth;

    @JsonProperty
    private boolean background;

    @JsonProperty
    private String file;

    @JsonProperty
    private String url;

    @JsonProperty
    private double[] location;

    @JsonProperty
    private int radius;

    @JsonProperty
    private String uuid;

    @JsonProperty
    private String major;

    @JsonProperty
    private String minor;

    @JsonIgnore
    transient private MediaPlayer mediaPlayer;

    @JsonIgnore
    transient private float currentVolume;

    @JsonIgnore
    transient private double currentDistance;

    @JsonIgnore
    transient private LatLng latlng;

    public Track() {
    }

    public Track(String url) {
        this.url = url;
        System.out.println("new track: " + url);
    }

    public String getStreamUrl() {
        return url != null ? url : Constants.CONTENT_URL_PREFIX + file;
    }

    public File getCacheFile(Context context) {
        return new File(context.getCacheDir(), getHash()
                + ".mp3");
    }

    private String getHash() {
        return Utils.sha256(getStreamUrl());
    }

    public LatLng getLocationLatLng() {
        if (location == null) {
            return null;
        }
        if (latlng == null) {
            latlng = new LatLng(location[0], location[1]);
        }
        return latlng;
    }

    public double getCurrentDistance() {
        return currentDistance;
    }

    public void setCurrentDistance(double currentDistance) {
        this.currentDistance = currentDistance;
    }

    public float getCalculatedVolume(int defaultRadius) {
        if (isBackground()) {
            return 1.0f;
        }
        double radius = this.radius;
        if (radius <= 0.0) {
            radius = defaultRadius;
        }
        if (currentDistance > radius) {
            return 0f;
        }
        // Magic (c) James Bryan Graves :)
        return (float) Math.max(0.0,
                Math.min(1.0, Math.log(currentDistance / radius) * -0.5));
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public float getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume(float currentVolume) {
        this.currentVolume = currentVolume;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(currentVolume, currentVolume);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    public double getRadius() {
        return this.radius;
    }

    public boolean isBluetooth() {
        return bluetooth;
    }

    public boolean isBackground() {
        return background;
    }

    public String getFile() {
        return file;
    }

    public String getId() {
        return file;
    }

    public int getTrackHash() {
        return file.hashCode();
    }

    public String getUuid() {
        return uuid;
    }

    public String getMajor() {
        return major;
    }

    public String getMinor() {
        return minor;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getUrl() {
        return url;
    }

    public ListenerNotification getListenerNotification(Context context, Beacon lastBeacon) {
        return isBluetooth() ? new BluetoothListenerNotification(context, this, lastBeacon) : new GeoListenerNotification(context, this);
    }
}
