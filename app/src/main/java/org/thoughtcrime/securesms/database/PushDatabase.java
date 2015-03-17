package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

//import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

public class PushDatabase extends Database {

  private static final String TAG = PushDatabase.class.getSimpleName();

  private static final String TABLE_NAME   = "push";
  public  static final String ID           = "_id";
  public  static final String TYPE         = "type";
  public  static final String SOURCE       = "source";
  public  static final String DEVICE_ID    = "device_id";
  public  static final String BODY         = "body";
  public  static final String TIMESTAMP    = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      TYPE + " INTEGER, " + SOURCE + " TEXT, " + DEVICE_ID + " INTEGER, " + BODY + " TEXT, " + TIMESTAMP + " INTEGER);";

  public PushDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

//  public long insert(TextSecureEnvelope envelope) {
//    ContentValues values = new ContentValues();
//    values.put(TYPE, envelope.getType());
//    values.put(SOURCE, envelope.getSource());
//    values.put(DEVICE_ID, envelope.getSourceDevice());
//    values.put(BODY, Base64.encodeBytes(envelope.getMessage()));
//    values.put(TIMESTAMP, envelope.getTimestamp());
//
//    return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
//  }
//
//  public TextSecureEnvelope get(long id) throws NoSuchMessageException {
//    Cursor cursor = null;
//
//    try {
//      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, ID_WHERE,
//                                                          new String[] {String.valueOf(id)},
//                                                          null, null, null);
//
//      if (cursor != null && cursor.moveToNext()) {
//        return new TextSecureEnvelope(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
//                                      cursor.getString(cursor.getColumnIndexOrThrow(SOURCE)),
//                                      cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID)),
//                                      "",
//                                      cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)),
//                                      Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(BODY))));
//      }
//    } catch (IOException e) {
//      Log.w(TAG, e);
//      throw new NoSuchMessageException(e);
//    } finally {
//      if (cursor != null)
//        cursor.close();
//    }
//
//    throw new NoSuchMessageException("Not found");
//  }

  public Cursor getPending() {
    return databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
  }

  public void delete(long id) {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID_WHERE, new String[] {id+""});
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static class Reader {
    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

//    public TextSecureEnvelope getNext() {
//      try {
//        if (cursor == null || !cursor.moveToNext())
//          return null;
//
//        int          type         = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
//        String       source       = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE));
//        int          deviceId     = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
//        byte[]       body         = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(BODY)));
//        long         timestamp    = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
//
//        return new TextSecureEnvelope(type, source, deviceId, "", timestamp, body);
//      } catch (IOException e) {
//        throw new AssertionError(e);
//      }
//    }

    public void close() {
      this.cursor.close();
    }
  }
}
