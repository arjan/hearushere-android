package nl.hearushere.app.data;

import java.io.File;
import java.util.ArrayList;

import nl.hearushere.app.Constants;
import nl.hearushere.app.Utils;
import android.content.Context;
import android.media.MediaPlayer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
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
	private MediaPlayer mediaPlayer;

	@JsonIgnore
	private float currentVolume;

	@JsonIgnore
	private double currentDistance;

	public String getTagList() {
		return tagList;
	}

	public void setTagList(String tagList) {
		this.tagList = tagList;
	}

	public String getStreamUrl() {
		return streamUrl;
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
		if (location == null) {
			// geo:lat=52.374351 geo:lon=4.852556
			String[] parts = tagList.split(" ");
			if (parts.length == 2) {
				double lat = Double.parseDouble(parts[0].split("=")[1]);
				double lng = Double.parseDouble(parts[1].split("=")[1]);
				location = new LatLng(lat, lng);
			}
		}
		return location;
	}

	public double getCurrentDistance() {
		return currentDistance;
	}

	public void setCurrentDistance(double currentDistance) {
		this.currentDistance = currentDistance;
	}

	public float getCalculatedVolume() {
		if (currentDistance > Constants.MAX_SOUND_DISTANCE) {
			return 0f;
		}
		// Magic (c) James Bryan Graves :)
		return (float) Math
				.max(0.0,
						Math.min(
								1.0,
								Math.log(currentDistance
										/ Constants.MAX_SOUND_DISTANCE)
										* -0.5));
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
}
