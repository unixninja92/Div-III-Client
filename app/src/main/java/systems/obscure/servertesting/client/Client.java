package systems.obscure.servertesting.client;

import org.abstractj.kalium.crypto.Random;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.SigningKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * @author unixninja92
 */
public class Client {

    // autoFetch controls whether the network goroutine performs periodic
    // transactions or waits for outside prompting.
    boolean autoFetch;

    // newMeetingPlace is a function that returns a PANDA MeetingPlace. In
    // tests this can be overridden to return a testing meeting place.
//    newMeetingPlace func() panda.MeetingPlace TODO fix this

    // stateFilename is the filename of the file on disk in which we
    // load/save our state.
    String stateFile;

    // torAddress contains a string like "127.0.0.1:9050", which specifies
    // the address of the local Tor SOCKS proxy.
    String torAddress;

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

    Random rand;

    // outbox contains all outgoing messages.
    QueuedMessage[] outbox;
    HashMap<Long, Draft> drafts;
    HashMap<Long, Contact> contacts;
    InboxMessage[] inbox;

    // queue is a queue of messages for transmission that's shared with the
    // network goroutine and protected by queueMutex.
     QueuedMessage[] queue; //synchronized

    HashMap<Long, Boolean> usedIds;


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

    public Draft outboxToDraft(QueuedMessage msg) {
        Draft draft = new Draft();
        draft.id = msg.id;
        //TODO draft.created = msg.created;
        draft.to = msg.to;
        draft.body = msg.message.body.toString();
        draft.attachments = msg.message.files;
        draft.detachments = msg.message.detached_files;

        long irt = msg.message.in_reply_to;
        if(irt != 0){
            // The inReplyTo value of a draft references *our* id for the
            // inbox message. But the InReplyTo field of a pond.Message
            // references's the contact's id for the message. So we need to
            // enumerate the messages in the inbox from that contact and
            // find the one with the matching id.
            for(InboxMessage inboxMsg: inbox) {
                if(inboxMsg.from == msg.to && inboxMsg.message != null && inboxMsg.message.id == irt){
                    draft.inReplyTo = inboxMsg.id;
                    break;
                }
            }
        }
        return draft;
    }

    public String contactName(long id) {
        if(id == 0)
            return "Home Server";
        return contacts.get(id).name;
    }

    //TODO randBytes func

    //TODO randId func

    //TODO now func

    // registerId records that an ID number has been used, typically because we are
    // loading a state file.
    public void registerId(long id) throws Exception {//TODO choose better execption to throw
        if(usedIds.get(id))
            throw new Exception("duplicate ID registered");
        usedIds.put(id, true);
    }

    //TODO newRatchet func

    //TODO newKeyExchange func

    public Contact contactByName(String name) {
        for(Map.Entry entry: contacts.entrySet()) {
            Contact c = (Contact)entry.getValue();
            if(c.name == name)
                return c;
        }
        return  null;
    }

    public void deleteInboxMsg(long id) {
        InboxMessage[] newInbox = new InboxMessage[inbox.length];
        int pos = 0;
        for(int i = 0; i < inbox.length; i++){
            InboxMessage inboxMsg = inbox[i];
            if(inboxMsg.id == id)
                continue;
            newInbox[pos++] = inboxMsg;
        }
        inbox = newInbox;
    }

    // dropSealedAndAckMessagesFrom removes all sealed or pure-ack messages from
    // the given contact, from the inbox.
    public void dropSealedAndAckMessagesFrom(Contact contact) {
        InboxMessage[] newInbox = new InboxMessage[inbox.length];
        int pos = 0;
        for(int i = 0; i < inbox.length; i++){
            InboxMessage inboxMsg = inbox[i];
            if(inboxMsg.from == contact.id && inboxMsg.sealed.length > 0 ||
                    inboxMsg.message != null && inboxMsg.message.body.size() == 0)
                continue;
            newInbox[pos++] = inboxMsg;
        }
        inbox = newInbox;
    }

    public void deleteOutboxMsg(long id) {
        QueuedMessage[] newOutbox = new QueuedMessage[outbox.length];
        int pos = 0;
        for(int i = 0; i < outbox.length; i++){
            QueuedMessage outboxMsg = outbox[i];
            if(outboxMsg.id == id)
                continue;
            newOutbox[pos++] = outboxMsg;
        }
        outbox = newOutbox;
    }

    public int indexOfQueuedMessage(QueuedMessage msg) {
        // c.queueMutex must be held before calling this function.
        for(int i = 0; i < queue.length; i++) {
            if(queue[i] == msg)
                return i;
        }
        return -1;
    }
}
