/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.math.ArgumentOutsideDomainException;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.opengl.GLES20;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

//import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
//import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math.analysis.polynomials.PolynomialFunction;
//import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
//import org.apache.commons.math3.exception.DimensionMismatchException;
//import org.apache.commons.math.exception.DimensionMismatchException;
//import org.apache.commons.math3.exception.NonMonotonicSequenceException;
//import org.apache.commons.math.exception.NonMonotonicSequenceException;
//import org.apache.commons.math3.exception.NumberIsTooSmallException;
//import org.apache.commons.math3.exception.util.LocalizedFormats;
//import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math.util.MathUtils.*;
//import org.apache.commons.math3.util.MathArrays;
import org.apache.commons.math.util.*;

import static android.view.MotionEvent.ACTION_MASK;
import static java.lang.Math.PI;

public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, View.OnTouchListener, ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private ImageView histogramView;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
//            setRGB();

//            Bitmap wholeBitmap = mTextureView.getBitmap();
//            int cropX = (mTextureView.getWidth() / 2) - dpToPx(25);
//            int cropY = (mTextureView.getHeight() / 2) - dpToPx(25);
//            final Bitmap croppedBitmap = Bitmap.createBitmap(wholeBitmap, cropX, cropY, dpToPx(50), dpToPx(50));
//            drawHistogram(croppedBitmap);
//            wholeBitmap.recycle();
//            croppedBitmap.recycle();
        }
    };


    void setRGB() {
        Bitmap bitmap = mTextureView.getBitmap();
//      Bitmap bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
        int x = (int) (mTextureView.getX() + mTextureView.getWidth() / 2);
        int y = (int) (mTextureView.getY() + mTextureView.getHeight() / 2);
        int pixel = bitmap.getPixel(x, y);
        int redValue = Color.red(pixel);
        int greenValue = Color.green(pixel);
        int blueValue = Color.blue(pixel);
        colorRedTextView.setText("Red:" + redValue);
        colorGreenTextView.setText("Green:" + greenValue);
        colorBlueTextView.setText("Blue:" + blueValue);
        colorTextView.setBackgroundColor(Color.rgb(redValue, greenValue, blueValue));

        Spectrum spctr = new Spectrum();
        int spctrValue = spctr.GetCCT(redValue, greenValue, blueValue);

        CCTTextView.setText("CCT: " + spctrValue + "K");
    }

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

//            final Image image = reader.acquireLatestImage();
//            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//            byte[] bytes = new byte[buffer.capacity()];
//            buffer.get(bytes);
//            final Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
////            final Bitmap bitmapImage = mTextureView.getBitmap();
//            int cropX = (bitmapImage.getWidth() / 2) - 50; //dpToPx(5);
//            int cropY = (bitmapImage.getHeight() / 2) - 50; //dpToPx(5);
//            final Bitmap croppedBitmap = Bitmap.createBitmap(bitmapImage, cropX, cropY, 100, 100); //dpToPx(10), dpToPx(10));
//            Log.i( TAG, "mTextureWidth: "  + mTextureView.getWidth());
//            Log.i( TAG, "mTextureHeigth: "  + mTextureView.getHeight());
//
//            Log.i( TAG, "imageWidth: "  + bitmapImage.getWidth());
//            Log.i( TAG, "imageHeigth: "  + bitmapImage.getHeight());
//
//            Log.i( TAG, "cropX: "  + cropX);
//            Log.i( TAG, "cropY: "  + cropY);


//            getActivity().runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    histogramView.invalidate();
//                    histogramView.setImageBitmap(null);
//                    histogramView.setImageResource(0);

//                    histogramView.setImageBitmap(croppedBitmap);

            //drawHistogram(croppedBitmap);
//
//                }
//            });

