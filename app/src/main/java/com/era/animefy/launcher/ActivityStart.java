package com.era.animefy.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import com.era.animefy.R;
import com.era.animefy.launcher.home.ActivityHome;
import com.era.animefy.utils.Utils;
import com.era.animefy.utils.dialog.ErrorExitDialog;

public class ActivityStart extends AppCompatActivity {
    private final Handler toastHandler = new Handler();
    private boolean doubleBack = false;
    private boolean canceled = false;
    private TextView txtLoadingInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        txtLoadingInfo = findViewById(R.id.loading);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (doubleBack) {
                    canceled = true;
                    finish();
                    return;
                }

                doubleBack = true;
                Toast.makeText(getApplicationContext(), R.string.msg_double_press_back, Toast.LENGTH_SHORT).show();
                toastHandler.postDelayed(() -> doubleBack = false, Utils.DURATION_SHORT);
            }
        });

        new Thread(this::loadResource).start();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out);
    }

    private void updateLoadingInfo(CharSequence info) {
        runOnUiThread(() -> txtLoadingInfo.setText(info));
    }

    private void loadResource() {
        try {
            updateLoadingInfo("Still loading");
            updateLoadingInfo("Load successfully");
            Thread.sleep(100);
            if (!canceled) {
                startActivity(new Intent(this, ActivityMain.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            }
        } catch (Exception e) {
            Utils.runInDebug(e::printStackTrace);
            new ErrorExitDialog("Exit ERA application", "Error when loading resource").show(getSupportFragmentManager(), null);
        }
    }
}
