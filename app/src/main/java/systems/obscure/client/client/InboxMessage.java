package systems.obscure.client.client;

import java.util.HashMap;

import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// InboxMessage represents a message in the client's inbox. (Acks also appear
// as InboxMessages, but their message.Body is empty.)
public class InboxMessage {
    Long id;
    boolean read = false;
    Long receivedTime;
    Long from;


    // sealed contained the encrypted message if the contact who sent this
    // message is still pending.
    byte[] sealed;
    boolean acked = false;

    // message may be nil if the contact who sent this is pending. In this
    // case, sealed with contain the encrypted message.
    Pond.Message message;

    // retained is true if the user has chosen to retain this message -
    // i.e. to opt it out of the usual, time-based, auto-deletion.
    boolean retained = false;

    // exposureTime contains the time when the message was last "exposed".
    // This is used to allow a small period of time for the user to mark a
    // message as retained (messageGraceTime). For example, if a message is
    // loaded at startup and has expired then it's a candidate for
    // deletion, but the exposureTime will be the startup time, which
    // ensures that we leave it a few minutes before deletion. Setting
    // retained to false also resets the exposureTime.
    long exposureTime;

    HashMap<Long, PendingDecryption> decryptions;

    public String getSentTime(){
        if(message == null)
            return "(unknown)";
        else
            return ""+message.getTime();//TODO format dat time!!!
    }

    public Long getEraserTime(){
        return receivedTime + Globals.MESSAGE_LIFETIME;//TODO format dat time!!!
    }

    public String getBody(){
        if(message == null)
            return "(cannot display message as key exchange is still pending)";
        else if(message.getBodyEncoding() == Pond.Message.Encoding.RAW)
            return  message.getBody().toString();
        return  "(cannot display message as encoding is not supported)";
    }
}
