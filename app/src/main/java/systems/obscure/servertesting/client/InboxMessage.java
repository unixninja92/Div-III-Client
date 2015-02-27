package systems.obscure.servertesting.client;

import com.google.common.primitives.UnsignedLong;

import java.util.HashMap;

import systems.obscure.servertesting.protos.ProtoMessage;

/**
 * @author unixninja92
 */
// InboxMessage represents a message in the client's inbox. (Acks also appear
// as InboxMessages, but their message.Body is empty.)
public class InboxMessage {
    UnsignedLong id;
    boolean read = false;
    int receivedTime;
    UnsignedLong from;


    // sealed contained the encrypted message if the contact who sent this
    // message is still pending.
    byte[] sealed;
    boolean acked = false;

    // message may be nil if the contact who sent this is pending. In this
    // case, sealed with contain the encrypted message.
    ProtoMessage message;

    // retained is true if the user has chosen to retain this message -
    // i.e. to opt it out of the usual, time-based, auto-deletion.
    boolean retained = false;

    HashMap<Long, PendingDecryption> decryptions;

    public String getSentTime(){
        if(message == null)
            return "(unknown)";
        else
            return message.time.toString();//TODO format dat time!!!
    }

    public int getEraserTime(){
        return receivedTime + Constants.MESSAGE_LIFETIME;//TODO format dat time!!!
    }

    public String getBody(){
        if(message == null)
            return "(cannot display message as key exchange is still pending)";
        else if(message.body_encoding == ProtoMessage.Encoding.RAW)
            return  message.body.toString();
        return  "(cannot display message as encoding is not supported)";
    }
}
