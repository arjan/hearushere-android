package nl.hearushere.app;

import android.app.Activity;

import nl.hearushere.app.main.R;

/**
 * The main Hear us here audio walk service
 *
 * Created by Arjan Scherpenisse on 6-5-15.
 */
public class AudioWalkService extends nl.hearushere.lib.AudioWalkService {
    @Override
    public int getStatIcon() {
        return R.drawable.ic_stat_huh;
    }

    @Override
    public int getAppIcon() {
        return R.drawable.ic_launcher;
    }

    @Override
    public int getAppName() {
        return R.string.app_name;
    }

    @Override
    public Class<? extends Activity> getMainActivity() {
        return MainActivity.class;
    }
}
