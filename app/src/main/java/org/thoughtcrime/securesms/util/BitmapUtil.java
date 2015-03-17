package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.BitmapDecodingException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

//import org.thoughtcrime.securesms.mms.PartAuthority;

public class BitmapUtil {
  private static final String TAG = org.thoughtcrime.securesms.util.BitmapUtil.class.getSimpleName();

  private static final int MAX_COMPRESSION_QUALITY  = 95;
  private static final int MIN_COMPRESSION_QUALITY  = 50;
  private static final int MAX_COMPRESSION_ATTEMPTS = 4;

  public static byte[] createScaledBytes(Context context, MasterSecret masterSecret, Uri uri, int maxWidth, int maxHeight, int maxSize)
      throws IOException, org.thoughtcrime.securesms.util.BitmapDecodingException
  {
    Bitmap bitmap = null;
//    try {
//      bitmap = createScaledBitmap(context, masterSecret, uri, maxWidth, maxHeight, false);
//    } catch(OutOfMemoryError oome) {
//      Log.w(TAG, "OutOfMemoryError when scaling precisely, doing rough scale to save memory instead");
//      bitmap = createScaledBitmap(context, masterSecret, uri, maxWidth, maxHeight, true);
//    }
    int quality         = MAX_COMPRESSION_QUALITY;
    int attempts        = 0;

    ByteArrayOutputStream baos;

    do {
      baos = new ByteArrayOutputStream();
      bitmap.compress(CompressFormat.JPEG, quality, baos);

      quality = Math.max((quality * maxSize) / baos.size(), MIN_COMPRESSION_QUALITY);
    } while (baos.size() > maxSize && attempts++ < MAX_COMPRESSION_ATTEMPTS);

    Log.w(TAG, "createScaledBytes(" + uri + ") -> quality " + Math.min(quality, MAX_COMPRESSION_QUALITY) + ", " + attempts + " attempt(s)");

    bitmap.recycle();

    if (baos.size() <= maxSize) return baos.toByteArray();
    else                        throw new IOException("Unable to scale image below: " + baos.size());
  }

//  public static Bitmap createScaledBitmap(Context context, MasterSecret masterSecret, Uri uri, int maxWidth, int maxHeight)
//      throws BitmapDecodingException, IOException
//  {
//    Bitmap bitmap;
//    try {
//      bitmap = createScaledBitmap(context, masterSecret, uri, maxWidth, maxHeight, false);
//    } catch(OutOfMemoryError oome) {
//      Log.w(TAG, "OutOfMemoryError when scaling precisely, doing rough scale to save memory instead");
//      bitmap = createScaledBitmap(context, masterSecret, uri, maxWidth, maxHeight, true);
//    }
//
//    return bitmap;
//  }

//  private static Bitmap createScaledBitmap(Context context, MasterSecret masterSecret, Uri uri, int maxWidth, int maxHeight, boolean constrainedMemory)
//      throws IOException, BitmapDecodingException
//  {
//    InputStream is = PartAuthority.getPartStream(context, masterSecret, uri);
//    if (is == null) throw new IOException("Couldn't obtain InputStream");
//    return createScaledBitmap(is,
//                              PartAuthority.getPartStream(context, masterSecret, uri),
//                              PartAuthority.getPartStream(context, masterSecret, uri),
//                              maxWidth, maxHeight, constrainedMemory);
//  }

//  private static Bitmap createScaledBitmap(InputStream measure, InputStream orientationStream, InputStream data,
//                                           int maxWidth, int maxHeight, boolean constrainedMemory)
//      throws BitmapDecodingException
//  {
//    Bitmap bitmap = createScaledBitmap(measure, data, maxWidth, maxHeight, constrainedMemory);
//    return fixOrientation(bitmap, orientationStream);
//  }

  private static Bitmap createScaledBitmap(InputStream measure, InputStream data, int maxWidth, int maxHeight,
                                           boolean constrainedMemory)
      throws org.thoughtcrime.securesms.util.BitmapDecodingException
  {
    final BitmapFactory.Options options = getImageDimensions(measure);
    return createScaledBitmap(data, maxWidth, maxHeight, options, constrainedMemory);
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data, float scale)
      throws org.thoughtcrime.securesms.util.BitmapDecodingException
  {
    final BitmapFactory.Options options = getImageDimensions(measure);
    final int outWidth = (int)(options.outWidth * scale);
    final int outHeight = (int)(options.outHeight * scale);
    Log.w(TAG, "creating scaled bitmap with scale " + scale + " => " + outWidth + "x" + outHeight);
    return createScaledBitmap(data, outWidth, outHeight, options, false);
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data, int maxWidth, int maxHeight)
      throws org.thoughtcrime.securesms.util.BitmapDecodingException
  {
    return createScaledBitmap(measure, data, maxWidth, maxHeight, false);
  }

