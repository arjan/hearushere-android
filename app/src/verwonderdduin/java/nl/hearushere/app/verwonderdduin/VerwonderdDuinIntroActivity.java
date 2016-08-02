package nl.hearushere.app.verwonderdduin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import nl.hearushere.app.main.R;

/**
 * Created by arjan on 8/2/16.
 */
public class VerwonderdDuinIntroActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vwd_intro);

        findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VerwonderdDuinIntroActivity.this, MainActivity.class));
            }
        });
    }
}
