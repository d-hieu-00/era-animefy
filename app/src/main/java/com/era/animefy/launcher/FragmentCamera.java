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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;

import androidx.camera.core.ImageCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

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
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCamera extends Fragment {
    // Setup ActivityResultContracts Launcher
    private final ActivityResultLauncher<String> permissionReadImageLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::openCamera);

    // Internal variables
    private final ImageGenerator imageGenerator = new ImageGenerator();
    private boolean isProcessing;
    private Bitmap convertedImage;
    private Bitmap originalImage;

    // Views
    protected TextView mainTxtTitle;
    protected ProgressBar mainPbLoading;
    protected PreviewView previewView;
    protected FrameLayout imgViewLayout;
    protected LinearLayout imgActionsViewLayout0;
    protected LinearLayout imgActionsViewLayout;
    protected ImageView imgViewConverted;
    protected ImageView imgViewOriginal;
    protected TextView txtViewOpenAction;
    protected TextView txtViewSaveAction;
    protected TextView txtViewShowAction;
    protected TextView txtViewOpenCameraAction;
    protected TextView txtViewTakePictureAction;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    public FragmentCamera(TextView txtTitle, ProgressBar pbLoading) {
        mainTxtTitle = txtTitle;
        mainPbLoading = pbLoading;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // [1] Setup variables
        previewView = view.findViewById(R.id.preview_view);
        imgViewLayout = view.findViewById(R.id.img_frame);
        imgActionsViewLayout0 = view.findViewById(R.id.sub_menu0);
        imgActionsViewLayout = view.findViewById(R.id.sub_menu);
        imgViewConverted = view.findViewById(R.id.img_converted);
        imgViewOriginal = view.findViewById(R.id.img_original);
        txtViewOpenAction = view.findViewById(R.id.txt_open_action);
        txtViewSaveAction = view.findViewById(R.id.txt_save_action);
        txtViewShowAction = view.findViewById(R.id.txt_show_action);
        txtViewOpenCameraAction = view.findViewById(R.id.txt_open_camera);
        txtViewTakePictureAction = view.findViewById(R.id.txt_take_picture);

        imgViewLayout.setVisibility(View.GONE);
        imgActionsViewLayout.setVisibility(View.GONE);

        // [2] Setup events
        txtViewOpenAction.setOnClickListener(v -> {
            requireActivity().runOnUiThread(() -> {
                imgActionsViewLayout0.setVisibility(View.VISIBLE);
                previewView.setVisibility(View.VISIBLE);
                imgActionsViewLayout.setVisibility(View.GONE);
                imgViewLayout.setVisibility(View.GONE);
            });
        });
        txtViewOpenCameraAction.setOnClickListener(v -> openCamera(false));
        txtViewTakePictureAction.setOnClickListener(v -> takePicture());
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @SuppressLint("InlinedApi")
    protected void openCamera(boolean permission) {
        // [1] Get permission to read media image
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !permission) {
            Utils.getPermission(getActivity(),
                    permissionReadImageLauncher,
                    getString(R.string.msg_permission_camera_reason),
                    Manifest.permission.CAMERA,
                    this::openCamera);
            return;
        }

        // [2] Setup camera
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                // Handle any errors
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        // [3] Change display
        txtViewOpenCameraAction.setVisibility(View.GONE);
        txtViewTakePictureAction.setVisibility(View.VISIBLE);
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner) requireContext(), cameraSelector, preview);

        imageCapture = new ImageCapture.Builder()
                .build();
    }

    protected void takePicture() {
        originalImage = previewView.getBitmap();
        requireActivity().runOnUiThread(() -> {
            imgActionsViewLayout0.setVisibility(View.GONE);
            previewView.setVisibility(View.GONE);
            imgActionsViewLayout.setVisibility(View.VISIBLE);
            imgViewLayout.setVisibility(View.VISIBLE);
        });
        new Thread(this::processImage).start();
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;

        // Create a Bitmap from the captured image bytes
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
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
            previewView.setVisibility(View.GONE);

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
                previewView.setVisibility(View.GONE);

                imgViewOriginal.setVisibility(View.VISIBLE);
                imgViewConverted.setVisibility(View.GONE);
                imgViewLayout.setVisibility(View.VISIBLE);

                txtViewOpenAction.setVisibility(View.VISIBLE);
                txtViewSaveAction.setVisibility(View.GONE);
                txtViewShowAction.setVisibility(View.GONE);
                imgActionsViewLayout.setVisibility(View.VISIBLE);
            } else {
                // Process success
                previewView.setVisibility(View.GONE);

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
            previewView.setVisibility(View.VISIBLE);

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

    protected void processImage() {
        // [1] Set Persistent permission
        setIsProcessing(true);

        // [2] Reading image
        convertedImage = null;
        requireActivity().runOnUiThread(this::handleDisplay);

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