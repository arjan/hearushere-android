package nl.hearushere.app.data;

import java.util.ArrayList;

import android.content.Context;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public class Walk {

	public static class List extends ArrayList<Walk> {

		private static final long serialVersionUID = 1L;

	}

	@JsonProperty
	private String title;

	@JsonProperty
	private String scUser;

	// [lat, lng, lat, lng, ..]
	@JsonProperty
	private double[] area;

	// [lat, lng]
	@JsonProperty
	private double[] location;

	@JsonProperty
	private String image;
	
	@JsonProperty
	private String credits;
	
	@JsonProperty
	private String description;
	
	public static Walk create(Context context, String title, String scUser, int areaRes) {
		Walk walk = new Walk();
		walk.title = title;
		walk.scUser = scUser;

		String[] source = context.getResources().getStringArray(areaRes);
		walk.area = new double[source.length*2];
		int i=0;
		for (String line : source) {
			String[] m = line.split(" ");
			assert (m.length == 2);
			double lat = Double.parseDouble(m[0]);
			double lng = Double.parseDouble(m[1]);
			walk.area[i++] = lat;
			walk.area[i++] = lng;
		}
		return walk;
	}
	
	public ArrayList<LatLng> getPoints() {
		ArrayList<LatLng> result = new ArrayList<LatLng>();
		for (int i=0; i < area.length; i+= 2) {
			result.add(new LatLng(area[i], area[i+1]));
		}
		return result;
	}
	
	public LatLng getLocation() {
		return new LatLng(location[0], location[1]);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getScUser() {
		return scUser;
	}

	public void setScUser(String scUser) {
		this.scUser = scUser;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getCredits() {
		return credits;
	}

	public void setCredits(String credits) {
		this.credits = credits;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