  private static Bitmap createScaledBitmap(InputStream data, int maxWidth, int maxHeight,
                                           BitmapFactory.Options options, boolean constrainedMemory)
      throws org.thoughtcrime.securesms.util.BitmapDecodingException
  {
    final int imageWidth  = options.outWidth;
    final int imageHeight = options.outHeight;

    int scaler = 1;
    int scaleFactor = (constrainedMemory ? 1 : 2);
    while ((imageWidth / scaler / scaleFactor >= maxWidth) && (imageHeight / scaler / scaleFactor >= maxHeight)) {
      scaler *= 2;
    }

    options.inSampleSize       = scaler;
    options.inJustDecodeBounds = false;

    BufferedInputStream is = new BufferedInputStream(data);
    Bitmap roughThumbnail  = BitmapFactory.decodeStream(is, null, options);
    try {
      is.close();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException thrown when closing an images InputStream", ioe);
    }
    Log.w(TAG, "rough scale " + (imageWidth) + "x" + (imageHeight) +
               " => " + (options.outWidth) + "x" + (options.outHeight));
    if (roughThumbnail == null) {
      throw new BitmapDecodingException("Decoded stream was null.");
    }
    if (constrainedMemory) {
      return roughThumbnail;
    }

    if (options.outWidth > maxWidth || options.outHeight > maxHeight) {
      final float aspectWidth, aspectHeight;

      if (imageWidth == 0 || imageHeight == 0) {
        aspectWidth = maxWidth;
        aspectHeight = maxHeight;
      } else if (options.outWidth >= options.outHeight) {
        aspectWidth = maxWidth;
        aspectHeight = (aspectWidth / options.outWidth) * options.outHeight;
      } else {
        aspectHeight = maxHeight;
        aspectWidth = (aspectHeight / options.outHeight) * options.outWidth;
      }

      final int fineWidth  = Math.round(aspectWidth);
      final int fineHeight = Math.round(aspectHeight);

      Log.w(TAG, "fine scale " + options.outWidth + "x" + options.outHeight +
                 " => " + fineWidth + "x" + fineHeight);
      Bitmap scaledThumbnail = null;
      try {
        scaledThumbnail = Bitmap.createScaledBitmap(roughThumbnail, fineWidth, fineHeight, true);
      } finally {
        if (roughThumbnail != scaledThumbnail) roughThumbnail.recycle();
      }
      return scaledThumbnail;
    } else {
      return roughThumbnail;
    }
  }

//  private static Bitmap fixOrientation(Bitmap bitmap, InputStream orientationStream) {
//    final int orientation = Exif.getOrientation(orientationStream);
//
//    if (orientation != 0) {
//      return rotateBitmap(bitmap, orientation);
//    } else {
//      return bitmap;
//    }
//  }

  private static Bitmap rotateBitmap(Bitmap bitmap, int angle) {
    Matrix matrix = new Matrix();
    matrix.postRotate(angle);
    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    if (rotated != bitmap) bitmap.recycle();
    return rotated;
  }

  private static BitmapFactory.Options getImageDimensions(InputStream inputStream) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds    = true;
    BufferedInputStream fis       = new BufferedInputStream(inputStream);
    BitmapFactory.decodeStream(fis, null, options);
    try {
      fis.close();
    } catch (IOException ioe) {
      Log.w(TAG, "failed to close the InputStream after reading image dimensions");
    }
    return options;
  }

  public static Pair<Integer, Integer> getDimensions(InputStream inputStream) {
    BitmapFactory.Options options = getImageDimensions(inputStream);
    return new Pair<>(options.outWidth, options.outHeight);
  }

  public static InputStream toCompressedJpeg(Bitmap bitmap) {
    ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
    bitmap.compress(CompressFormat.JPEG, 85, thumbnailBytes);
    return new ByteArrayInputStream(thumbnailBytes.toByteArray());
  }

  public static byte[] toByteArray(Bitmap bitmap) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(CompressFormat.PNG, 100, stream);
    return stream.toByteArray();
  }
}
