package systems.obscure.client.client;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.VerifyKey;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.Channel;
import org.jcsp.lang.One2AnyChannel;
import org.jcsp.lang.One2OneChannel;
import org.jcsp.lang.SharedChannelInput;
import org.jcsp.lang.SharedChannelOutput;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;
import java.io.IOException;
import java.security.KeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import systems.obscure.client.Globals;
import systems.obscure.client.crypto.SigningKey;
import systems.obscure.client.disk.NewState;
import systems.obscure.client.disk.StateFile;
import systems.obscure.client.protos.LocalStorage;
import systems.obscure.client.protos.Pond;
import systems.obscure.client.ratchet.Ratchet;
import systems.obscure.client.service.StateService;
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

    // hmacKey is shared with clients home server so that the home server knows
    // what messages to send to the client
    SecretKey hmacKey;

    // generation is the generation number of the group private key and is
    // incremented when a member of the group is revoked.
//    public Integer generation;

    // siging Ed25519 keypair.
    public SigningKey signingKey;

    public SecureRandom rand;

    // outbox contains all outgoing messages.
    public HashMap<Long, QueuedMessage> outbox;
    public HashMap<Long, Draft> drafts;
    public HashMap<Long, Contact> contacts;
    public HashMap<Long, InboxMessage> inbox;

    // <hmac, id> hmac mapped to contact id
    public HashMap<Long, Long> hmacIndex;


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

    public StateFile stateFile;

    static boolean stateReady = false;


    private static Client ourInstance;// = new Client();

    public static Client getInstance() {
            if (ourInstance == null ) {
                ourInstance = new Client(Globals.applicaiontContext);
                ourInstance.start(Globals.applicaiontContext);
            }
        return ourInstance;
    }

    public static boolean isNull() {
//        return ourInstance == null;
        return ourInstance == null || !stateReady;
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

        outbox = new HashMap<>();
        drafts = new HashMap<>();
        contacts = new HashMap<>();
        inbox = new HashMap<>();

        hmacIndex = new HashMap<>();

//        contactList = new ArrayList<>();

        fetchNowChan = Channel.any2one();
//        Pond.KeyExchange.

//        start(context);
    }

    public synchronized void start(Context context) {
        try {
            rand = SecureRandom.getInstance("SHA1PRNG");

            stateFile = new StateFile(rand, stateFilename);
            stateLock = stateFile.getLock();

            boolean newAccount;
//            boolean test = true;
            boolean test = false;
            if(!test) {
                stateLock.readLock().lock();
                newAccount = !(new File(stateFilename).isFile());
                stateLock.readLock().unlock();
            }
            else
                newAccount = true;

            if(newAccount) {
                MessageDigest digest = MessageDigest.getInstance("SHA256");
                byte[] seed = new byte[32];
                rand.nextBytes(seed);
                signingKey = new SigningKey(digest.digest(seed));
                identity = new KeyPair();
                byte[] hmac = new byte[32];
                rand.nextBytes(hmac);
                hmacKey = new SecretKeySpec(hmac, "HmacSHA256");

                System.out.println("Create StateFile instance");
                stateFile.Create(KeyCachingService
                        .getMasterSecret(context).getEncryptionKey().getEncoded());

                System.out.println("Do the network thing :)");
//                new AsyncTask<Void, Void, Void>() {
//                    @Override
//                    protected Void doInBackground(Void... params) {
                        Network.doCreateAccount(hmacKey.getEncoded(), server);
                        TextSecurePreferences.setRegisteredOnServer(context, true);
//                        return null;
//                    }
//                }.execute();
            }
            else {
                System.out.println("load dat State file");
                stateLock.readLock().lock();
                loadState();
                stateLock.readLock().unlock();
            }

            Any2OneChannel<NewState> stateChan = Channel.any2one(5);
            writerChan = stateChan.out();

            One2AnyChannel doneChan = Channel.one2any(5);
            writerDone = doneChan.in();

            System.out.println("start state service");
            Intent stateIntent = new Intent(context, StateService.class);
            Globals.stateIn = stateChan.in();
            Globals.stateDone = doneChan.out();
            context.startService(stateIntent);

            System.out.println("start transact service");
            Intent transactIntent = new Intent(context, TransactService.class);
            context.startService(transactIntent);



            if(newAccount){
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        save();
                        return null;
                    }
                }.execute();
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        stateReady = true;
    }

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
            for(InboxMessage inboxMsg: inbox.values()) {
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
        while(true) {
            Long n = rand.nextLong();
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
    public void registerId(long id){
//        if(usedIds.containsKey(id))
//            throw new RuntimeException("duplicate ID registered");
        usedIds.put(id, true);
    }

    //TODO newRatchet func

    public Pond.SignedKeyExchange newKeyExchange(Contact contact) {
        contact.ratchet = new Ratchet(contact, rand);
        Pond.KeyExchange.Builder kx = Pond.KeyExchange.newBuilder();
        kx.setPublicKey(ByteString.copyFrom(signingKey.getVerifyKey().toBytes()));
        kx.setIdentityPublic(ByteString.copyFrom(identity.getPublicKey().toBytes()));
        kx.setServer(server);

        contact.ratchet.fillKeyExchagne(kx);

        kx.addAllHmacPairs(contact.generateHMACPairs(4));


        byte[] sig = signingKey.sign(kx.build().toByteArray());

        Pond.SignedKeyExchange.Builder kxs = Pond.SignedKeyExchange.newBuilder();
        kxs.setSigned(ByteString.copyFrom(kx.build().toByteArray()));
        kxs.setSignature(ByteString.copyFrom(sig));

        contact.kxsBytes = kxs.build().toByteArray();

        return kxs.build();
    }

    public Contact contactByName(String name) {
        for(Map.Entry entry: contacts.entrySet()) {
            Contact c = (Contact)entry.getValue();
            if(c.name.equals(name))
                return c;
        }
        return  null;
    }

    public void deleteInboxMsg(Long id) {
        inbox.remove(id);
    }

    // dropSealedAndAckMessagesFrom removes all sealed or pure-ack messages from
    // the given contact, from the inbox.
    public void dropSealedAndAckMessagesFrom(Contact contact) {
        for(InboxMessage inboxMsg: inbox.values()){
            if(inboxMsg.from.equals(contact.id) && inboxMsg.sealed.length > 0 ||
                    inboxMsg.message != null && inboxMsg.message.getBody().size() == 0)
                inbox.remove(inboxMsg.id);
        }
    }

    public void deleteOutboxMsg(Long id) {
        outbox.remove(id);
    }


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
        for(InboxMessage inboxMsg: inbox.values()){
            if(inboxMsg.from == contact.id)
                inbox.remove(inboxMsg.id);
        }
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

        for(QueuedMessage outboxMsg: outbox.values()){
            if(outboxMsg.id == contact.id)
                outbox.remove(outboxMsg.id);
        }

        //TODO revocationMessage := c.revoke(contact)
        //c.ui.addRevocationMessageUI(revocationMessage)

        contacts.remove(contact.id);
    }

    public void loadState() throws IOException {
        LocalStorage.State state = stateFile.Read(KeyCachingService
                .getMasterSecret(Globals.applicaiontContext).getEncryptionKey().getEncoded());

        server = state.getServer();

        System.out.println(state.getIdentity().size());
        if(state.getIdentity().size() != 32)
            throw new IOException("client: identity is wrong length in State");

        identity = new KeyPair(state.getIdentity().toByteArray());

        signingKey = new SigningKey(state.getSeed().toByteArray());

        hmacKey = new SecretKeySpec(state.getHmacKey().toByteArray(), "HmacSHA256");


        for (LocalStorage.Contact cont: state.getContactsList()){
            Contact contact = new Contact(cont.getId(), cont.getName());
            contact.kxsBytes = cont.getKeyExchangeBytes().toByteArray();
            contact.revokedUs = cont.getRevokedUs();
            registerId(contact.id);
            contacts.put(contact.id, contact);

            if(cont.hasIsPending() && cont.getIsPending()) {
                contact.isPending = true;
                continue;
            }

            contact.theirServer = cont.getTheirServer();

            contact.theirPub = new VerifyKey(cont.getTheirPub().toByteArray());

            contact.theirIdentityPublic = new PublicKey(cont.getTheirIdentityPublic().toByteArray());

            if(cont.hasSupportedVersion())
                contact.supportedVersion = cont.getSupportedVersion();

            contact.theirHMACPairs = new ArrayList<>(cont.getTheirPairsList());

            for(LocalStorage.Contact.MyHMACPair pair: cont.getMyPairsList()){
                contact.myHMACs.add(new HMACPair(pair.getPublicKey().toByteArray(),
                        pair.getHmac().toByteArray()));
            }

            contact.events = new ArrayList<>();
            for(LocalStorage.Contact.Event evt: cont.getEventsList()){
                contact.events.add(new Event(evt.getTime(), evt.getMessage()));
            }
        }


        for(LocalStorage.Inbox in: state.getInboxList()){
            InboxMessage mesg = new InboxMessage();
            mesg.id = in.getId();
            mesg.from = in.getFrom();
            mesg.receivedTime = in.getReceivedTime();
            mesg.acked = in.getAcked();
            mesg.read = in.getRead();
            mesg.sealed = in.getSealed().toByteArray();
            mesg.retained = in.getRetained();

            registerId(mesg.id);

            if(in.getMessage().size() > 0){
                mesg.message = Pond.Message.parseFrom(in.getMessage());
            }

            inbox.put(mesg.id, mesg);
        }

        for(LocalStorage.Outbox out: state.getOutboxList()){
            QueuedMessage msg = new QueuedMessage();
            msg.id = out.getId();
            msg.to = out.getTo();
            msg.server = out.getServer();
            msg.created = out.getCreated();

            registerId(msg.id);

            if(out.getMessage().size() > 0){
                msg.message = Pond.Message.parseFrom(out.getMessage()).toBuilder();
            }

            if(out.hasSent())
                msg.sent = out.getSent();
            if(out.hasAcked())
                msg.acked = out.getAcked();

            if(out.getRequest().size() > 0){
                msg.request = Pond.Request.parseFrom(out.getRequest()).toBuilder();
            }
            msg.revocation = out.getRevocation();
            msg.server = out.getServer();

            outbox.put(msg.id, msg);

            if(msg.sent == 0L && (msg.to == 0L || !contacts.get(msg.to).revokedUs))
                enqueue(msg);
        }

        for(LocalStorage.Draft d: state.getDraftsList()){
            Draft draft = new Draft();
            draft.id = d.getId();
            draft.body = d.getBody();
            draft.attachments = d.getAttachmentsList();
            draft.detachments = d.getDetachmentsList();
            draft.created = d.getCreated();

            registerId(draft.id);
            if(d.hasTo())
                draft.to = d.getTo();
            if(d.hasInReplyTo())
                draft.inReplyTo = d.getInReplyTo();

            drafts.put(draft.id, draft);
        }

        System.out.println("StateFile loaded!");
    }

    public void save() {
        System.out.println("start save");

        //rotate erasureStorageKey

        ArrayList<LocalStorage.Contact> conts = new ArrayList<>();

        for(Contact contact: contacts.values()){
            LocalStorage.Contact.Builder cont = LocalStorage.Contact.newBuilder();
            cont.setId(contact.id);
            cont.setName(contact.name);
            cont.setIsPending(contact.isPending);
            cont.setKeyExchangeBytes(ByteString.copyFrom(contact.kxsBytes));
            cont.setSupportedVersion(contact.supportedVersion);
            cont.setRevokedUs(contact.revokedUs);
            cont.addAllTheirPairs(contact.theirHMACPairs);
            for(HMACPair pair : contact.myHMACs){
                cont.addMyPairs(LocalStorage.Contact.MyHMACPair.newBuilder()
                        .setPublicKey(ByteString.copyFrom(pair.publicKey))
                        .setHmac(ByteString.copyFrom(pair.hmacOfKey))
                        .build());
            }

            if(!contact.isPending) {
                cont.setTheirPub(ByteString.copyFrom(contact.theirPub.toBytes()));
                cont.setTheirIdentityPublic(ByteString.copyFrom(contact.theirIdentityPublic.toBytes()));
                cont.setTheirServer(contact.theirServer);
            }

            for(int i = 0; i < contact.events.size(); i++) {
                if(System.nanoTime()-contact.events.get(i).time > Globals.MESSAGE_LIFETIME)
                    continue;
                LocalStorage.Contact.Event.Builder event = LocalStorage.Contact.Event.newBuilder();
                event.setTime(contact.events.get(i).time);
                event.setMessage(contact.events.get(i).msg);
                cont.setEvents(i, event);
            }

            conts.add(cont.build());
        }

        ArrayList<LocalStorage.Inbox> inbox = new ArrayList<>();
        for(InboxMessage inMsg: this.inbox.values()){
            if(System.nanoTime()-inMsg.receivedTime > Globals.MESSAGE_LIFETIME && !inMsg.retained)
                continue;

            LocalStorage.Inbox.Builder msg = LocalStorage.Inbox.newBuilder();
            msg.setId(inMsg.id);
            msg.setFrom(inMsg.from);
            msg.setReceivedTime(inMsg.receivedTime);
            msg.setAcked(inMsg.acked);
            msg.setRead(inMsg.read);
            msg.setSealed(ByteString.copyFrom(inMsg.sealed));
            msg.setRetained(inMsg.retained);

            if(inMsg.message != null)
                msg.setMessage(inMsg.message.toByteString());

            inbox.add(msg.build());
        }

        ArrayList<LocalStorage.Outbox> outbox = new ArrayList<>();
        for(QueuedMessage outMsg: this.outbox.values()){
            if(System.nanoTime()-outMsg.created > Globals.MESSAGE_LIFETIME)
                continue;

            LocalStorage.Outbox.Builder msg = LocalStorage.Outbox.newBuilder();
            msg.setId(outMsg.id);
            msg.setTo(outMsg.to);
            msg.setServer(outMsg.server);
            msg.setCreated(outMsg.created);
            msg.setRevocation(outMsg.revocation);

            if(outMsg.message != null)
                msg.setMessage(outMsg.message.build().toByteString());
            if(outMsg.sent != 0L)
                msg.setSent(outMsg.sent);
            if(outMsg.acked != 0L)
                msg.setAcked(outMsg.acked);
            if(outMsg.request != null)
                msg.setRequest(outMsg.request.build().toByteString());

            outbox.add(msg.build());
        }

        ArrayList<LocalStorage.Draft> drafts = new ArrayList<>();
        for(Draft drMsg: this.drafts.values()){
            LocalStorage.Draft.Builder draft = LocalStorage.Draft.newBuilder();
            draft.setId(drMsg.id);
            draft.setBody(drMsg.body);
            draft.addAllAttachments(drMsg.attachments);
            draft.addAllDetachments(drMsg.detachments);
            draft.setCreated(drMsg.created);
            if(drMsg.to != 0L)
                draft.setTo(drMsg.to);
            if(drMsg.inReplyTo != 0L)
                draft.setTo(drMsg.inReplyTo);
            drafts.add(draft.build());
        }

        LocalStorage.State.Builder state = LocalStorage.State.newBuilder();
        state.setSeed(ByteString.copyFrom(signingKey.toBytes()));
        state.setIdentity(ByteString.copyFrom(identity.getPrivateKey().toBytes()));
        state.setHmacKey(ByteString.copyFrom(hmacKey.getEncoded()));
        state.setServer(server);
        state.addAllContacts(conts);
        state.addAllInbox(inbox);
        state.addAllOutbox(outbox);
        state.addAllDrafts(drafts);

        System.out.println("building state thingy");
        writerChan.write(new NewState(state.build().toByteArray(), false, false));
        System.out.println("finished building state thingy");
    }

}
