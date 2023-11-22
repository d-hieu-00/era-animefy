package com.era.animefy.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

public class Utils {
    public interface Callback { void call(); }
    public static final boolean DEBUG = true; // DEBUG MODE
    public static final int DURATION_SHORT = 1000; // 1000 millisecond
    public static void runInDebug(Callback callback) {
        if (DEBUG) {
            callback.call();
        }
    }
    public static void getPermission(Activity activity, ActivityResultLauncher<String> launcher, String requestReason, String permission, ActivityResultCallback<Boolean> callback) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            callback.onActivityResult(true);
        } else if (activity.shouldShowRequestPermissionRationale(permission)) {
            Toast.makeText(activity, requestReason, Toast.LENGTH_LONG).show();
            callback.onActivityResult(false);
        } else {
            launcher.launch(permission);
        }
    }
}
