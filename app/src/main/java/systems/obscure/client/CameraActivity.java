package systems.obscure.client;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
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
public class CameraActivity extends PassphraseRequiredNoActionBarActivity {
    private FrameLayout mFrame;
    private PreviewSurfaceView mPreview;
    private int camNum;
    private MasterSecret masterSecret;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        Intent intent = getIntent();
        camNum = intent.getIntExtra("cameraNum", 0);
        masterSecret = intent.getParcelableExtra("master_secret");

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mFrame = (FrameLayout)findViewById(R.id.fullscreen_camera);

    }

    @Override
    public void onResume() {
        super.onResume();
        newCamera();
    }

    @Override
    public void onDestroy() {
        MemoryCleaner.clean(masterSecret);
        super.onDestroy();
    }

    private void newCamera() {
        mFrame.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        CameraActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                FrameLayout.LayoutParams vgParam = new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT);
                                vgParam.gravity = Gravity.CENTER;
                                mPreview = new PreviewSurfaceView(mFrame.getContext(), camNum);
                                mPreview.setKeepScreenOn(true);
                                mFrame.addView(mPreview, 0, vgParam);
                            }
                        });

                    }
                }).start();
            }
        }, 50);
    }

    public void switchCamera(View view) {
        if(camNum==0)
            camNum = 1;
        else
            camNum = 0;
        mFrame.removeView(mPreview);
        mPreview.destroy();
        newCamera();
    }

    public void takePicture(View view) {
        mPreview.autoFocus(focusCallback);
    }

    Camera.PictureCallback bitmap = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            System.out.println(data.length);
            Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);

            pic = rotate(pic, 90);

            Globals.lastImageTaken = pic;

            Intent picIntent = new Intent(CameraActivity.this, PictureActivity.class);
            startActivity(picIntent);
        }
    };

    public void viewMessages(View view) {
        Intent messages = new Intent(this, ConversationListActivity.class);
        messages.putExtra("master_secret", masterSecret);
        startActivity(messages);
    }

    public void viewContacts(View view) {

    }

    Camera.PictureCallback raw = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    };

    Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            mPreview.takePic(shutterCallback, raw, bitmap);
        }
    };

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {

        }
    };

    // from https://github.com/devcelebi/Kut-Camera/tree/master/KutCamera
    public static Bitmap rotate(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(),
                source.getHeight(), matrix, false);
    }

    @Override
    public void onMasterSecretCleared() {
        startActivity(new Intent(this, RoutingActivity.class));
        super.onMasterSecretCleared();
    }
}
