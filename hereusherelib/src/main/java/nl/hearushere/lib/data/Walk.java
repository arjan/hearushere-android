package nl.hearushere.lib.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

import nl.hearushere.lib.Constants;

@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public class Walk {

    private java.util.List<ArrayList<LatLng>> mDisplayPoints;

    public static class List extends ArrayList<Walk> {

        private static final long serialVersionUID = 1L;

        public Walk findById(String id) {
            for (Walk w : this) {
                if (id.equals(w.getId())) {
                    return w;
                }
            }
            throw new RuntimeException("Walk not found: " + id);
        }

        public Walk findByTitle(String title) {
            for (Walk w : this) {
                if (title.equals(w.getTitle())) {
                    return w;
                }
            }
            throw new RuntimeException("Walk not found (by title): " + title);
        }

    }

    @JsonProperty("_id")
    private String id;

    @JsonProperty
    private java.util.List<double[]> areas;

    @JsonProperty
    private String title;

    @JsonProperty
    private int radius;

    @JsonProperty
    private String image;

    @JsonProperty
    private String credits;

    @JsonProperty
    private String description;

    @JsonProperty("autoplay")
    private boolean tracksSynchronized;

    @JsonProperty
    private java.util.List<Track> sounds;

    private transient LatLng mCenter;

    private transient double currentDistance = - 1;

    public Walk() {
    }

    public java.util.List<ArrayList<LatLng>> getDisplayPoints() {
        if (mDisplayPoints != null) {
            return mDisplayPoints;
        }
        return getPoints();
    }

    public java.util.List<ArrayList<LatLng>> getPoints() {
        ArrayList<ArrayList<LatLng>> result = new ArrayList<>();
        for (double[] area : areas) {
            ArrayList<LatLng> list = new ArrayList<>();
            for (int i = 0; i < area.length; i += 2) {
                list.add(new LatLng(area[i], area[i + 1]));
            }
            result.add(list);
        }
        return result;
    }

    public String getImageUrl() {
        if (image == null) {
            return null;
        }
        return Constants.CONTENT_URL_PREFIX + image;
    }

    public String getTitle() {
        return title;
    }

    public String getImage() {
        return image;
    }

    public String getCredits() {
        return credits;
    }

    public String getCreditsUrl() {
        if (credits == null) {
            return null;
        }
        return Constants.CONTENT_URL_PREFIX + credits.replace("walks/", "");
    }

    public String getDescription() {
        return description;
    }

    public boolean areTracksSynchronized() {
        return tracksSynchronized;
    }

    public void setTracksSynchronized(boolean tracksSynchronized) {
        this.tracksSynchronized = tracksSynchronized;
    }

    public String getFormattedDistance() {

        if (currentDistance < 0) {
            return "";
        }
        if (currentDistance > 1000) {
            return String.format("%.2f km", currentDistance / 1000);
        } else {
            return String.format("%.0f m", currentDistance);
        }
    }

    public LatLng getCenter() {
        if (mCenter == null && areas != null) {
            double minLat = 0, minLng = 0, maxLat = 0, maxLng = 0;
            boolean first = true;
            for (double[] area : areas) {
                for (int i = 0; i < area.length; i += 2) {
                    if (first) {
                        minLat = area[i];
                        maxLat = area[i];
                        minLng = area[i + 1];
                        maxLng = area[i + 1];
                        first = false;
                        continue;
                    }
                    minLat = Math.min(minLat, area[i]);
                    maxLat = Math.max(maxLat, area[i]);
                    minLng = Math.min(minLng, area[i + 1]);
                    maxLng = Math.max(maxLng, area[i + 1]);
                }
            }
            mCenter = new LatLng((maxLat + minLat) / 2, (maxLng + minLng) / 2);

            System.out.println("-- Center " + mCenter.latitude + " " + mCenter.longitude);
        }
        return mCenter;
    }

    public int getRadius() {
        return radius;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public java.util.List<Track> getSounds() {
        return sounds;
    }

    public boolean hasBluetooth() {
        for (Track s : sounds) {
            if (s.isBluetooth()) return true;
        }
        return false;
    }

    public java.util.List<Track> getBluetoothTracks() {
        java.util.List<Track> r = new ArrayList<>();
        for (Track s : sounds) {
            if (s.isBluetooth()) {
                r.add(s);
            }
        }
        return r;
    }

    public double getCurrentDistance() {
        return currentDistance;
    }

    public void setCurrentDistance(double currentDistance) {
        this.currentDistance = currentDistance;
    }

    public java.util.List<double[]> getAreas() {
        return areas;
    }

    public void setAreas(java.util.List<double[]> areas) {
        this.areas = areas;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isTracksSynchronized() {
        return tracksSynchronized;
    }

    public void setSounds(java.util.List<Track> sounds) {
        this.sounds = sounds;
    }

    public LatLng getmCenter() {
        return mCenter;
    }

    public void setmCenter(LatLng mCenter) {
        this.mCenter = mCenter;
    }

    public void setDisplayPoints(java.util.List<ArrayList<LatLng>> mDisplayPoints) {
        this.mDisplayPoints = mDisplayPoints;
    }
}
