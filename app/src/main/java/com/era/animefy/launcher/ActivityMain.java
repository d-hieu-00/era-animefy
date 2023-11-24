package com.era.animefy.launcher;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.era.animefy.R;
import com.era.animefy.utils.Utils;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashMap;

public class ActivityMain extends AppCompatActivity {
    private final String KeyPerReadImage = "per_read_image";
    private final Handler toastHandler = new Handler();
    private final HashMap<String, Boolean> permissions = new HashMap<>();
    private Boolean doubleBack = false;

    // Views
    protected TextView txtTitle;
    protected ProgressBar pbLoading;
    protected BottomNavigationView bottomNavigation;
    protected FragmentConvert fragmentConvert;
    protected FragmentCamera fragmentCamera;
    protected FragmentInfo fragmentInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // [1] Setup activity variables
        txtTitle = findViewById(R.id.txt_title);
        pbLoading = findViewById(R.id.pb_loading);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        pbLoading.setVisibility(View.GONE);

        // [2] Setup permissions
        permissions.put(KeyPerReadImage, false);

        // [3] Replace main frame to 'convert'
        fragmentConvert = new FragmentConvert(txtTitle, pbLoading);
        fragmentCamera = new FragmentCamera(txtTitle, pbLoading);
        fragmentInfo = new FragmentInfo();
        replaceMainFrame(fragmentConvert);

        // [4] Setup double back to exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (doubleBack) {
                    finish();
                    return;
                }

                doubleBack = true;
                Toast.makeText(getApplicationContext(), R.string.msg_double_press_back, Toast.LENGTH_SHORT).show();
                toastHandler.postDelayed(() -> doubleBack = false, Utils.DURATION_SHORT);
            }
        });

        // [5] Setup bottom navigation click action
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_convert) {
                replaceMainFrame(fragmentConvert);
                return true;
            }  else if (id == R.id.menu_camera) {
                replaceMainFrame(fragmentCamera);
                return true;
            } else if (id == R.id.menu_about) {
                replaceMainFrame(fragmentInfo);
                return true;
            }
            return false;
        });
    }

    @Override
    @SuppressLint("InlinedApi")
    protected void onStart() {
        super.onStart();

        // [1] Get permission to read media image
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && Boolean.FALSE.equals(permissions.get(KeyPerReadImage))) {
            ActivityResultCallback<Boolean> launcherCallback = (ok) -> {
                permissions.put(KeyPerReadImage, ok);
                if (ok) {
                    Toast.makeText(this, "Granted read image permission", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to get read image permission", Toast.LENGTH_SHORT).show();
                }
            };

            ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), launcherCallback);
            Utils.getPermission(this, permissionLauncher, getString(R.string.msg_permission_read_media_image_reason), Manifest.permission.READ_MEDIA_IMAGES, launcherCallback);
        } else {
            permissions.put(KeyPerReadImage, true);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out);
    }

    protected void replaceMainFrame(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.main_frame, fragment)
            .addToBackStack(null)
            .commit();
    }
}