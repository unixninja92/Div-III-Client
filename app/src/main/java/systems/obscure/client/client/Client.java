package systems.obscure.client.client;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.SigningKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import systems.obscure.client.protos.Pond;

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
    public KeyPair identity;

    // groupPriv is the group private key for the user's delivery group.
    byte[] groupPriv;

    // prevGroupPrivs contains previous group private keys that have been
    // revoked. This allows us to process messages that were inflight at
    // the time of the revocation.
    byte[][] preGroupPriv;

    byte[] hmacKey = new byte[32];

    // generation is the generation number of the group private key and is
    // incremented when a member of the group is revoked.
    Integer generation;

    // siging Ed25519 keypair.
    SigningKey signingKey;

    SecureRandom rand;

    // outbox contains all outgoing messages.
    QueuedMessage[] outbox;
    HashMap<Long, Draft> drafts;
    HashMap<Long, Contact> contacts;
    InboxMessage[] inbox;

    // queue is a queue of messages for transmission that's shared with the
    // network goroutine and protected by queueMutex.
     QueuedMessage[] queue; //synchronized

    HashMap<Long, Boolean> usedIds;

    Transport transport;


    public void start() {
        boolean newAccount = true;

        if(newAccount) {
            try {
                rand = SecureRandom.getInstance("SHA1PRNG");
                MessageDigest digest = MessageDigest.getInstance("SHA256");
                byte[] seed = new byte[32];
                rand.nextBytes(seed);
                signingKey = new SigningKey(digest.digest(seed));
                identity = new KeyPair();
                rand.nextBytes(hmacKey);

                byte[] pub = BaseEncoding.base32().decode("RX4SBLINCG6TUCR7FJYMNNSA33QAPVJAEYA5ROT6QG4IPX7FXE7Q");
//                byte[] pub = BaseEncoding.base32().decode("25WHHEVD3565FGIOXJZWV7LGQFR4BTO3HF3FWHEW7PCYPFMFPVOQ");
                PublicKey serverKey = new PublicKey(pub);
                transport = new Transport(identity, serverKey);
                transport.handshake();
                doCreateAccount();

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Draft outboxToDraft(QueuedMessage msg) {
        Draft draft = new Draft();
        draft.id = msg.id;
        //TODO draft.created = msg.created;
        draft.to = msg.to;
        draft.body = msg.message.getBody().toString();
        draft.attachments = msg.message.getFilesList();
        draft.detachments = msg.message.getDetachedFilesList();

        long irt = msg.message.getInReplyTo();
        if(irt != 0){
            // The inReplyTo value of a draft references *our* id for the
            // inbox message. But the InReplyTo field of a pond.Message
            // references's the contact's id for the message. So we need to
            // enumerate the messages in the inbox from that contact and
            // find the one with the matching id.
            for(InboxMessage inboxMsg: inbox) {
                if(inboxMsg.from.equals(msg.to) && inboxMsg.message != null && inboxMsg.message.getId() == irt){
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

    public void deleteInboxMsg(UnsignedLong id) {
        InboxMessage[] newInbox = new InboxMessage[inbox.length];
        int pos = 0;
        for(int i = 0; i < inbox.length; i++){
            InboxMessage inboxMsg = inbox[i];
            if(inboxMsg.id.equals(id))
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
            if(inboxMsg.from.equals(contact.id) && inboxMsg.sealed.length > 0 ||
                    inboxMsg.message != null && inboxMsg.message.getBody().size() == 0)
                continue;
            newInbox[pos++] = inboxMsg;
        }
        inbox = newInbox;
    }

    public void deleteOutboxMsg(UnsignedLong id) {
        QueuedMessage[] newOutbox = new QueuedMessage[outbox.length];
        int pos = 0;
        for(int i = 0; i < outbox.length; i++){
            QueuedMessage outboxMsg = outbox[i];
            if(outboxMsg.id.equals(id))
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

    public void removeQueuedMessage(int index) {
        // c.queueMutex must be held before calling this function.

        QueuedMessage[] newQueue = new QueuedMessage[queue.length];
        int pos = 0;
        for(int i = 0; i < queue.length; i++){
            if(i != index)
                newQueue[pos++] = queue[i];
        }
        queue = newQueue;
    }

    public void doCreateAccount() {
        byte[] gen = new byte[4];
        rand.nextBytes(gen);
        generation = ByteBuffer.wrap(gen).getInt();

        Pond.NewAccount.Builder newAccount = Pond.NewAccount.newBuilder();
        newAccount.setGeneration(generation);
        newAccount.setGroup(ByteString.copyFrom(hmacKey));
        newAccount.setHmacKey(ByteString.copyFrom(hmacKey));
//        NewAccount.Builder newAccount = new NewAccount.Builder();
//        newAccount.generation(generation);
//        newAccount.group(ByteString.of(hmacKey));
//        newAccount.hmac_key(ByteString.of(hmacKey));

        Pond.Request.Builder request = Pond.Request.newBuilder();
        request.setNewAccount(newAccount);
//        request.new_account(newAccount.build());
//        NewAccount newAccount = new NewAccount(generation, ByteString.of(hmacKey), null);
//        Request request = new Request(newAccount, null, null, null, null, null, null, null);

        try {
            transport.writeProto(request.build());
            Pond.Reply reply = transport.readProto();
            replyToError(reply);
//            System.out.println(reply.getAccountCreated().getDetails().toString());
//            System.out.println(reply.account_created.details.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void replyToError(Pond.Reply reply) throws IOException {
        if(reply.hasStatus() || reply.getStatus() == Pond.Reply.Status.OK)
            return;
        if(reply.getStatus().getNumber()<29 && reply.getStatus().getNumber()>=0)
            throw new IOException("error from server: "+reply.getStatus());
        else
            throw new IOException("unknown error from server: "+reply.getStatus());
    }
}
