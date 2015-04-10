package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.whispersystems.libaxolotl.state.SessionStore;

import systems.obscure.client.client.Contact;
//import org.whispersystems.textsecure.api.push.TextSecureAddress;

public class SessionUtil {

  public static boolean hasSession(Context context, org.thoughtcrime.securesms.crypto.MasterSecret masterSecret, Contact recipient) {
    return hasSession(context, masterSecret, recipient.name);
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String name) {
    SessionStore sessionStore   = new TextSecureSessionStore(context, masterSecret);
//    AxolotlAddress axolotlAddress = new AxolotlAddress(number, TextSecureAddress.DEFAULT_DEVICE_ID);
//
//    return sessionStore.containsSession(axolotlAddress);
      return false;
  }
}
