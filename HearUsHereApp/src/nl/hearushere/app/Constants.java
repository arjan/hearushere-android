package nl.hearushere.app;

public class Constants {

	public static final boolean USE_DEBUG_LOCATION = true;
	
	public static boolean TRACKS_ARE_SYNCHRONIZED = true; // whether the tracks start at the same seek time
	
	public static boolean USE_HARDCODED_WALK = true;
	
	public static final String SOUNDCLOUD_API_BASE_URL = "http://api.soundcloud.com/";
	public static final String HEARUSHERE_BASE_URL = "http://www.hearushere.nl/";
	
	public static final String CACHE_DIR = "hearushere";
	
	public static final double MAX_SOUND_DISTANCE = 100.0;
	public static int MAX_SIMULTANEOUS_SOUNDS = 4;

	public static String SOUNDCLOUD_CLIENT_ID;
	
	public static int FADE_STEP = 100; // ms per fade step
	public static int FADE_TIME = 3000; // ms per fade step
	

}
