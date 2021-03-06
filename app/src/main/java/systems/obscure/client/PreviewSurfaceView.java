package systems.obscure.client;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * Created by charles on 12/1/14.
 */
public class PreviewSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    SurfaceHolder mHolder;
    Camera mCamera;
    List<Size> mSupportedPreviewSizes;
    int cameraNum;

    PreviewSurfaceView(Context context, int camNum) {//Camera cam,
        super(context);
//        mHolder = getHolder();
//        mHolder.addCallback(this);
////        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        safeCameraOpen(camNum);
        safeCameraOpen(camNum);


        List<Size> localSizes = mCamera.getParameters().getSupportedPreviewSizes();
        mSupportedPreviewSizes = localSizes;
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (mCamera == null) { return; }

        try {
            mCamera.stopPreview();
        } catch (Exception e){
        }

        try {
//            Camera.Parameters parameters = mCamera.getParameters();
//            parameters.setPreviewSize(mSupportedPreviewSizes.get(0).width, mSupportedPreviewSizes.get(0).height);
//            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
        requestLayout();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        stopPreviewAndFreeCamera();
    }

    public void destroy() {
        surfaceDestroyed(getHolder());
    }

    /**
     * When this function returns, mCamera will be null.
     */
    private void stopPreviewAndFreeCamera() {

        if (mCamera != null) {
            // Call stopPreview() to stop updating the preview surface.
            mCamera.stopPreview();

            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            mCamera.release();

            mCamera = null;
        }

    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            stopPreviewAndFreeCamera();
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e(getContext().getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

//    PreviewSurfaceView(Context context, AttributeSet attributeSet, int camNum) {
//        super(context, attributeSet);
//        mHolder = getHolder();
//        mHolder.addCallback(this);
//        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        safeCameraOpen(camNum);
//    }
//
//    PreviewSurfaceView(Context context, AttributeSet attributeSet, int num, int camNum) {
//        super(context, attributeSet, num);
//        mHolder = getHolder();
//        mHolder.addCallback(this);
//        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        safeCameraOpen(camNum);
//    }

//    private void init(int camNum) {
//        cameraNum = camNum;
//        safeCameraOpen(cameraNum);


//        List<Size> localSizes = mCamera.getParameters().getSupportedPreviewSizes();
//        mSupportedPreviewSizes = localSizes;
//        mCamera.getParameters().
//    }


//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
////        if (mCamera == null) { init(cameraNum); }
////
////        try {
////            mCamera.stopPreview();
////        } catch (Exception e){
////        }
////
////        try {
////            mCamera.setPreviewDisplay(mHolder);
////            mCamera.setDisplayOrientation(90);
////            mCamera.startPreview();
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
////        requestLayout();
//    }
//
//    @Override
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//
////        stopPreviewAndFreeCamera();
//        if (mCamera == null) { safeCameraOpen(cameraNum); }
////        safeCameraOpen(cameraNum);
////        try {
//            mCamera.stopPreview();
////        } catch (Exception e){
////        }
//
//        try {
////            Camera.Parameters parameters = mCamera.getParameters();
////            parameters.setPreviewSize(mSupportedPreviewSizes.get(0).width, mSupportedPreviewSizes.get(0).height);
////            mCamera.setParameters(parameters);
//            mCamera.setPreviewDisplay(holder);
//            mCamera.setDisplayOrientation(90);
//            mCamera.startPreview();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        requestLayout();
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder holder) {
//        // Surface will be destroyed when we return, so stop the preview.
//        stopPreviewAndFreeCamera();
//    }
//
//    public void destroy() {
//        surfaceDestroyed(getHolder());
//    }
//
//    /**
//     * When this function returns, mCamera will be null.
//     */
//    private void stopPreviewAndFreeCamera() {
//
//        if (mCamera != null) {
//            // Call stopPreview() to stop updating the preview surface.
//            mCamera.stopPreview();
//
//            // Important: Call release() to release the camera for use by other
//            // applications. Applications should release the camera immediately
//            // during onPause() and re-open() it during onResume()).
//            mCamera.release();
//
//            mCamera = null;
//        }
//        System.out.println("Camera Stopped and Freed!!!");
//
//    }
//
//    private boolean safeCameraOpen(int id) {
//        cameraNum = id;
//        boolean qOpened = false;
//
//        if(mCamera != null) {
//            try {
//                mCamera.reconnect();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            return true;
//        }
//        try {
//            stopPreviewAndFreeCamera();
//            mCamera = Camera.open(id);
//            qOpened = (mCamera != null);
//        } catch (RuntimeException e) {
//            Log.e(getContext().getString(R.string.app_name), "failed to open Camera");
//            e.printStackTrace();
//        }
//
//        return qOpened;
//    }

    public void autoFocus(Camera.AutoFocusCallback auto) {
        mCamera.autoFocus(auto);
    }

    public void takePic(Camera.ShutterCallback shut, Camera.PictureCallback raw, Camera.PictureCallback jpeg) {
        mCamera.takePicture(shut, raw, jpeg);
    }



}
