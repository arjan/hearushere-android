package nl.hearushere.lib.net;

import java.util.ArrayList;
import java.util.List;

import nl.hearushere.lib.data.Track;
import nl.hearushere.lib.data.Triggers;
import nl.hearushere.lib.data.Walk;
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
		cacheableClasses.add(Track.class);
		cacheableClasses.add(Walk.List.class);
		cacheableClasses.add(Walk.class);
		cacheableClasses.add(Triggers.class);
		cacheableClasses.add(Triggers.Url.class);

		Ln.getConfig().setLoggingLevel(Log.DEBUG);

		// // init
		Jackson2ObjectPersisterFactory jacksonObjectPersisterFactory = new Jackson2ObjectPersisterFactory(
				application, cacheableClasses);
		cacheManager.addPersister(jacksonObjectPersisterFactory);

		return cacheManager;
	}
}