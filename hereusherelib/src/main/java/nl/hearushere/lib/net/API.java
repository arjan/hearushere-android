package nl.hearushere.lib.net;

import nl.hearushere.lib.Constants;
import nl.hearushere.lib.Utils;
import nl.hearushere.lib.data.Triggers;
import nl.hearushere.lib.data.Walk;
import nl.hearushere.lib.data.Walk.List;

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


	public void getLaatsteWoordTriggers(RequestListener<Triggers> listener) {
		System.out.println("-- get--");
		mSpiceManager.execute(new SpiceRequest<Triggers>(
				Triggers.class) {
			@Override
			public Triggers loadDataFromNetwork() throws Exception {
				System.out.println(" -- GET TRIGGERS --");
				JsonNode node = HttpRequest.doRequest("GET",
						Constants.LAATSTEWOORD_TRIGGERS_URL, null);
				return mMapper.treeToValue(node, Triggers.class);
			}
		}, "triggers", DurationInMillis.ONE_WEEK, listener);
	}
	public void clearCache() {
		mSpiceManager.removeAllDataFromCache();
	}

}