//            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
//    private void showToast(final String text) {
//        final Activity activity = getActivity();
//        if (activity != null) {
//            activity.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
//                }
//            });
//        }
//    }

    private TextView colorRedTextView;
    private TextView colorGreenTextView;
    private TextView colorBlueTextView;

    private TextView hueView;
    private TextView saturationView;
    private TextView valueView;

    private TextView labView;

    private TextView colorTextView;
    private View view;
    private TextView CCTTextView;
    private SightView sightView;
    private Bitmap croppedBitmap;


    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static int pxToDp(int px) {
        return (int) (px / Resources.getSystem().getDisplayMetrics().density);
    }

    public static int getDominantColor(Bitmap bitmap) {
        if (null == bitmap) return Color.TRANSPARENT;

        int redBucket = 0;
        int greenBucket = 0;
        int blueBucket = 0;
        int alphaBucket = 0;

        boolean hasAlpha = bitmap.hasAlpha();
        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int y = 0, h = bitmap.getHeight(); y < h; y++) {
            for (int x = 0, w = bitmap.getWidth(); x < w; x++) {
                int color = pixels[x + y * w]; // x + y * width
                redBucket += (color >> 16) & 0xFF; // Color.red
                greenBucket += (color >> 8) & 0xFF; // Color.greed
                blueBucket += (color & 0xFF); // Color.blue
                if (hasAlpha) alphaBucket += (color >>> 24); // Color.alpha
            }
        }

        return Color.rgb(
                redBucket / pixelCount,
                greenBucket / pixelCount,
                blueBucket / pixelCount);

//        return Color.argb(
//                        (hasAlpha) ? (alphaBucket / pixelCount) : 255,
//                        redBucket / pixelCount,
//                        greenBucket / pixelCount,
//                        blueBucket / pixelCount);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_camera2_basic, container, false);
        return view;

    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
//        view.findViewById(R.id.picture).setOnClickListener(this);
//        view.findViewById(R.id.info).setOnClickListener(this);

        colorRedTextView = (TextView) view.findViewById(R.id.colorRed);
        colorGreenTextView = (TextView) view.findViewById(R.id.colorGreen);
        colorBlueTextView = (TextView) view.findViewById(R.id.colorBlue);
        colorTextView = (TextView) view.findViewById(R.id.color);

        hueView = (TextView) view.findViewById(R.id.hue);
        saturationView = (TextView) view.findViewById(R.id.saturation);
        valueView = (TextView) view.findViewById(R.id.value);
        labView = (TextView) view.findViewById(R.id.CieLab);

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        histogramView = view.findViewById(R.id.histogram);
        CCTTextView = (TextView) view.findViewById(R.id.CCT);
        sightView = (SightView) view.findViewById(R.id.sightView);

        histogramView.setOnClickListener(this);
        mTextureView.setOnClickListener(this);

        graph = (GraphView) view.findViewById(R.id.graph);

//        graph.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                switchDiagrams();
//                return true;
//            }
//        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    StreamConfigurationMap map;

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);


                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);


                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            //showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //TODO: Tha camera resolution to work should be 640x480

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
//        histogramView.setImageResource(android.R.color.transparent);
//        histogramView.setImageBitmap(null);
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());


            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    //showToast("Saved: " + mFile);
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
//            case R.id.picture:
            case R.id.texture: {
                //mTextureView.setOnClickListener(null);
                histogramDrawn = false;
//                graph.setVisibility(View.INVISIBLE);
//                histogramView.setVisibility(View.VISIBLE);
                cameraBitmap = mTextureView.getBitmap();
                int cropX = (int) sightView.getX();  //dpToPx(5);
                int cropY = (int) sightView.getY(); // dpToPx(5);
//                Log.i(TAG, "cropX: " + cropX);
//                Log.i(TAG, "cropY: " + cropY);
                onPictureTaken(cameraBitmap);
                croppedBitmap = Bitmap.createBitmap(cameraBitmap, cropX, cropY, 30, 30); // dpToPx(10), dpToPx(10));
                int color = getDominantColor(croppedBitmap);

//                x = (int) motionEvent.getX();
//                y = (int) motionEvent.getY();
//                int pixel = bitmap.getPixel(x, y);

                float alpha = Color.alpha(color);
                int redValue = Color.red(color);
                int greenValue = Color.green(color);
                int blueValue = Color.blue(color);

                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                double[] lab = new double[3];
                ColorUtils.colorToLAB(color, lab);

                colorRedTextView.setText(String.valueOf(redValue));
                colorGreenTextView.setText(String.valueOf(greenValue));
                colorBlueTextView.setText(String.valueOf(blueValue));
                colorTextView.setBackgroundColor(Color.rgb(redValue, greenValue, blueValue));

                DecimalFormat dm = new DecimalFormat("#,##0");

                hueView.setText(String.format("%1$3.2f˚", hsv[0]));
                saturationView.setText(String.format("%1$1.3f", hsv[1]));
                valueView.setText(String.format("%1$1.3f", hsv[2]));
                labView.setText(String.format("%1$1.2f, %2$+3.2f, %3$+3.2f", lab[0], lab[1], lab[2]));

                Spectrum spctr = new Spectrum();
                int spctrValue = spctr.GetCCT(redValue, greenValue, blueValue);

                double calcLuminance = spctr.GetLuminance(redValue, greenValue, blueValue);
                float libLuminance = Color.luminance(color);


//                Log.i(TAG, "Calculated luminance: " + calcLuminance);
//                Log.i(TAG, "Library luminance: " + libLuminance);

                CCTTextView.setText(String.valueOf(spctrValue));
                histogramView.setImageBitmap(croppedBitmap);

                takePicture();
                break;
            }

            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }

            case R.id.histogram: {
                drawHistogram(croppedBitmap);
                Intent intent = new Intent(getActivity(), SpectrumActivity.class);
                getActivity().startActivity(intent);
                break;
            }
