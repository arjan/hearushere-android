package nl.hearushere.app.net;

import nl.hearushere.app.Constants;
import nl.hearushere.app.data.Track;

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

		mMapper = new ObjectMapper();
		mMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public void getUserTracks(final String userId,
			RequestListener<Track.List> requestListener) {
		mSpiceManager.execute(new SpiceRequest<Track.List>(
				Track.List.class) {

			@Override
			public Track.List loadDataFromNetwork() throws Exception {
				JsonNode node = HttpRequest.doRequest("GET",
						"users/" + userId + "/tracks.json?offset=0&limit=250&client_id=" + Constants.SOUNDCLOUD_CLIENT_ID, null);
				return mMapper.treeToValue(node, Track.List.class);
			}

		}, "tracks", DurationInMillis.ONE_WEEK, requestListener);
	}

}
