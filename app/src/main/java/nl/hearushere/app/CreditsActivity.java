package nl.hearushere.app;

import nl.hearushere.lib.Utils;
import nl.hearushere.lib.data.Walk;
import nl.hearushere.app.main.R;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

public class CreditsActivity extends Activity {

    private Walk mWalk;
    private WebView mWebView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.credits);

        mWalk = Utils.deserialize(getIntent().getStringExtra("credits"), Walk.class);

        getActionBar().setTitle(mWalk.getTitle());

        if (mWalk.getImageUrl() != null) {
            ImageLoader.getInstance().loadImage(mWalk.getImageUrl(), new ImageLoadingListener() {

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
        }

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
        }

        mWebView = (WebView) findViewById(R.id.wv_credits);

        mWebView.loadUrl(mWalk.getCreditsUrl());
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
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
