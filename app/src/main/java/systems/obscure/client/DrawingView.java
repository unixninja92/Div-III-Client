package systems.obscure.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
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
    private Bitmap bitmap;
    private Bitmap canvasBitmap;//for later saving the image

    private BitmapDrawable bitmapDrawable;

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

//        int bitmapW = bitmapDrawable.getIntrinsicWidth();
//        int bitmapH = bitmapDrawable.getIntrinsicHeight();
//        Point size = new Point();
//        getDisplay().getSize(size);
//        float scaleW = bitmapW / (float) size.x;
//        float scaleH = bitmapH / (float) size.y;
//        Paint paint = bitmapDrawable.getPaint();
//
//        if (isAspectFit) {
//            float scale = Math.max(scaleW, scaleH);
//            drawCanvas.save();
//            bitmapW /= scale;
//            bitmapH /= scale;
//            Rect drawRegion = new Rect();
//            drawRegion.set(canvasBitmap. + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2);
//            bitmapDrawable.setBounds(drawRegion);
//            try {
//                bitmapDrawable.setAlpha(alpha);
//                bitmapDrawable.draw(drawCanvas);
//            } catch (Exception e) {
//            }
//            drawCanvas.restore();
//        } else {
//            if (Math.abs(scaleW - scaleH) > 0.00001f) {
//                drawCanvas.save();
//                drawCanvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);
//
//                if (bitmapW / scaleH > imageW) {
//                    bitmapW /= scaleH;
//                    drawRegion.set(imageX - (bitmapW - imageW) / 2, imageY, imageX + (bitmapW + imageW) / 2, imageY + imageH);
//                } else {
//                    bitmapH /= scaleW;
//                    drawRegion.set(imageX, imageY - (bitmapH - imageH) / 2, imageX + imageW, imageY + (bitmapH + imageH) / 2);
//                }
//                bitmapDrawable.setBounds(drawRegion);
//                if (isVisible) {
//                    try {
//                        bitmapDrawable.setAlpha(alpha);
//                        bitmapDrawable.draw(canvas);
//                    } catch (Exception e) {
//                        if (bitmapDrawable == currentImage && currentKey != null) {
//                            ImageLoader.getInstance().removeImage(currentKey);
//                            currentKey = null;
//                        } else if (bitmapDrawable == currentThumb && currentThumbKey != null) {
//                            ImageLoader.getInstance().removeImage(currentThumbKey);
//                            currentThumbKey = null;
//                        }
//                        setImage(currentImageLocation, currentHttpUrl, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentCacheOnly);
//                        FileLog.e("tmessages", e);
//                    }
//                }
//
//                canvas.restore();
//            } else {
//                drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
//                bitmapDrawable.setBounds(drawRegion);
//                if (isVisible) {
//                    try {
//                        bitmapDrawable.setAlpha(alpha);
//                        bitmapDrawable.draw(canvas);
//                    } catch (Exception e) {
//                        if (bitmapDrawable == currentImage && currentKey != null) {
//                            ImageLoader.getInstance().removeImage(currentKey);
//                            currentKey = null;
//                        } else if (bitmapDrawable == currentThumb && currentThumbKey != null) {
//                            ImageLoader.getInstance().removeImage(currentThumbKey);
//                            currentThumbKey = null;
//                        }
//                        setImage(currentImageLocation, currentHttpUrl, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentCacheOnly);
//                        FileLog.e("tmessages", e);
//                    }
//                }
//            }
//        }

        canvasBitmap = Bitmap.createScaledBitmap(bitmap, w, h, false);
//        if(canvasBitmap==null)
//            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
//        else
//            canvasBitmap = Bitmap.createBitmap(canvasBitmap, 0, 0, w, h);
        drawCanvas = new Canvas(canvasBitmap);
    }
    int touchX =0;
    int touchY =0;

    @Override
    protected void onDraw(Canvas canvas) {
    //draw view
        canvas.drawBitmap(canvasBitmap, touchX, touchY, canvasPaint);
//        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    //detect user touch
        if(pen || erase) {
            touchX = (int)event.getX();
            touchY = (int) event.getY();
//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN://finger touches screen
//                    drawPath.moveTo(touchX, touchY);
//                    break;
//                case MotionEvent.ACTION_MOVE://finger moves on screen
//                    drawPath.lineTo(touchX, touchY);
//                    break;
//                case MotionEvent.ACTION_UP://finger is removed from screen
//                    drawCanvas.drawPath(drawPath, drawPaint);
//                    drawPath.reset();
//                    break;
//                default:
//                    return false;
//            }
//            invalidate();
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

        this.bitmap = bitmap;
        canvasBitmap = bitmap;
        bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
//        bitmapDrawable.
        drawCanvas = new Canvas(canvasBitmap);
        invalidate();
    }

    public void saveBitmap(OutputStream out){
        canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
}
