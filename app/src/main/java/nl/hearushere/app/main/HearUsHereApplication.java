package nl.hearushere.app.main;

import android.app.Application;

import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Main app
 *
 * Created by Arjan Scherpenisse on 28-10-14.
 */
public class HearUsHereApplication extends Application {

    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        CalligraphyConfig.initDefault("fonts/Relative-Faux.otf", R.attr.fontPath);
    }

}
