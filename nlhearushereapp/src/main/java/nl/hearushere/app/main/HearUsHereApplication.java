package nl.hearushere.app.main;

import android.app.Application;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Main app
 *
 * Created by Arjan Scherpenisse on 28-10-14.
 */
public class HearUsHereApplication extends Application {

    public void onCreate() {
        super.onCreate();
        CalligraphyConfig.initDefault("fonts/Relative-Faux.otf", R.attr.fontPath);
    }

}
