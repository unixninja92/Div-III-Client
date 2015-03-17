package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;

//@Module (complete = false, injects = {CleanPreKeysJob.class})
public class AxolotlStorageModule {

  private final Context context;

  public AxolotlStorageModule(Context context) {
    this.context = context;
  }

//  @Provides SignedPreKeyStoreFactory provideSignedPreKeyStoreFactory() {
//    return new SignedPreKeyStoreFactory() {
//      @Override
//      public SignedPreKeyStore create(MasterSecret masterSecret) {
//        return new TextSecureAxolotlStore(context, masterSecret);
//      }
//    };
//  }

  public static interface SignedPreKeyStoreFactory {
    public SignedPreKeyStore create(MasterSecret masterSecret);
  }
}
