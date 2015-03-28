package systems.obscure.client.client;

import android.content.Context;
import android.content.Intent;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.SigningKey;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.Channel;
import org.jcsp.lang.One2AnyChannel;
import org.jcsp.lang.One2OneChannel;
import org.jcsp.lang.SharedChannelInput;
import org.jcsp.lang.SharedChannelOutput;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import systems.obscure.client.Globals;
import systems.obscure.client.disk.NewState;
import systems.obscure.client.disk.StateFile;
import systems.obscure.client.service.TransactService;

/**
 * @author unixninja92
 */
public class Client {
    // autoFetch controls whether the network goroutine performs periodic
    // transactions or waits for outside prompting.
    public boolean autoFetch = true;

    // newMeetingPlace is a function that returns a PANDA MeetingPlace. In
    // tests this can be overridden to return a testing meeting place.
//    newMeetingPlace func() panda.MeetingPlace TODO fix this

    // stateFilename is the filename of the file on disk in which we
    // load/save our state.
    public String stateFilename;
    // stateLock protects the state against concurrent access by another
    // program.
    public ReentrantReadWriteLock stateLock;

    // writerChan is a channel that the disk goroutine reads from to
    // receive updated, serialised states.
    public SharedChannelOutput<NewState> writerChan;

    // writerDone is a channel that is closed by the disk goroutine when it
    // has finished all pending updates.
    public SharedChannelInput writerDone;

    // fetchNowChan is the channel that the network goroutine reads from
    // that triggers an immediate network transaction. Mostly intended for
    // testing.
    public Any2OneChannel<Boolean> fetchNowChan;

    // lastErasureStorageTime is the time at which we last rotated the
    // erasure storage value.
    public long lastErasureStorageTime;

    // torAddress contains a string like "127.0.0.1:9050", which specifies
    // the address of the local Tor SOCKS proxy.
    public String torAddress;

    // server is the URL of the user's home server.
    public String server;

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
    public Integer generation;

    // siging Ed25519 keypair.
    public SigningKey signingKey;

    public SecureRandom rand;

    // outbox contains all outgoing messages.
    public QueuedMessage[] outbox;
    public HashMap<Long, Draft> drafts;
    public HashMap<Long, Contact> contacts;
    public InboxMessage[] inbox;

    // queue is a queue of messages for transmission that's shared with the
    // network goroutine and protected by queueMutex.
    public LinkedBlockingQueue<QueuedMessage> queue; //synchronized
    public ReentrantReadWriteLock queueLock;

    // newMessageChan receives messages that have been read from the home
    // server by the network goroutine.
    public One2OneChannel<NewMessage> newMessageChan;
    // messageSentChan receives the ids of messages that have been sent by
    // the network goroutine.
    public Any2OneChannel<MessageSendResult> messageSentChan;
    // backgroundChan is used for signals from background processes - e.g.
    // detachment uploads.
    public One2OneChannel backgroundChan;
    // signingRequestChan receives requests to sign messages for delivery,
    // just before they are sent to the destination server.
    public One2OneChannel<SigningRequest> signingRequestChan;

    public HashMap<Long, Boolean> usedIds;


    private static Client ourInstance;// = new Client();

    public static Client getInstance() {
        if(ourInstance == null) {
            ourInstance = new Client(Globals.applicaiontContext);
            ourInstance.start(Globals.applicaiontContext);
        }
        return ourInstance;
    }

    private Client(Context context) {
        torAddress = "127.0.0.1:9050";
        server = "RX4SBLINCG6TUCR7FJYMNNSA33QAPVJAEYA5ROT6QG4IPX7FXE7Q";
        stateFilename = context.getFilesDir().getPath()+"/statefile";
        queue = new LinkedBlockingQueue<>();
        queueLock = new ReentrantReadWriteLock();
        newMessageChan = Channel.one2one();
        messageSentChan = Channel.any2one();
        backgroundChan = Channel.one2one();
        signingRequestChan = Channel.one2one();
        usedIds = new HashMap<>();

        outbox = new QueuedMessage[10];
        drafts = new HashMap<>();
        contacts = new HashMap<>();
        inbox = new InboxMessage[10];

        fetchNowChan = Channel.any2one();

    }

