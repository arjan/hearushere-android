package nl.hearushere.lib.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by Arjan Scherpenisse on 6-5-15.
 */
public class Triggers {

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
        return w;
    }

    private List<double[]> getWalkAreas() {
        List<double[]> result = new ArrayList<>();
        for (Area a : areas) {
            double[] area = new double[a.coords.size()];
            for (int i=0; i<a.coords.size(); i++) {
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
            System.out.println(loc.toString());
        }
        return list;
    }

    public static class Area {
        @JsonProperty
        public List<Double> coords;

        @JsonProperty
        public List<Url> sounds;

        @JsonProperty
        public List<Track> triggers;
    }

    public static class Url {
        @JsonProperty
        public String url;
    }
}
