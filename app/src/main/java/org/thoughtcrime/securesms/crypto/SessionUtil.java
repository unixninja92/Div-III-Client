package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;
//import org.whispersystems.textsecure.api.push.TextSecureAddress;

public class SessionUtil {

  public static boolean hasSession(Context context, org.thoughtcrime.securesms.crypto.MasterSecret masterSecret, Recipient recipient) {
    return hasSession(context, masterSecret, recipient.getNumber());
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String number) {
//    SessionStore   sessionStore   = new TextSecureSessionStore(context, masterSecret);
//    AxolotlAddress axolotlAddress = new AxolotlAddress(number, TextSecureAddress.DEFAULT_DEVICE_ID);
//
//    return sessionStore.containsSession(axolotlAddress);
      return false;
  }
}