    public void start(Context context) {
        try {
            rand = SecureRandom.getInstance("SHA1PRNG");

            StateFile stateFile = new StateFile(rand, stateFilename);
            stateLock = stateFile.getLock();

            boolean newAccount = true;//TextSecurePreferences.isRegisteredOnServer(context);

            if(newAccount) {
                MessageDigest digest = MessageDigest.getInstance("SHA256");
                byte[] seed = new byte[32];
                rand.nextBytes(seed);
                signingKey = new SigningKey(digest.digest(seed));
                identity = new KeyPair();
                rand.nextBytes(hmacKey);

                Network.doCreateAccount();//TODO make async task
            }

            Any2OneChannel<NewState> stateChan = Channel.any2one(5);
            writerChan = stateChan.out();

            One2AnyChannel doneChan = Channel.one2any(5);
            writerDone = doneChan.in();

            //            stateFile.StartWrtie(stateChan.in(), doneChan.out());//TODO put on seperate thread

            Intent intent = new Intent(context, TransactService.class);
            context.startService(intent);

            if(newAccount){
                //TODO save
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
//        stateFilename = filename;
////        context = con;
//
//        try {
//            rand = SecureRandom.getInstance("SHA1PRNG");
//
//            StateFile stateFile = new StateFile(rand, stateFilename);
//            stateLock = stateFile.getLock();
////            stateLock.
//
//
//            boolean newAccount = TextSecurePreferences.isRegisteredOnServer(context);
//
//            if(newAccount) {
////                try {
//                    MessageDigest digest = MessageDigest.getInstance("SHA256");
//                    byte[] seed = new byte[32];
//                    rand.nextBytes(seed);
//                    signingKey = new SigningKey(digest.digest(seed));
//                    identity = new KeyPair();
//                    rand.nextBytes(hmacKey);
////
////                    byte[] pub = BaseEncoding.base32().decode(server);
////                    PublicKey serverKey = new PublicKey(pub);
////                    transport = new Transport(identity, serverKey);
////                    transport.handshake();
////                    doCreateAccount();
//
////                } catch (IOException e) {
////                    e.printStackTrace();
////                }
//            }
//
//            Any2OneChannel<NewState> stateChan = Channel.any2one(5);
//            writerChan = stateChan.out();
//
//            One2AnyChannel doneChan = Channel.one2any(5);
//            writerDone = doneChan.in();
//
////            stateFile.StartWrtie(stateChan.in(), doneChan.out());//TODO put on seperate thread
//
//            TransactService ts = new TransactService();
//            ts.registerActivityStarted(context);
//            ts.onCreate();
////            ApplicationContext.getInstance(context);
//
//            if(newAccount){
//                //TODO save
//            }
//
//
//        } catch (NoSuchAlgorithmException e) {
//                e.printStackTrace();
//        }
//    }

    public Draft outboxToDraft(QueuedMessage msg) {
        Draft draft = new Draft();
        draft.id = msg.id;
        draft.created = msg.created;
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

    public void enqueue(QueuedMessage m) {
        queueLock.writeLock().lock();
        queue.add(m);
        queueLock.writeLock().unlock();
    }

    // logEvent records an exceptional event relating to the given contact.
    public void logEvent(Contact contact, String msg) {
        Long time = System.nanoTime();
        Event e = new Event(time, msg);
        contact.events.add(e);
        //LOGME
    }

    public Long randId() {
        byte[] idBytes = new byte[8];
        while(true) {
            rand.nextBytes(idBytes);
            Long n = ByteBuffer.wrap(idBytes).getLong();
            if(n == 0)
                continue;
            if(usedIds.containsKey(n))
                continue;
            usedIds.put(n, true);
            return n;
        }
    }

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
            if(c.name.equals(name))
                return c;
        }
        return  null;
    }

    public void deleteInboxMsg(Long id) {
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

    public void deleteOutboxMsg(Long id) {
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

//    public int indexOfQueuedMessage(QueuedMessage msg) {
//        // c.queueMutex must be held before calling this function.
//        for(int i = 0; i < queue.length; i++) {
//            queue.
//            if(queue[i] == msg)
//                return i;
//        }
//        return -1;
//    }

    public void removeQueuedMessage(QueuedMessage msg) {
        // c.queueMutex must be held before calling this function.
//        queueLock.writeLock().lock();
        if(queueLock.writeLock().isHeldByCurrentThread())
            queue.remove(msg);
//        queueLock.writeLock().unlock();
    }

    // If sending a message fails for any reason then we want to move the
// message to the end of the queue so that we never clog the queue with
// an unsendable message. However, we also don't want to reorder messages
// so all messages to the same contact are moved to the end of the queue.
    public void moveContactsMessagesToEndOfQueue(Long id) {
        // c.queueMutex must be held before calling this function.

        if(queue.size() < 2)
            // There are no other orders for queues of length zero or one.
            return;

        LinkedBlockingQueue<QueuedMessage> newQueue = new LinkedBlockingQueue<>();
        ArrayList<QueuedMessage> movedMessages = new ArrayList<>();

        for(QueuedMessage m: queue){
            if(m.to == id)
                movedMessages.add(m);
            else
                newQueue.add(m);
        }
        newQueue.addAll(movedMessages);
        queue = newQueue;

    }

    public void deleteContact(Contact contact){
        InboxMessage[] newInbox = new InboxMessage[inbox.length];
        int pos = 0;
        for(int i = 0; i < inbox.length; i++){
            InboxMessage inboxMsg = inbox[i];
            if(inboxMsg.from == contact.id)
                continue;
            newInbox[pos++] = inboxMsg;
        }
        inbox = newInbox;
        //TODO update UI

        for(Draft d: drafts.values()) {
            if(d.to == contact.id)
                d.to = 0L;
        }

        queueLock.readLock().lock();
        LinkedBlockingQueue<QueuedMessage> newQueue = new LinkedBlockingQueue<>();
        for(QueuedMessage msg: queue) {
            if(msg.to == contact.id && !msg.revocation)
                continue;
            newQueue.add(msg);
        }
        queueLock.readLock().unlock();
        queueLock.writeLock().lock();
        queue = newQueue;
        queueLock.writeLock().unlock();

        QueuedMessage[] newOutbox = new QueuedMessage[outbox.length];
        pos = 0;
        for(int i = 0; i < outbox.length; i++){
            QueuedMessage outboxMsg = outbox[i];
            if(outboxMsg.id == contact.id)
                continue;
            newOutbox[pos++] = outboxMsg;
        }
        outbox = newOutbox;

        //TODO revocationMessage := c.revoke(contact)
        //c.ui.addRevocationMessageUI(revocationMessage)

        contacts.remove(contact.id);
    }


}