//            case R.id.histogram: {
//                if (histogramView.getDrawable() == null)
//                    break;
//                if (histogramDrawn)
//                    switchDiagrams();
//                else
//                    drawHistogram(croppedBitmap);
//                break;
//            }

//            case R.id.graph: {
//                switchDiagrams();
//            }


        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        // TODO Auto-generated method stub
        float x = 0;
        float y = 0;
//        croppedBitmap = null;
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
//                Log.i("Camera2Basic", "onTouch: ACTION_DOWN");
                //some code....
                x = motionEvent.getX();
                y = motionEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
//                float x2 = motionEvent.getX() - x;
//                float y2 = motionEvent.getY() - y;
//                if (Math.abs(x2) > 50)
//                    switchDiagrams();
                break;
            default:
                break;

        }

        return false;
    }

    void switchDiagrams() {
        onPictureTaken(cameraBitmap);
        graph.setTitle("Spectrum");


//        if (histogramView.getVisibility() == View.VISIBLE) {
//            histogramView.setVisibility(View.INVISIBLE);
//            graph.setVisibility(View.VISIBLE);
//            if (cameraBitmap != null)
//                onPictureTaken(cameraBitmap);
//            graph.setTitle("Spectrum");
//        } else {
//            histogramView.setVisibility(View.VISIBLE);
//            graph.setVisibility(View.INVISIBLE);
//        }
    }


    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {

//            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
//            byte[] bytes = new byte[buffer.remaining()];
//            buffer.get(bytes);
//            FileOutputStream output = null;
//            try {
//                output = new FileOutputStream(mFile);
//                output.write(bytes);
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                mImage.close();
//                if (null != output) {
//                    try {
//                        output.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    private boolean histogramDrawn;
    private Bitmap cameraBitmap;

    public void drawHistogram(Bitmap bitmap) {
        try {
            Mat rgba = new Mat();
            Utils.bitmapToMat(bitmap, rgba);
//            bitmap.recycle();

            org.opencv.core.Size rgbaSize = new org.opencv.core.Size(histogramView.getMeasuredWidth(), histogramView.getMeasuredHeight());
//            org.opencv.core.Size rgbaSize = new org.opencv.core.Size(bitmap.getWidth(), bitmap.getHeight());
//            org.opencv.core.Size rgbaSize = rgba.size();

            int histSize = 256;
            MatOfInt histogramSize = new MatOfInt(histSize);

            int histogramHeight = histogramView.getMeasuredHeight();
//            int histogramHeight = (int) rgbaSize.height;
//            int binWidth = 5;

            int binWidth = Math.round(histogramView.getMeasuredWidth() / histSize);
//            int binWidth = Math.round(histogramSize.width() / histSize);
            MatOfFloat histogramRange = new MatOfFloat(0f, 256f);


            Scalar[] colorsRgb = new Scalar[]{new Scalar(200, 0, 0, 255), new Scalar(0, 200, 0, 255), new Scalar(0, 0, 200, 255)};
            MatOfInt[] channels = new MatOfInt[]{new MatOfInt(0), new MatOfInt(1), new MatOfInt(2)};

            Mat[] histograms = new Mat[]{new Mat(), new Mat(), new Mat()};
//            Mat histMatBitmap = new Mat(rgbaSize, rgba.type());
            Mat histMatBitmap = new Mat(new org.opencv.core.Size(histogramView.getMeasuredWidth(), histogramView.getMeasuredHeight()), rgba.type());
//            Mat histMatBitmap = new Mat(new org.opencv.core.Size(200, 100), rgba.type());

            for (int i = 0; i < channels.length; i++) {
                Imgproc.calcHist(Collections.singletonList(rgba), channels[i], new Mat(), histograms[i], histogramSize, histogramRange);
                Core.normalize(histograms[i], histograms[i], 0, histogramHeight, Core.NORM_MINMAX);  //NORM_INF);
                for (int j = 0; j < histSize; j++) {
                    org.opencv.core.Point p1 = new org.opencv.core.Point(binWidth * (j - 1), histogramHeight - Math.round(histograms[i].get(j - 1, 0)[0]));
                    org.opencv.core.Point p2 = new org.opencv.core.Point(binWidth * j, histogramHeight - Math.round(histograms[i].get(j, 0)[0]));
                    Imgproc.line(histMatBitmap, p1, p2, colorsRgb[i], 3, 8, 0);
                }
            }

            Bitmap histBitmap = Bitmap.createBitmap(histMatBitmap.cols(), histMatBitmap.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(histMatBitmap, histBitmap);

            //histogramView.setImageResource(android.R.color.transparent);
            //histogramView.setImageBitmap(null);
            //histogramView.invalidate();

            BitmapHelper.showBitmap(getContext(), histBitmap, histogramView);
            histogramDrawn = true;
            rgba.release();
            for (int g = 0; g < histograms.length; g++) {
                histograms[g].release();
            }

            histMatBitmap.release();
            histBitmap.recycle();
//
//            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                public void run() {
//                    // here put code
//                    mTextureView.setOnClickListener(Camera2BasicFragment.this);
//                }
//            }, 500 /*delay time in milliseconds*/);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void drawColorGamut() {
//        vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
//
//        vec3 xyY = vec3(pass_Position.xy, 100.0);
//        vec3 XYZ = xyY.z * vec3(xyY.x / xyY.y, 1.0, (1.0 - xyY.x - xyY.y) / xyY.y);
//
//        mat3 XYZtoRGB = mat3(vec3( 0.032406f, -0.009689f,  0.000557f),
//                            vec3(-0.015372f,  0.018758f, -0.002040f),
//                            vec3(-0.004986f,  0.000415f,  0.010570f));
//
//        vec3 RGB = XYZtoRGB * XYZ;
//
//        if (RGB.x >= 0.0 && RGB.y >= 0.0 && RGB.z >= 0.0)
//        {
//            RGB /= max(RGB.x, max(RGB.y, RGB.z));
//            vec3 sRGB = pow(RGB, vec3(1.0 / 2.4));
//            color.xyz = sRGB;
//        }
    }


    double f144B;
    int DataY;
    double f145G;
    double L1;
    double L2;
    double Ri;
    double f146X;
    double f147Y;
    double f148Z;
    double a1;
    double a2;
    double b1;
    double b2;
    //    TextView bluer;


    int chave;
    String coName;
    //    TextView colorName;
//    TextView deltae;
    GraphView graph;
    int graphRaw;
    //    TextView green;
//    TextView hex;
//    TextView hhue;
//    TextView lab2;
//    String light;
    int porcentagem;
    //    TextView red;
    byte[] segura;
    LineGraphSeries<DataPoint> series;
    int seris;
    double var_B;
    double var_G;
    double var_R;
    double var_X;
    double var_Y;
    double var_Z;
    ArrayList<Double> xP = new ArrayList();
    ArrayList<Double> yP = new ArrayList();
    ArrayList<Double> yPn = new ArrayList();

    public void onPictureTaken(Bitmap bitmap) {
        this.porcentagem = 20;
        if (bitmap != null) {
            double var_R1;
//                int index = this.bmp.getPixel(this.bmp.getWidth() / 2, this.bmp.getHeight() / 2);
            int measureX = (int) sightView.getX() + 15;  //dpToPx(5);
            int measureY = (int) sightView.getY() + 15; // dpToPx(5);

//                Log.i(TAG, "cropX: " + cropX);
//                Log.i(TAG, "cropY: " + cropY);

            int index = bitmap.getPixel(measureX, measureY);

            int R1 = (index >> 16) & ACTION_MASK;  //MotionEventCompat.ACTION_MASK;
            int G1 = (index >> 8) & ACTION_MASK; // MotionEventCompat.ACTION_MASK;
            int B1 = index & ACTION_MASK; // MotionEventCompat.ACTION_MASK;
            this.Ri = (double) R1;
            this.f145G = (double) G1;
            this.f144B = (double) B1;
            DecimalFormat decimalFormat = new DecimalFormat("#,##0");
            String Rkk = decimalFormat.format(this.Ri);
            String Gkk = decimalFormat.format(this.f145G);
            String Bkk = decimalFormat.format(this.f144B);
            int Rkk2 = Integer.parseInt(String.valueOf(Rkk));
            int Gkk2 = Integer.parseInt(String.valueOf(Gkk));
            int Bkk2 = Integer.parseInt(String.valueOf(Bkk));
//            this.red.setBackgroundColor(index);
            float[] hsv = new float[3];
            Color.RGBToHSV(Rkk2, Gkk2, Bkk2, hsv);
            float huekk = hsv[0];
            float sat = hsv[1];
            float val = hsv[2];
//            this.green.setText(String.valueOf("R: " + Rkk2 + " G: " + Gkk2 + " B: " + Bkk2));
            String finHEX = "#" + Integer.toHexString(Color.rgb(Rkk2, Gkk2, Bkk2)).substring(2, 8);
//            this.hex.setText(String.valueOf("HEX: " + finHEX));
            this.var_R = this.Ri / 255.0d;
            this.var_G = this.f145G / 255.0d;
            this.var_B = this.f144B / 255.0d;
            if (this.var_R > 0.04045d) {
                var_R1 = (this.var_R + 0.055d) / 1.055d;
                this.var_R = Math.pow(var_R1, 2.4d);
            } else {
                this.var_R /= 12.92d;
            }
            if (this.var_G > 0.04045d) {
                var_R1 = (this.var_G + 0.055d) / 1.055d;
                this.var_G = Math.pow(var_R1, 2.4d);
            } else {
                this.var_G /= 12.92d;
            }
            if (this.var_B > 0.04045d) {
                var_R1 = (this.var_B + 0.055d) / 1.055d;
                this.var_B = Math.pow(var_R1, 2.4d);
            } else {
                this.var_B /= 12.92d;
            }
            this.var_R *= 100.0d;
            this.var_G *= 100.0d;
            this.var_B *= 100.0d;
            this.f146X = ((this.var_R * 0.4124d) + (this.var_G * 0.3576d)) + (this.var_B * 0.1805d);
            this.f147Y = ((this.var_R * 0.2126d) + (this.var_G * 0.7152d)) + (this.var_B * 0.0722d);
            this.f148Z = ((this.var_R * 0.0193d) + (this.var_G * 0.1192d)) + (this.var_B * 0.9505d);
            decimalFormat = new DecimalFormat("#,##0.000");
            String Xiz = decimalFormat.format(this.f146X);
            String Yiz = decimalFormat.format(this.f147Y);
            String Ziz = decimalFormat.format(this.f148Z);
            this.porcentagem = 30;
            this.var_X = this.f146X / 95.047d;
            this.var_Y = this.f147Y / 100.0d;
            this.var_Z = this.f148Z / 108.883d;
            if (this.var_X > 0.008856d) {
                this.var_X = Math.pow(this.var_X, 0.33333d);
            } else {
                this.var_X = (7.787d * this.var_X) + 0.0d;
            }
            if (this.var_Y > 0.008856d) {
                this.var_Y = Math.pow(this.var_Y, 0.33333d);
            } else {
                this.var_Y = (7.787d * this.var_Y) + 0.0d;
            }
            if (this.var_Z > 0.008856d) {
                this.var_Z = Math.pow(this.var_Z, 0.33333d);
            } else {
                this.var_Z = (7.787d * this.var_Z) + 0.0d;
            }
            double CIEL = Math.max(0.0d, (116.0d * this.var_Y) - 16.0d);
            double CIEa = 500.0d * (this.var_X - this.var_Y);
            double CIEb = 200.0d * (this.var_Y - this.var_Z);
            decimalFormat = new DecimalFormat("#,##0.0");
            String eli = decimalFormat.format(CIEL);
            String aa = decimalFormat.format(CIEa);
            String bb = decimalFormat.format(CIEb);
//            this.lab2.setText(String.valueOf("CIE L: " + eli + " a*: " + aa + " b*: " + bb));
            double cr = Math.sqrt(Math.pow(CIEa, 2.0d) + Math.pow(CIEb, 2.0d));
            String crom = new DecimalFormat("#,##0.00").format(cr);
            this.L1 = CIEL;
            this.a1 = CIEa;
            this.b1 = CIEb;
            double Var_H = Math.atan2(CIEb, CIEa);
            if (Var_H > 0.0d) {
                Var_H = (Var_H / PI) * 180.0d;
            } else {
                Var_H = 360.0d - ((Math.abs(Var_H) / PI) * 180.0d);
            }
            if (Var_H < 0.0d) {
                Var_H += 360.0d;
            } else if (Var_H >= 360.0d) {
                Var_H -= 360.0d;
            }
            this.porcentagem = 40;
            String hueei = String.format("%.1f", new Object[]{Float.valueOf(huekk)});
            if (huekk <= 6.0f) {
                this.coName = "Red";
            } else if (huekk > 350.0f) {
                this.coName = "Red";
            } else if (huekk > 6.0f && huekk <= 10.0f) {
                this.coName = "Red-Orange";
            } else if (huekk >= 340.0f && huekk <= 350.0f) {
                this.coName = "Pink-red";
            } else if (huekk >= 320.0f && huekk < 340.0f) {
                this.coName = "Magenta-Pink";
            } else if (huekk >= 290.0f && huekk < 320.0f) {
                this.coName = "Magenta";
            } else if (huekk >= 270.0f && huekk < 290.0f) {
                this.coName = "Blue-Magenta";
            } else if (huekk > 200.0f && huekk < 270.0f) {
                this.coName = "Blue";
            } else if (huekk > 177.0f && huekk <= 200.0f) {
                this.coName = "Cyan-Blue";
            } else if (huekk >= 155.0f && huekk <= 177.0f) {
                this.coName = "Cyan-Green";
            } else if (huekk >= 73.0f && huekk < 155.0f) {
                this.coName = "Green";
            } else if (huekk >= 55.0f && huekk < 73.0f) {
                this.coName = "Yellow-Green";
            } else if (huekk >= 47.0f && huekk < 55.0f) {
                this.coName = "Yellow";
            } else if (huekk >= 17.0f && huekk < 47.0f) {
                this.coName = "Orange-Brown";
            } else if (huekk < 10.0f || huekk >= 17.0f) {
                this.coName = "Color name";
            } else {
                this.coName = "Red-Orange-Brown";
            }
            if (cr <= 5.0d && CIEL > 40.0d && CIEL < 88.0d) {
                this.coName = "Light-Gray";
//                this.light = " ";
            } else if (cr <= 5.0d && CIEL >= 17.0d && CIEL <= 40.0d) {
                this.coName = "Dark-Gray";
//                this.light = " ";
            } else if (cr >= 4.0d && CIEL > 58.0d && CIEL <= 100.0d) {
//                this.light = "Light-";
            } else if (cr >= 6.0d && CIEL > 8.0d && CIEL <= 47.0d) {
//                this.light = "Dark-";
            } else if (cr <= 3.0d && CIEL > 88.0d) {
                this.coName = "White";
//                this.light = " ";
            } else if (cr <= 4.0d && CIEL >= 0.0d && CIEL <= 23.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (cr <= 5.0d && CIEL >= 0.0d && CIEL <= 18.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (cr <= 6.0d && CIEL >= 0.0d && CIEL < 17.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (CIEL == 0.0d) {
//                this.light = "Very Dark- ";
            } else {
//                this.light = " ";
            }
//            String NomeDaCor = this.light + this.coName;
//            this.colorName.setText(String.valueOf(NomeDaCor));
            decimalFormat = new DecimalFormat("#,##0.0");
//            this.hhue.setText(String.valueOf("HUE: " + hueei + "º " + "Chroma: " + crom));
//            this.deltae.setText(String.valueOf("ΔE*: " + new DecimalFormat("#,##0.00").format(Math.sqrt((Math.pow(Math.max(this.L1, this.L2) - Math.min(this.L1, this.L2), 2.0d) + Math.pow(Math.max(this.a1, this.a2) - Math.min(this.a1, this.a2), 2.0d)) + Math.pow(Math.max(this.b1, this.b2) - Math.min(this.b1, this.b2), 2.0d)))));
//                Person person = new Person(NomeDaCor, eli, aa, bb, crom, hueei, String.valueOf(Rkk2), String.valueOf(Gkk2), String.valueOf(Bkk2), finHEX);
//                this.dbhelper = new DatabaseHelper(this.getApplicationContext());
//                this.dbhelper.addPersonData(person);
            this.porcentagem = 50;
            this.xP = new ArrayList();
            this.yP = new ArrayList();
//            r77 = new double[5];

            try {
                PolynomialSplineFunction splines = new SplineInterpolator().interpolate(new double[]{450.0d, 470.0d, 525.0d, 665.0d, 715.0d}, new double[]{0.0d, (double) Bkk2, (double) Gkk2, (double) Rkk2, 0.0d});
                for (int i = 450; i < 715; i++) {
//                    double interpolationX = (double) i;
                    this.xP.add(Double.valueOf((double) i));
                    this.yP.add(Double.valueOf(splines.value((double) i)));
                }
                for (int norm = 0; norm < this.yP.size(); norm++) {
                    double eMax = ((Double) Collections.max(this.yP)).doubleValue();
                    double eMin = ((Double) Collections.min(this.yP)).doubleValue();
                    this.yPn.add(Double.valueOf((((Double) this.yP.get(norm)).doubleValue() - eMin) / (eMax - eMin)));
                }
            } catch (ArgumentOutsideDomainException aode) {
                aode.printStackTrace();
            }
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(0);
            this.graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));
            this.graph.getGridLabelRenderer().setLabelFormatter(new C03511());
            this.graph.getGridLabelRenderer().setHorizontalAxisTitle("nm");
            this.graph.getGridLabelRenderer().setNumVerticalLabels(3);
            this.series = new LineGraphSeries(this.generateData());
            this.yPn.clear();
            this.series.setThickness(2);
            this.series.setColor(Color.rgb(Rkk2, Gkk2, Bkk2));
            if (this.seris == 3) {
                this.graph.removeAllSeries();
                this.seris = 0;
            }
            this.graph.addSeries(this.series);
            this.seris++;
//            this.graph.getViewport().setScalable(true);
//            this.graph.getViewport().setXAxisBoundsStatus(Viewport.AxisBoundsStatus.AUTO_ADJUSTED);
//            this.graph.getViewport().setYAxisBoundsManual(true);
//            this.graph.getViewport().setMaxY((double) this.graphRaw);
//            this.graph.getViewport().setMinY(0.0d);
            this.L2 = 0.0d;
            this.a2 = 0.0d;
            this.b2 = 0.0d;
            this.porcentagem = 75;
            this.porcentagem = 100;
        }

        this.L2 = this.L1;
        this.a2 = this.a1;
        this.b2 = this.b1;
        this.L1 = 0.0d;
        this.a1 = 0.0d;
        this.b1 = 0.0d;

        this.segura = null;
    }

    class C03511 extends DefaultLabelFormatter {
        C03511() {
        }

        public String formatLabel(double value, boolean isValueX) {
            NumberFormat nf;
            if (isValueX) {
                nf = NumberFormat.getInstance();
                nf.setMaximumFractionDigits(0);
                nf.setMaximumIntegerDigits(2);
                return super.formatLabel(value, isValueX);
            }
            nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(0);
            nf.setMaximumIntegerDigits(2);
            return super.formatLabel(value, isValueX);
        }
    }

    private DataPoint[] generateData() {
        this.xP.size();
        this.yP.size();
        DataPoint[] values;
        int i;
        if (this.DataY == 1) {
            values = new DataPoint[this.xP.size()];
            for (i = 0; i < this.xP.size(); i++) {
                values[i] = new DataPoint(((Double) this.xP.get(i)).doubleValue(), ((Double) this.yPn.get(i)).doubleValue());
            }
            return values;
        }
        values = new DataPoint[this.xP.size()];
        for (i = 0; i < this.xP.size(); i++) {
            values[i] = new DataPoint(((Double) this.xP.get(i)).doubleValue(), ((Double) this.yPn.get(i)).doubleValue());
        }
        return values;
    }
}

