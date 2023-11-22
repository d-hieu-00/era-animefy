package com.era.animefy.launcher.home;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.era.animefy.R;
import com.era.animefy.utils.ImageGenerator;
import com.era.animefy.utils.Utils;

import java.io.FileNotFoundException;

public class ActivityHome extends AppCompatActivity {
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::openImage);
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::displayImage);
    protected Boolean hasPermission = false;
    protected ImageView imageView;
    protected ImageView imageView1;
    protected Button btnTest;
    protected Button btnTest1;
    protected ProgressBar pbLoading;
    protected ImageGenerator imageGenerator;
    protected Boolean isLoading = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnTest = findViewById(R.id.btn_test);
        btnTest1 = findViewById(R.id.btn_test1);
        pbLoading = findViewById(R.id.pb_loading);
        imageView = findViewById(R.id.img_view);
        imageView1 = findViewById(R.id.img_view1);
        imageView.setVisibility(View.GONE);

//        btnTest.setOnClickListener(view -> captureFn());
        btnTest.setOnClickListener(view -> openImage(false));
        btnTest1.setOnClickListener(view -> {
            if (imageView.getVisibility() == View.VISIBLE) {
                imageView.setVisibility(View.GONE);
                imageView1.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView1.setVisibility(View.GONE);
            }
        });

    }

    protected void processImage(Uri uri) {
        try {
            String[] error = new String[1];
            Bitmap selectedBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            Bitmap bitmap = imageGenerator.run(this, selectedBitmap, error);

            if (bitmap == null) {
                runOnUiThread(() -> Toast.makeText(this, "Error process image. " + error[0] , Toast.LENGTH_LONG).show());
            } else {
                runOnUiThread(() -> {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                    imageView1.setImageBitmap(selectedBitmap);
                    imageView1.setVisibility(View.GONE);
                });
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "Not found image: " + uri, Toast.LENGTH_LONG).show());
        }

        runOnUiThread(() -> {
            pbLoading.setVisibility(View.GONE);
            btnTest.setVisibility(View.VISIBLE);
            btnTest1.setVisibility(View.VISIBLE);
        });
    }

    protected void displayImage(Uri uri) {
        if (uri != null) {
            pbLoading.setVisibility(View.VISIBLE);
            btnTest.setVisibility(View.GONE);
            btnTest1.setVisibility(View.GONE);
            new Thread(() -> processImage(uri)).start();
        } else {
            Toast.makeText(this, "No media image selected", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("InlinedApi")
    protected void openImage(Boolean hasPermission) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !hasPermission) {
            Utils.getPermission(this, requestPermissionLauncher, getString(R.string.msg_permission_read_media_image_reason), permission.READ_MEDIA_IMAGES, this::openImage);
            return;
        }

        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
            .build());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ActivityResultCallback<Boolean> launcherCallback = (ok) -> {
            hasPermission = ok;
            if (ok) {
                Log.i("Home", "Granted camera permission");
            } else {
                Toast.makeText(this, "Failed to get camera permission", Toast.LENGTH_SHORT).show();
            }
        };

        ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), launcherCallback);
        Utils.getPermission(this, permissionLauncher, getString(R.string.msg_permission_camera), permission.CAMERA, launcherCallback);
        new Thread(this::loadModelImageGenerator).start();
    }

    private void loadModelImageGenerator() {
        imageGenerator = new ImageGenerator();
        runOnUiThread(() -> {
            pbLoading.setVisibility(View.GONE);
            btnTest.setVisibility(View.VISIBLE);
            btnTest1.setVisibility(View.VISIBLE);
        });
        isLoading = false;
    }
}

