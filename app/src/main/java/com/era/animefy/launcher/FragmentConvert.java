package com.era.animefy.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.era.animefy.R;
import com.era.animefy.utils.ImageGenerator;
import com.era.animefy.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

public class FragmentConvert extends Fragment {
    // Setup ActivityResultContracts Launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::displayImage);
    private final ActivityResultLauncher<String> permissionReadImageLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::openImage);
    // private final ActivityResultLauncher<String> permissionWriteImageLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::saveImage);

    // Internal variables
    private final ImageGenerator imageGenerator = new ImageGenerator();
    private boolean isProcessing;
    private Bitmap convertedImage;
    private Bitmap originalImage;

    // Views
    protected TextView mainTxtTitle;
    protected ProgressBar mainPbLoading;
    protected ConstraintLayout openViewLayout;
    protected FrameLayout imgViewLayout;
    protected LinearLayout imgActionsViewLayout;
    protected ImageView imgViewConverted;
    protected ImageView imgViewOriginal;
    protected TextView txtViewOpenAction;
    protected TextView txtViewSaveAction;
    protected TextView txtViewShowAction;

    public FragmentConvert(TextView txtTitle, ProgressBar pbLoading) {
        mainTxtTitle = txtTitle;
        mainPbLoading = pbLoading;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_convert, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // [1] Setup variables
        openViewLayout = view.findViewById(R.id.open_container);
        imgViewLayout = view.findViewById(R.id.img_frame);
        imgActionsViewLayout = view.findViewById(R.id.sub_menu);
        imgViewConverted = view.findViewById(R.id.img_converted);
        imgViewOriginal = view.findViewById(R.id.img_original);
        txtViewOpenAction = view.findViewById(R.id.txt_open_action);
        txtViewSaveAction = view.findViewById(R.id.txt_save_action);
        txtViewShowAction = view.findViewById(R.id.txt_show_action);

        imgViewLayout.setVisibility(View.GONE);
        imgActionsViewLayout.setVisibility(View.GONE);

        // [2] Setup events
        openViewLayout.setClickable(true);
        openViewLayout.setOnClickListener(v -> openImage(false));
        txtViewOpenAction.setOnClickListener(v -> openImage(false));
        txtViewSaveAction.setOnClickListener(v -> saveImage());
        txtViewShowAction.setOnClickListener(v -> {
            if (imgViewConverted.getVisibility() == View.VISIBLE) {
                imgViewConverted.setVisibility(View.GONE);
                imgViewOriginal.setVisibility(View.VISIBLE);
                txtViewShowAction.setText(R.string.show_converted);
                txtViewSaveAction.setVisibility(View.GONE);
            } else {
                imgViewConverted.setVisibility(View.VISIBLE);
                imgViewOriginal.setVisibility(View.GONE);
                txtViewShowAction.setText(R.string.show_origin);
                txtViewSaveAction.setVisibility(View.VISIBLE);
            }
        });

        // [3] Handle display on resume
        handleDisplay();
    }

    @SuppressLint("InlinedApi")
    protected void openImage(boolean permission) {
        // [1] Get permission to read media image
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !permission) {
            Utils.getPermission(getActivity(),
                    permissionReadImageLauncher,
                    getString(R.string.msg_permission_read_media_image_reason),
                    Manifest.permission.READ_MEDIA_IMAGES,
                    this::openImage);
            return;
        }

        // [2] Pick image
        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    protected void displayImage(Uri uri) {
        if (uri != null) {
            new Thread(() -> processImage(uri)).start();
        } else {
            Toast.makeText(getActivity(), "No media image selected", Toast.LENGTH_SHORT).show();
        }
    }

    protected void handleDisplay() {
        if (originalImage != null) {
            imgViewOriginal.setImageBitmap(originalImage);
        }
        if (convertedImage != null) {
            imgViewConverted.setImageBitmap(convertedImage);
        }

        // Handle visibility
        if (isProcessing && originalImage != null && convertedImage == null) {
            // In process
            openViewLayout.setVisibility(View.GONE);

            imgViewOriginal.setVisibility(View.VISIBLE);
            imgViewConverted.setVisibility(View.GONE);
            imgViewLayout.setVisibility(View.VISIBLE);

            txtViewOpenAction.setVisibility(View.GONE);
            txtViewSaveAction.setVisibility(View.GONE);
            txtViewShowAction.setVisibility(View.GONE);
            imgActionsViewLayout.setVisibility(View.GONE);
        } else if (!isProcessing && originalImage != null) {
            if (convertedImage == null) {
                // Process failed
                openViewLayout.setVisibility(View.GONE);

                imgViewOriginal.setVisibility(View.VISIBLE);
                imgViewConverted.setVisibility(View.GONE);
                imgViewLayout.setVisibility(View.VISIBLE);

                txtViewOpenAction.setVisibility(View.VISIBLE);
                txtViewSaveAction.setVisibility(View.GONE);
                txtViewShowAction.setVisibility(View.GONE);
                imgActionsViewLayout.setVisibility(View.VISIBLE);
            } else {
                // Process success
                openViewLayout.setVisibility(View.GONE);

                imgViewOriginal.setVisibility(View.GONE);
                imgViewConverted.setVisibility(View.VISIBLE);
                imgViewLayout.setVisibility(View.VISIBLE);

                txtViewOpenAction.setVisibility(View.VISIBLE);
                txtViewSaveAction.setVisibility(View.VISIBLE);
                txtViewShowAction.setVisibility(View.VISIBLE);
                txtViewShowAction.setText(R.string.show_origin);
                imgActionsViewLayout.setVisibility(View.VISIBLE);
            }
        } else {
            // Not process yet
            openViewLayout.setVisibility(View.VISIBLE);

            imgViewOriginal.setVisibility(View.GONE);
            imgViewConverted.setVisibility(View.GONE);
            imgViewLayout.setVisibility(View.GONE);

            txtViewOpenAction.setVisibility(View.GONE);
            txtViewSaveAction.setVisibility(View.GONE);
            txtViewShowAction.setVisibility(View.GONE);
            imgActionsViewLayout.setVisibility(View.GONE);
        }
    }

    protected void setIsProcessing(boolean processing) {
        isProcessing = processing;
        requireActivity().runOnUiThread(() -> {
            if (isProcessing) {
                mainPbLoading.setVisibility(View.VISIBLE);
            } else {
                mainPbLoading.setVisibility(View.GONE);
            }
        });
    }

    protected void processImage(Uri uri) {
        // [1] Set Persistent permission
        requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setIsProcessing(true);

        // [2] Reading image
        try {
            convertedImage = null;
            originalImage = BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(uri));
            requireActivity().runOnUiThread(this::handleDisplay);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Not found image: " + uri, Toast.LENGTH_LONG).show());
        }

        // [3] Convert image
        String[] error = new String[1];
        convertedImage = imageGenerator.run(requireContext(), originalImage, error);
        setIsProcessing(false);
        if (convertedImage == null) {
            requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Error process image. " + error[0], Toast.LENGTH_LONG).show());
            requireActivity().runOnUiThread(this::handleDisplay);
            return;
        }

        // [4] Handle display
        requireActivity().runOnUiThread(this::handleDisplay);
    }

    protected void saveImage() {
        String fileName = "era-animefy-" + UUID.randomUUID().toString() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = requireContext().getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            Uri uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues);

            try {
                OutputStream stream = resolver.openOutputStream(Objects.requireNonNull(uri));
                convertedImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Failed to save image (3).", Toast.LENGTH_SHORT).show());
                return;
            }
        } else {
            // [1] Find & create directory
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pictures");
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Failed to save image (1).", Toast.LENGTH_SHORT).show());
                    return;
                }
            }

            // [2] Create the file to save the image.
            File file = new File(directory, fileName);

            // [3] Save to previous created file
            try (FileOutputStream out = new FileOutputStream(file)) {
                convertedImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Failed to save image (2).", Toast.LENGTH_SHORT).show());
                return;
            }
        }
        requireActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Save image success", Toast.LENGTH_SHORT).show());
    }
}