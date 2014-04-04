package nl.hearushere.app.net;

import nl.hearushere.app.Constants;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octo.android.robospice.persistence.exception.SpiceException;

public class HttpRequest {

	static ObjectMapper mapper = new ObjectMapper();

	static {
		java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(
				java.util.logging.Level.FINEST);
	}

	public static JsonNode doRequest(String method, String url,
			String requestJSONBody) throws SpiceException {

		String urlParams = "";

		if (requestJSONBody != null && method.equals(HttpGet.METHOD_NAME)) {
			// urlParams = "?" + URLEncodedUtils.format(params, "utf-8");
		}

		HttpUriRequest request;

		if (method.equalsIgnoreCase(HttpGet.METHOD_NAME)) {
			request = new HttpGet(url + urlParams);
		} else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
			request = new HttpPut(url + urlParams);
		} else if (method.equalsIgnoreCase(HttpDelete.METHOD_NAME)) {
			request = new HttpDelete(url + urlParams);
		} else if (method.equalsIgnoreCase(HttpPost.METHOD_NAME)) {
			request = new HttpPost(url + urlParams);
		} else {
			throw new Error("Invalid request method");
		}

		if (requestJSONBody != null && !method.equals(HttpGet.METHOD_NAME)) {
			try {
				request.setHeader("Content-Type", "application/json");
				((HttpEntityEnclosingRequestBase) request)
						.setEntity(new StringEntity(requestJSONBody, "utf-8"));
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		HttpClient client = new DefaultHttpClient();

		// request.setHeader("User-Agent", Utils.getUserAgentString(context));

		try {
			HttpResponse response = client.execute(request);

			String body = null;
			int statusCode = response.getStatusLine().getStatusCode();

			HttpEntity entity = response.getEntity();
			if (entity != null) { // No content
				body = EntityUtils.toString(entity);
			}

			if (statusCode >= 400) {
				// HTTP code >= 400 is some kind of error
				throw new HttpException(statusCode, body);
			}
			if (body == null)
				return null;

			return mapper.readValue(body, JsonNode.class);

		} catch (Throwable e) {
			Log.e("xx", ".doJsonRequest(): " + e.getLocalizedMessage());
			e.printStackTrace();
			throw new SpiceException(e);
		}
	}
}
