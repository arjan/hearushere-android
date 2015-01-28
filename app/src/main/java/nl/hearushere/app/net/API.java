package nl.hearushere.app.net;

import nl.hearushere.app.Constants;
import nl.hearushere.app.Utils;
import nl.hearushere.app.data.Walk;
import nl.hearushere.app.data.Walk.List;

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
                        System.out.println(" -- GET WALKS --");
                        JsonNode node = HttpRequest.doRequest("GET",
								Constants.API_URL_PREFIX + "walks", null);
						return mMapper.treeToValue(node, Walk.List.class);
					}
		}, "walks", DurationInMillis.ONE_WEEK, listener);
	}

	public void clearCache() {
		mSpiceManager.removeAllDataFromCache();
	}

}
