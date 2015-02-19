package systems.obscure.servertesting.client;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.SigningKey;
import org.abstractj.kalium.keys.VerifyKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import systems.obscure.servertesting.protos.Fetch;
import systems.obscure.servertesting.protos.ProtoMessage;
import systems.obscure.servertesting.protos.Request;
import systems.obscure.servertesting.protos.ServerAnnounce;

/**
 * @author unixninja92
 */
public class Client {
    // messageLifetime is the default amount of time for which we'll keep a
    // message. (Counting from the time that it was received.)
    public final int messageLifetime = 7 * 24;


    // The current protocol version implemented by this code.
    int protoVersion = 1;

    // stateFilename is the filename of the file on disk in which we
    // load/save our state.
    String stateFile;

    // server is the URL of the user's home server.
    String server;

    // identity is a curve25519 private value that's used to authenticate
    // the client to its home server.
    KeyPair identity;

    // groupPriv is the group private key for the user's delivery group.
    byte[] groupPriv;

    // prevGroupPrivs contains previous group private keys that have been
    // revoked. This allows us to process messages that were inflight at
    // the time of the revocation.
    byte[][] preGroupPriv;

    // generation is the generation number of the group private key and is
    // incremented when a member of the group is revoked.
    Integer generation;

    // siging Ed25519 keypair.
    SigningKey signingKey;

    // InboxMessage represents a message in the client's inbox. (Acks also appear
    // as InboxMessages, but their message.Body is empty.)
    public class InboxMessage {
        Long id;
        boolean read = false;
        int receivedTime;
        Long from;


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

        public String getSentTime(){
            if(message == null)
                return "(unknown)";
            else
                return message.time.toString();//TODO format dat time!!!
        }

        public int getEraserTime(){
            return receivedTime + messageLifetime;//TODO format dat time!!!
        }

        public String getBody(){
            if(message == null)
                return "(cannot display message as key exchange is still pending)";
            else if(message.body_encoding == ProtoMessage.Encoding.RAW)
                return  message.body.toString();
            return  "(cannot display message as encoding is not supported)";
        }
    }

    // NewMessage is sent from the network goroutine to the client goroutine and
    // contains messages fetched from the home server. TODO fix comment for java
    public class NewMessage {
        Fetch fetched;
        ServerAnnounce serverAnnounce;
        boolean ack = false;
    }

    public class Contact {
        // id is only locally valid.
        long id;

        // name is the friendly name that the user chose for this contact. It
        // is unique for all contacts.
        String name;

        // isPending is true if we haven't received a key exchange message from
        // this contact.
        boolean isPending = true;

        // kxsBytes is the serialised key exchange message that we generated
        // for this contact. (Only valid if |isPending| is true.)
        byte[] kxsBytes;

        // groupKey is the group member key that we gave to this contact.
        // groupKey TODO bbsig.MemberKey
        // myGroupKey is the one that they gave to us.
        // myGroupKey TODO bbsig.MemberKey

        // generation is the current group generation number that we know for
        // this contact.
        Long generation;

        // theirServer is the URL of the contact's home server.
        String theirServer;

        // theirPub is their Ed25519 public key.
        VerifyKey theirPub;

        // theirIdentityPublic is the public identity that their home server
        // knows them by.
        PublicKey theirIdentityPublic;

        // supportedVersion contains the greatest protocol version number that
        // we have observed from this contact.
        int supportedVersion;

        // revoked is true if this contact has been revoked.
        boolean revoked = false;

        // revokedUs is true if this contact has revoked us.
        boolean revokedUs = false;

        // pandaKeyExchange contains the serialised PANDA state if a key
        // exchange is ongoing.
        byte[] pandaKeyExchange;

        // pandaResult contains an error message in the event that a PANDA key
        // exchange failed.
        String pandaResult;
    }

    // contactList is a sortable slice of Contacts.
    public class ContactList {
        Contact[] contactList;
        public int len() {
            return contactList.length;
        }
        public boolean less(int i, int j) {
            return contactList[i].name.compareTo(contactList[j].name) < 0;
        }
        //TODO sort contacts list
    }

    public class QueuedMessage {
        Request request;
        Long id;
        Long to;
        String server;
        int created;
        int sent;
        int acked;
        boolean revocation;
        ProtoMessage message;

        // sending is true if the transact goroutine is currently sending this
        // message. This is protected by the queueMutex. TODO adjust comment for java
        boolean sending;

        //TODO methods for this
    }

    public void start() {
        boolean newAccount = true;

        if(newAccount) {
            try {
                SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
                MessageDigest digest = MessageDigest.getInstance("SHA256");
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                signingKey = new SigningKey(digest.digest(seed));
                identity = new KeyPair();


            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
