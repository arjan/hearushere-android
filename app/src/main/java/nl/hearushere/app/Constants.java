package nl.hearushere.app;

import nl.hearushere.app.main.BuildConfig;

public class Constants {

	public static final boolean USE_DEBUG_LOCATION = BuildConfig.DEBUG;
	
	public static final String API_URL_PREFIX = "http://api.hearushere.nl/";
	
	public static final String CACHE_DIR = "hearushere";
    public static final String CONTENT_URL_PREFIX = "http://hearushere.nl/walks/";

    public static int MAX_SIMULTANEOUS_SOUNDS = 10;

	public static int FADE_STEP = 100; // ms per fade step
	public static int FADE_TIME = 3000; // ms per fade step
	

}
