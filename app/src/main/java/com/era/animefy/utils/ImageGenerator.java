package com.era.animefy.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.CompatibilityList;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

class ImageGeneratorHelper {
    public static Bitmap rotateBitmap(Bitmap originalBitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, int newWidth, int newHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    public static void prepareInput(Bitmap input, float[][][][] outArr) {
        int width = input.getWidth();
        int height = input.getHeight();

        int[] pixels = new int[width * height];
        input.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = pixels[y * width + x];

                outArr[0][x][y][0] = (float) (((pixel >> 16) & 0xFF) / 127.5 - 1.0);    // red / 127.5 - 1.0
                outArr[0][x][y][1] = (float) (((pixel >> 8) & 0xFF) / 127.5 - 1.0);     // green / 127.5 - 1.0
                outArr[0][x][y][2] = (float) ((pixel & 0xFF) / 127.5 - 1.0);            // blue / 127.5 - 1.0
            }
        }
    }

    private static int clipInt(int input)
    {
        return  input <= 0 ? 0 : Math.min(input, 255);
    }

    public static Bitmap parseOutput(float[][][][] output) {
        int width = output[0].length;
        int height = output[0][0].length;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // pixel = (0xFF << 24) | (clipInt(red) << 16) | (clipInt(green) << 8) | clipInt(blue)
                int pixel = (0xFF << 24)
                        | (clipInt((int) ((output[0][x][y][0] + 1) * 127.5)) << 16)
                        | (clipInt((int) ((output[0][x][y][1] + 1) * 127.5)) << 8)
                        | clipInt((int) ((output[0][x][y][2] + 1) * 127.5));
                pixels[y * width + x] = pixel;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;
    }

}

public class ImageGenerator {
    /** @noinspection resource*/
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public ImageGenerator() {
        Log.i("ImageGenerator", "Init ImageGenerator. Support size: 720x960 , 480x640 , 320x480 , 240x320");
    }

    private String pickInterpreterModelPath(int width, int[] newSize) {
        if (newSize.length != 2) {
            return null;
        }

        // Expected width must less than height
        if (width >= 720) {
            newSize[0] = 720;
            newSize[1] = 960;
            return "model.720x960.tflite";
        } else if (width >= 480) {
            newSize[0] = 480;
            newSize[1] = 640;
            return "model.480x640.tflite";
        } else if (width >= 320) {
            newSize[0] = 320;
            newSize[1] = 480;
            return "model.320x480.tflite";
        }

        newSize[0] = 240;
        newSize[1] = 320;
        return "model.240x320.tflite";
    }

    public Bitmap run(Context context, Bitmap input, String[] error) {
        error[0] = "";
        Bitmap rotatedBitmap = null;
        boolean haveRotated = false;
        if (input.getWidth() < input.getHeight()) {
            rotatedBitmap = ImageGeneratorHelper.rotateBitmap(input, 90);
            haveRotated = true;
        }

        int width = haveRotated ? rotatedBitmap.getWidth() : input.getWidth();
        int height = haveRotated ? rotatedBitmap.getHeight() : input.getHeight();
        int[] newSize = {0, 0};

        String modelPath = this.pickInterpreterModelPath(width, newSize);
        Bitmap output = null;
        Interpreter interpreter = null;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.addDelegate(new GpuDelegate());
            try (CompatibilityList compatList = new CompatibilityList()) {
                if (compatList.isDelegateSupportedOnThisDevice()) {
                    // if the device has a supported GPU, add the GPU delegate
                    options.addDelegate(new GpuDelegate());
                } else {
                    // if the GPU is not supported, set use NN API https://developer.android.com/ndk/guides/neuralnetworks (available from android 8)
                    options.setUseNNAPI(true);
                }
            } catch (Exception e) {
                options.setUseNNAPI(true);
            }
            interpreter = new Interpreter(this.loadModelFile(context, modelPath), options);

            // Resize the original bitmap to new size
            Bitmap resizedBitmap = ImageGeneratorHelper.resizeBitmap(haveRotated ? rotatedBitmap : input, newSize[0], newSize[1]);

            // Prepare data for generate process
            float[][][][] inData = new float[1][resizedBitmap.getWidth()][resizedBitmap.getHeight()][3];
            float[][][][] result = new float[1][resizedBitmap.getWidth()][resizedBitmap.getHeight()][3];
            ImageGeneratorHelper.prepareInput(resizedBitmap, inData);

            // Do generate process
            interpreter.run(inData, result);

            // Resize the bitmap back to its original size
            output = ImageGeneratorHelper.resizeBitmap(ImageGeneratorHelper.parseOutput(result), width, height);
        } catch (Exception e) {
            e.printStackTrace();
            error[0] = e.getMessage();
        }

        if (interpreter != null) {
            interpreter.close();
        }

        if (haveRotated && output != null) {
            return  ImageGeneratorHelper.rotateBitmap(output, -90);
        }
        return output;
    }
}
