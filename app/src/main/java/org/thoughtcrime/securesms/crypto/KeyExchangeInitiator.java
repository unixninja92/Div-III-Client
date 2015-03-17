/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.crypto;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Dialogs;

import systems.obscure.client.R;

//import org.thoughtcrime.securesms.sms.MessageSender;
//import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
//import org.whispersystems.textsecure.api.push.TextSecureAddress;

public class KeyExchangeInitiator {

  public static void initiate(final Context context, final org.thoughtcrime.securesms.crypto.MasterSecret masterSecret, final Recipient recipient, boolean promptOnExisting) {
    if (promptOnExisting && hasInitiatedSession(context, masterSecret, recipient)) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      dialog.setTitle(R.string.KeyExchangeInitiator_initiate_despite_existing_request_question);
      dialog.setMessage(R.string.KeyExchangeInitiator_youve_already_sent_a_session_initiation_request_to_this_recipient_are_you_sure);
      dialog.setIcon(Dialogs.resolveIcon(context, R.attr.dialog_alert_icon));
      dialog.setCancelable(true);
      dialog.setPositiveButton(R.string.KeyExchangeInitiator_send, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          initiateKeyExchange(context, masterSecret, recipient);
        }
      });
      dialog.setNegativeButton(android.R.string.cancel, null);
      dialog.show();
    } else {
      initiateKeyExchange(context, masterSecret, recipient);
    }
  }

  private static void initiateKeyExchange(Context context, org.thoughtcrime.securesms.crypto.MasterSecret masterSecret, Recipient recipient) {
//    SessionStore      sessionStore      = new TextSecureSessionStore(context, masterSecret);
//    PreKeyStore       preKeyStore       = new TextSecurePreKeyStore(context, masterSecret);
//    SignedPreKeyStore signedPreKeyStore = new TextSecurePreKeyStore(context, masterSecret);
//    IdentityKeyStore  identityKeyStore  = new TextSecureIdentityKeyStore(context, masterSecret);
//
////    SessionBuilder    sessionBuilder    = new SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore,
////                                                             identityKeyStore, new AxolotlAddress(recipient.getNumber(),
////                                                                                                  TextSecureAddress.DEFAULT_DEVICE_ID));
//
//    KeyExchangeMessage         keyExchangeMessage = sessionBuilder.process();
//    String                     serializedMessage  = Base64.encodeBytesWithoutPadding(keyExchangeMessage.serialize());
//    OutgoingKeyExchangeMessage textMessage        = new OutgoingKeyExchangeMessage(recipient, serializedMessage);
//
//    MessageSender.send(context, masterSecret, textMessage, -1, false);
  }

  private static boolean hasInitiatedSession(Context context, MasterSecret masterSecret,
                                             Recipient recipient)
  {
//    SessionStore  sessionStore  = new TextSecureSessionStore(context, masterSecret);
//    SessionRecord sessionRecord = sessionStore.loadSession(new AxolotlAddress(recipient.getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID));
//
//    return sessionRecord.getSessionState().hasPendingKeyExchange();
      return false;
  }
}
