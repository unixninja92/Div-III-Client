package systems.obscure.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.OutputStream;

/**
 * Created by charles on 4/20/14.
 * Based on http://code.tutsplus.com/tutorials/android-sdk-create-a-drawing-app-interface-creation--mobile-19021
 */
public class DrawingView extends View {
    //drawing path
    private Path drawPath;
    //drawing and canvas paint
    private Paint drawPaint, canvasPaint;
    //canvas
    private Canvas drawCanvas;
    //canvas bitmap
    private Bitmap canvasBitmap;//for later saving the image

    //currently selected tool
    private boolean pen = false;
//    private boolean brush = false;
    private boolean erase = false;

    //brush size
    private int size = 10;


    public DrawingView(Context context, AttributeSet attrs){
        super(context, attrs);
        setupDrawing();
    }

    private void setupDrawing(){
    //get drawing area setup for interaction
        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(0xFF000000);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(size);
        drawPaint.setStyle(Paint.Style.STROKE);
//        drawPaint.setStrokeJoin(Paint.Join.ROUND);
//        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        canvasPaint = new Paint();//Paint.DITHER_FLAG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    //view given size, resizes canvas
        super.onSizeChanged(w, h, oldw, oldh);
//        if(canvasBitmap==null)
//            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
//        else
//            canvasBitmap = Bitmap.createBitmap(canvasBitmap, 0, 0, w, h);
        drawCanvas = new Canvas(canvasBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
    //draw view
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    //detect user touch
        if(pen || erase) {
            float touchX = event.getX();
            float touchY = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN://finger touches screen
                    drawPath.moveTo(touchX, touchY);
                    break;
                case MotionEvent.ACTION_MOVE://finger moves on screen
                    drawPath.lineTo(touchX, touchY);
                    break;
                case MotionEvent.ACTION_UP://finger is removed from screen
                    drawCanvas.drawPath(drawPath, drawPaint);
                    drawPath.reset();
                    break;
                default:
                    return false;
            }
            invalidate();
        }
        return true;
    }

    /*
    * Sets up Eraser tool
     */
    public void setErase() {
        erase = true;
        pen = false;
//        brush = false;

        drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        drawPaint.setShadowLayer(0, 0, 0, 0xFF000000);
    }

//    /*
//    * Sets up Brush tool
//     */
//    public void setBrush() {
//        brush = true;
//        pen = false;
//        erase = false;
//
//        drawPaint.setXfermode(null);
//        drawPaint.setShadowLayer((int)(size/1.5), 0, 0, 0xFF000000);
//    }

    /*
    * Sets up Pen tool
     */
    public void setPen() {
        pen = true;
//        brush = false;
        erase = false;

        drawPaint.setXfermode(null);
        drawPaint.setShadowLayer(0, 0, 0, 0xFF000000);
    }

    public void setNone() {
        pen = false;
//        brush = false;
        erase = false;
    }

//    /*
//    * Sets stroke size of currently selected tool
//     */
//    public void setSize(int newSize) {
//        switch (newSize){
//            case 0: size = 10;
//                break;
//            case 1: size = 20;
//                break;
//            case 2: size = 40;
//                break;
//            default: size = 10;
//        }
//        drawPaint.setStrokeWidth(size);
//    }

    public void loadBitmap(Bitmap bitmap){
        canvasBitmap = bitmap;
        drawCanvas = new Canvas(canvasBitmap);
        invalidate();
    }

    public void saveBitmap(OutputStream out){
        canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
}
