package nl.hearushere.app.verwonderdduin;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import nl.hearushere.app.main.R;

/**
 * Created by arjan on 8/2/16.
 */
public class VerwonderdDuinCreditsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vwd_credits);

        findViewById(R.id.container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
