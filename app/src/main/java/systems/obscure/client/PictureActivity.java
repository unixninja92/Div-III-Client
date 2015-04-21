package systems.obscure.client;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.thoughtcrime.securesms.PassphraseRequiredNoActionBarActivity;


public class PictureActivity extends PassphraseRequiredNoActionBarActivity {
//    DrawingView drawingView;
    ImageView imageView;
//    Button penButton;
//    Button eraseButton;

    static byte[] imageToShow=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture);

//        Intent intent = getIntent();
//        byte[] bytes = intent.getByteArrayExtra("image");
        Bitmap image = Globals.lastImageTaken;


        imageView = (ImageView) findViewById(R.id.imageReview);
        imageView.setImageBitmap(image);
//        drawingView = (DrawingView) findViewById(R.id.imageReview);
//        drawingView.loadBitmap(image);
//        imageView.setScaleType(ImageView.ScaleType.FIT_XY);

//        penButton = (Button) findViewById(R.id.penButton);
//        eraseButton = (Button) findViewById(R.id.eraseButton);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_picture, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void up(View view) {
        NavUtils.navigateUpFromSameTask(this);
    }

//    public void penSelect(View view) {
//        if(penButton.isSelected()){
//            penButton.setSelected(false);
//            drawingView.setNone();
//        }
//        else {
//            penButton.setSelected(true);
//            drawingView.setPen();
//        }
//    }
//
//    public void eraseSelect(View view) {
//        if(eraseButton.isSelected()){
//            eraseButton.setSelected(false);
//            drawingView.setNone();
//        }
//        else {
//            eraseButton.setSelected(true);
//            drawingView.setErase();
//        }
//    }
}
