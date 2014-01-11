package nl.hearushere.app.net;

import java.util.ArrayList;
import java.util.List;

import nl.hearushere.app.data.Track;
import roboguice.util.temp.Ln;
import android.app.Application;
import android.util.Log;

import com.octo.android.robospice.SpiceService;
import com.octo.android.robospice.persistence.CacheManager;
import com.octo.android.robospice.persistence.exception.CacheCreationException;
import com.octo.android.robospice.persistence.springandroid.json.jackson2.Jackson2ObjectPersisterFactory;

public class HttpSpiceService extends SpiceService {

	@Override
	public CacheManager createCacheManager(Application application)
			throws CacheCreationException {
		CacheManager cacheManager = new CacheManager();

		List<Class<?>> cacheableClasses = new ArrayList<Class<?>>();
		cacheableClasses.add(Track.List.class);

		Ln.getConfig().setLoggingLevel(Log.WARN);

		// // init
		Jackson2ObjectPersisterFactory jacksonObjectPersisterFactory = new Jackson2ObjectPersisterFactory(
				application, cacheableClasses);
		cacheManager.addPersister(jacksonObjectPersisterFactory);

		return cacheManager;
	}
}