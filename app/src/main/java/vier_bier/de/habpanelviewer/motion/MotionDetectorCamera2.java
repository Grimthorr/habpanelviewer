package vier_bier.de.habpanelviewer.motion;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Motion detection using Camera2 API.
 */
public class MotionDetectorCamera2 extends AbstractMotionDetector<LumaData> {
    private static final String TAG = "MotionDetectorCamera2";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Activity activity;
    private CameraManager camManager;
    private TextureView previewView;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    private CameraDevice mCamera;

    public MotionDetectorCamera2(CameraManager manager, MotionListener l, Activity act) throws CameraAccessException {
        super(l);

        Log.d(TAG, "instantiating motion detection");

        camManager = manager;
        activity = act;
    }

    @Override
    protected LumaData getPreviewLumaData() {
        return fPreview.getAndSet(null);
    }

    @Override
    protected synchronized void startDetection(TextureView textureView, int deviceDegrees) throws CameraAccessException {
        previewView = textureView;
        super.startDetection(textureView, deviceDegrees);
    }

    @Override
    protected String createCamera(int deviceDegrees) throws CameraAccessException {
        for (String camId : camManager.getCameraIdList()) {
            CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                Log.d(TAG, "front-facing mCamera found: " + camId);
                return camId;
            }
        }

        throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Could not find front facing mCamera!");
    }

    protected void stopPreview() {
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
    }

    protected void startPreview() {
        try {
            CameraCharacteristics characteristics
                    = camManager.getCameraCharacteristics(String.valueOf(mCameraId));
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            chooseOptimalSize(map.getOutputSizes(ImageFormat.YUV_420_888), 640, 480, new Size(4, 3));

            camManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "mCamera opened: " + cameraDevice);
                    mCamera = cameraDevice;
                    createCameraPreviewSession(previewView);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "mCamera disconnected: " + cameraDevice);
                    stopDetection();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    Log.e(TAG, "mCamera error: " + cameraDevice + ", error code: " + i);
                    stopDetection();
                }
            }, null);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Could not open camera", e);
        }
    }

    private void configureTransform(TextureView textureView) {
        if (null == textureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, textureView.getWidth(), textureView.getHeight());
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) textureView.getHeight() / mPreviewSize.getHeight(),
                    (float) textureView.getWidth() / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
        }
        matrix.postRotate(-90 * rotation, centerX, centerY);
        textureView.setTransform(matrix);
    }

    private void createCameraPreviewSession(final TextureView previewView) {
        try {
            Log.v(TAG, "preview image size is " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight());

            configureTransform(previewView);

            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image i = reader.acquireLatestImage();

                    if (i != null) {
                        if (fPreview.get() == null) {
                            Log.v(TAG, "preview image available: size " + i.getWidth() + "x" + i.getHeight());
                        }

                        ByteBuffer luma = i.getPlanes()[0].getBuffer();
                        final byte[] data = new byte[luma.capacity()];
                        luma.get(data);

                        setPreview(new LumaData(data, i.getWidth(), i.getHeight(), mBoxes));
                        i.close();
                    }
                }
            }, null);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            Surface previewSurface = new Surface(surface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            ArrayList<Surface> surfaces = new ArrayList<>();
            surfaces.add(mImageReader.getSurface());
            surfaces.add(previewSurface);

            // Here, we create a CameraCaptureSession for mCamera preview.
            mCamera.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Capture session configured");

                            // The mCamera is already closed
                            if (null == mCamera) {
                                Log.e(TAG, "Capture session has no camera");
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for mCamera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the mCamera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Could not create preview request", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "Could not create capture session");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not create preview", e);
        }
    }

    @Override
    protected int getSensorOrientation() {
        try {
            CameraCharacteristics characteristics
                    = camManager.getCameraCharacteristics(String.valueOf(mCameraId));
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Couldn't find out camera sensor orientation");
        }

        return 0;
    }
}
