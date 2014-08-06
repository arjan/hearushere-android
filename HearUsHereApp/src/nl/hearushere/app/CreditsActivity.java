package nl.hearushere.app;

import nl.hearushere.app.extrapool.R;
import nl.hearushere.app.data.Walk;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;

public class CreditsActivity extends Activity {

	private Walk mWalk;
	private WebView mWebView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.credits);
		
		mWalk = Utils.deserialize(getIntent().getStringExtra("credits"), Walk.class);
		
		getActionBar().setTitle(mWalk.getTitle());
		
		ImageLoader.getInstance().loadImage(Constants.HEARUSHERE_BASE_URL + mWalk.getImage(), new ImageLoadingListener() {
			
			@Override
			public void onLoadingStarted(String arg0, View arg1) {
			}
			
			@Override
			public void onLoadingFailed(String arg0, View arg1, FailReason arg2) {
			}
			
			@Override
			public void onLoadingComplete(String arg0, View arg1, Bitmap arg2) {
				getActionBar().setIcon(new BitmapDrawable(getResources(), arg2));
			}
			
			@Override
			public void onLoadingCancelled(String arg0, View arg1) {
			}
		});
		
		getActionBar().setHomeButtonEnabled(true);

		mWebView = (WebView)findViewById(R.id.wv_credits);
		
		mWebView.loadUrl(Constants.HEARUSHERE_BASE_URL + mWalk.getCredits());
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
