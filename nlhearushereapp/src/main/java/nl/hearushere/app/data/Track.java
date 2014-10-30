package nl.hearushere.app.data;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;

import nl.hearushere.app.Utils;
import android.content.Context;
import android.media.MediaPlayer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public class Track {

	public static class List extends ArrayList<Track> {

		private static final long serialVersionUID = 1L;

	}

	@JsonProperty
	private int id;

	@JsonProperty
	private String title;

	@JsonProperty("tag_list")
	private String tagList;

	@JsonProperty("stream_url")
	private String streamUrl;

	@JsonIgnore
	private LatLng location;

	@JsonIgnore
	private int radius;

	@JsonIgnore
	private MediaPlayer mediaPlayer;

	@JsonIgnore
	private float currentVolume;

	@JsonIgnore
	private double currentDistance;

	public String getTagList() {
		return tagList;
	}

    public boolean isBackground() {
        return tagList != null && tagList.equals("background");
    }

	public void setTagList(String tagList) {
		this.tagList = tagList;
	}

	public String getStreamUrl() {
        try {
            return "http://148.251.184.40:8888/?mp3=" + URLEncoder.encode(streamUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return streamUrl;
        }
	}

	public void setStreamUrl(String streamUrl) {
		this.streamUrl = streamUrl;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public File getCacheFile(Context context) {
		return new File(context.getCacheDir(), Utils.stringHash(streamUrl)
				+ ".mp3");
	}

	public LatLng getLocation() {
		return location;
	}

	public double getCurrentDistance() {
		return currentDistance;
	}

	public void setCurrentDistance(double currentDistance) {
		this.currentDistance = currentDistance;
	}

	public float getCalculatedVolume(int radius) {
        if (isBackground()) {
            return 1.0f;
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

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
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
			}
		}
	}

	public void determineLocationAndRadius(int defaultRadius) {
		double lat = 0.0, lng = 0.0;
		int radius = 0;
		boolean hasLat = false, hasLng = false, hasRadius = false;

		// geo:lat=52.374351 geo:lon=4.852556 radius=444

		String[] parts = tagList.split(" ");
		for (String part : parts) {
			try {
				if (part.startsWith("geo:lat")) {
					lat = Double.parseDouble(part.split("=")[1]);
					hasLat = true;
				}
				else if (part.startsWith("geo:lon")) {
					lng = Double.parseDouble(part.split("=")[1]);
					hasLng = true;
				}
				else if (part.contains("radius")) {
					radius = Integer.parseInt(part.split("=")[1]);
					hasRadius = true;
				}
			} catch (Exception e) {
			}
		}
		if (hasLat && hasLng) {
			location = new LatLng(lat, lng);
		}
		this.radius = hasRadius ? radius : defaultRadius;
	}

	public double getRadius() {
		return this.radius;
	}
}
