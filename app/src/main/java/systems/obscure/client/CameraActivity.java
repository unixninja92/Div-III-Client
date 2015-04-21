package systems.obscure.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.PassphraseRequiredNoActionBarActivity;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import systems.obscure.client.util.SystemUiHider;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 *
 * @see SystemUiHider
 */
public class CameraActivity extends PassphraseRequiredNoActionBarActivity
        implements SnapSecureCameraFragment.Contract{
    private FrameLayout mFrame;
    private int camNum;
    private MasterSecret masterSecret;
    private SnapSecureCameraFragment fragment;

    private int deviceHeight;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        fragment = (SnapSecureCameraFragment) getFragmentManager().findFragmentById(R.id.camera_preview);

        Intent intent = getIntent();
        camNum = intent.getIntExtra("cameraNum", 0);
        masterSecret = intent.getParcelableExtra("master_secret");
    }

    @Override
    public void onResume() {
        super.onResume();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mFrame = (FrameLayout)findViewById(R.id.fullscreen_camera);

        Globals.lastImageTaken = null;

//        newCamera();
    }

    @Override
    public void onPause() {
//        mPreview.surfaceDestroyed();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        MemoryCleaner.clean(masterSecret);
        super.onDestroy();
    }

//    private void newCamera() {
//        System.out.println("WTF's the problem?? "+mFrame==null);
//        Runnable thread = new Runnable() {
//            @Override
//            public void run() {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        CameraActivity.this.runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                FrameLayout.LayoutParams vgParam = new FrameLayout.LayoutParams(
//                                        FrameLayout.LayoutParams.MATCH_PARENT,
//                                        FrameLayout.LayoutParams.MATCH_PARENT);
//                                vgParam.gravity = Gravity.CENTER;
//                                mPreview = new PreviewSurfaceView(mFrame.getContext(), camNum);
//                                mPreview.setKeepScreenOn(true);
//                                mFrame.addView(mPreview, 0, vgParam);
//                            }
//                        });
//
//                    }
//                }).start();
//            }
//        };
//        mFrame.postDelayed(thread, 50);
//    }

    public void switchCamera(View view) {
        if(camNum==0)
            camNum = 1;
        else
            camNum = 0;
//        mFrame.removeView(mPreview);
//        mPreview.destroy();
//        newCamera();
    }

    public void takePicture(View view) {
//        mPreview.autoFocus(focusCallback);
        fragment.takeSimplePicture();
    }

//    Camera.PictureCallback bitmap = new Camera.PictureCallback() {
//        @Override
//        public void onPictureTaken(byte[] data, Camera camera) {
//            System.out.println(data.length);
//            Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
//
//            pic = rotate(pic, 90);
//
//            Globals.lastImageTaken = pic;
//
//            Intent picIntent = new Intent(CameraActivity.this, PictureActivity.class);
//            startActivity(picIntent);
//        }
//    };

    public void viewMessages(View view) {
        Intent messages = new Intent(this, ConversationListActivity.class);
        messages.putExtra("master_secret", masterSecret);
        startActivity(messages);
    }

    public void viewContacts(View view) {
        Intent contacts = new Intent(this, ContactsListActivity.class);
        contacts.putExtra("master_secret", masterSecret);
        startActivity(contacts);
    }

//    Camera.PictureCallback raw = new Camera.PictureCallback() {
//        @Override
//        public void onPictureTaken(byte[] data, Camera camera) {
//
//        }
//    };

//    Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
//        @Override
//        public void onAutoFocus(boolean success, Camera camera) {
//            mPreview.takePic(shutterCallback, raw, bitmap);
//        }
//    };

    // from https://github.com/devcelebi/Kut-Camera/tree/master/KutCamera
//    public static Bitmap rotate(Bitmap source, float angle) {
//        Matrix matrix = new Matrix();
//        matrix.postRotate(angle);
//        return Bitmap.createBitmap(source, 0, 0, source.getWidth(),
//                source.getHeight(), matrix, false);
//    }

    @Override
    public void onMasterSecretCleared() {
        startActivity(new Intent(this, RoutingActivity.class));
        super.onMasterSecretCleared();
    }

    @Override
    public int getCameraNum() {
        return camNum;
    }

    @Override
    public void setCameraNum(int cameraNum) {
        camNum = cameraNum;
    }
}
