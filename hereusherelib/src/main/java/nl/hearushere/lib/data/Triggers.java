package nl.hearushere.lib.data;

import android.content.Context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import nl.hearushere.lib.Utils;

/**
 * Created by Arjan Scherpenisse on 6-5-15.
 */
public class Triggers implements Serializable {

    @JsonProperty
    public List<Double> displayCoords;

    @JsonProperty
    public List<Area> areas;

    @JsonProperty
    public Url firstSound;

    @JsonProperty
    public Url lastSound;

    @JsonProperty
    public Url outOfBoundsSound;

    public Walk buildWalk() {
        Walk w = new Walk();
        w.setTitle("Laatste woord");
        w.setAreas(getWalkAreas());
        w.setSounds(getHearUsHereTracks());
        w.setDisplayPoints(Arrays.asList(getDisplayCoords()));
        return w;
    }

    private List<double[]> getWalkAreas() {
        List<double[]> result = new ArrayList<>();
        for (Area a : areas) {
            double[] area = new double[a.coords.size()];
            for (int i = 0; i < a.coords.size(); i++) {
                area[i] = a.coords.get(i);
            }
            result.add(area);
        }
        return result;
    }

    public ArrayList<LatLng> getDisplayCoords() {

        ArrayList<LatLng> list = new ArrayList<>();
        for (int i = 0; i < displayCoords.size(); i += 2) {
            LatLng loc = new LatLng(displayCoords.get(i), displayCoords.get(i + 1));
            list.add(loc);
        }
        return list;
    }

    public List<Track> getSoundTracks() {
        ArrayList<Track> all = new ArrayList<>();
        for (Area area : areas) {
            for (Track trigger : area.triggers) {
                if (trigger.getUrl() != null) {
                    all.add(trigger);
                }
            }
            for (Url url : area.sounds) {
                if (url.url != null) {
                    all.add(new Track(url.url));
                }
            }
        }
        all.add(new Track(firstSound.url));
        all.add(new Track(lastSound.url));
        all.add(new Track(outOfBoundsSound.url));

        System.out.println("Sounds to load: " + all.size());
        return all;
    }

    public List<Track> getHearUsHereTracks() {
        ArrayList<Track> all = new ArrayList<>();
        for (Area area : areas) {
            for (Track trigger : area.triggers) {
                if (trigger.getUrl() != null) {
                    all.add(trigger);
                }
            }
        }
        return all;
    }

    public static class Area implements Serializable {
        @JsonProperty
        public List<Double> coords;

        @JsonProperty
        public List<Url> sounds;

        @JsonProperty
        public List<Track> triggers;

        public ArrayList<LatLng> getCoords() {
            ArrayList<LatLng> list = new ArrayList<>();
            for (int i = 0; i < coords.size(); i += 2) {
                LatLng loc = new LatLng(coords.get(i), coords.get(i + 1));
                list.add(loc);
            }
            return list;
        }

        public Track getClosestTrigger(LatLng location) {
            Track candidate = null;
            double mind = Double.MAX_VALUE;

            for (Track trigger : triggers) {
                if (trigger.getUrl() != null) {
                    continue;
                }

                double d = Utils.measureGeoDistance(trigger.getLocationLatLng(), location);
                if (d > trigger.getRadius()) {
                    continue;
                }

                if (candidate == null || d <= mind) {
                    mind = d;
                    candidate = trigger;
                }
            }
            return candidate;
        }
    }

    public static class Url implements Serializable {
        @JsonProperty
        public String url;

        public File getCacheFile(Context context) {
            return new File(context.getCacheDir(), getHash()
                    + ".mp3");
        }

        private String getHash() {
            return Utils.sha256(url);
        }

    }
}
