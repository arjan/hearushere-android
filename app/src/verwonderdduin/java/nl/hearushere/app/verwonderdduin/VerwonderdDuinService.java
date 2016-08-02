package nl.hearushere.app.verwonderdduin;

import android.app.Activity;
import android.app.Service;

import nl.hearushere.app.main.R;
import nl.hearushere.lib.AudioWalkService;

/**
 * The main Hear us here audio walk service
 *
 * Created by Arjan Scherpenisse on 6-5-15.
 */
public class VerwonderdDuinService extends AudioWalkService {
    @Override
    public int getStatIcon() {
        return R.drawable.ic_stat_huh;
    }

    @Override
    public int getAppIcon() {
        return R.mipmap.ic_launcher;
    }

    @Override
    public int getAppName() {
        return R.string.app_name;
    }

    @Override
    public Class<? extends Service> getAudioService() {
        return VerwonderdDuinService.class;
    }

    @Override
    public Class<? extends Activity> getMainActivity() {
        return MainActivity.class;
    }
}
