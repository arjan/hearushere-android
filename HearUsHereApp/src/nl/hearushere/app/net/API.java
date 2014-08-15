package nl.hearushere.app.net;

import nl.hearushere.app.Constants;
import nl.hearushere.app.Utils;
import nl.hearushere.app.data.Track;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.data.Walk.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.request.SpiceRequest;
import com.octo.android.robospice.request.listener.RequestListener;

public class API {

	private SpiceManager mSpiceManager;
	private ObjectMapper mMapper;

	public API(SpiceManager spiceManager) {
		mSpiceManager = spiceManager;
		
		mMapper = Utils.getObjectMapper();
	}
	
	public void getWalks(RequestListener<Walk.List> listener) {
		mSpiceManager.execute(new SpiceRequest<Walk.List>(
				Walk.List.class) {
					@Override
					public List loadDataFromNetwork() throws Exception {
						JsonNode node = HttpRequest.doRequest("GET",
								Constants.HEARUSHERE_BASE_URL + "walks.json", null);
						return mMapper.treeToValue(node, Walk.List.class);
					}
		}, "walks", 10 * DurationInMillis.ONE_WEEK, listener);
	}

	public void getSoundCloudUserTracks(final String userId,
			RequestListener<Track.List> requestListener) {
		mSpiceManager.execute(new SpiceRequest<Track.List>(
				Track.List.class) {

			@Override
			public Track.List loadDataFromNetwork() throws Exception {
				String url = Constants.SOUNDCLOUD_API_BASE_URL + "users/" + userId + "/tracks.json?offset=0&limit=250&client_id=" + Constants.SOUNDCLOUD_CLIENT_ID;
				System.out.println("URL: " + url);
				JsonNode node = HttpRequest.doRequest("GET",
						url, null);
				return mMapper.treeToValue(node, Track.List.class);
			}

		}, "tracks-" + userId, 10 * DurationInMillis.ONE_MINUTE, requestListener);
	}
	
	public void clearCache() {
		mSpiceManager.removeAllDataFromCache();
	}

}
